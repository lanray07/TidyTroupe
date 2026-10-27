"""Amazon production RVS adapter. Bind localhost behind an HTTPS proxy; never deploy bare HTTP.

Secrets stay on the server. No household, nickname or child data is accepted or required.
"""
from __future__ import annotations

import base64
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


def validate_receipt(receipt: dict, expected_sku: str, receipt_id: str) -> bool:
    """Only production, one-time, matching, active receipts can grant this entitlement."""
    if receipt.get("receiptId") != receipt_id or receipt.get("productId") != expected_sku:
        raise ValueError("Receipt does not match the configured product")
    if receipt.get("productType") != "ENTITLED":
        raise ValueError("Expected a one-time entitlement")
    if receipt.get("testTransaction") is not False or receipt.get("betaProduct") is not False:
        raise ValueError("Test receipts cannot grant production entitlement")
    purchase_date = receipt.get("purchaseDate")
    if type(purchase_date) is not int or purchase_date <= 0 or purchase_date > int(time.time() * 1000) + 300_000:
        raise ValueError("Invalid purchase date")
    return receipt.get("cancelDate") is None


def verified_payload(user_id: str, receipt_id: str, nonce: str, expected_sku: str, shared_secret: str) -> dict:
    quote = lambda value: urllib.parse.quote(value, safe="")
    url = "https://appstore-sdk.amazon.com/version/1.0/verifyReceiptId/developer/{}/user/{}/receiptId/{}".format(
        quote(shared_secret), quote(user_id), quote(receipt_id))
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"Accept": "application/json"}), timeout=12) as response:
            body = response.read(64_001)
            if len(body) > 64_000:
                raise ValueError("Upstream response too large")
            receipt = json.loads(body)
            entitled = validate_receipt(receipt, expected_sku, receipt_id)
            purchase_date = receipt["purchaseDate"]
    except urllib.error.HTTPError as error:
        if error.code == 410:
            entitled = False
            purchase_date = 0
        else:
            # Never put the exception URL in logs: its path includes the developer secret.
            raise RuntimeError("Amazon receipt verification unavailable") from None
    now = int(time.time())
    return {"version": 1, "sku": expected_sku, "userId": user_id, "receiptId": receipt_id,
            "entitled": entitled, "checkedAt": now, "expiresAt": now + 7 * 24 * 3600,
            "purchaseDate": purchase_date, "nonce": nonce}


def signed_envelope(payload: dict, private_key: rsa.RSAPrivateKey) -> dict:
    raw = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    signature = private_key.sign(raw, padding.PKCS1v15(), hashes.SHA256())
    return {"payload": base64.b64encode(raw).decode("ascii"), "signature": base64.b64encode(signature).decode("ascii")}


class VerifierHandler(BaseHTTPRequestHandler):
    server_version = "TidyReceiptVerifier"
    private_key: rsa.RSAPrivateKey
    expected_sku: str
    shared_secret: str
    attempts: dict[str, deque] = defaultdict(deque)
    attempts_lock = threading.Lock()

    def log_message(self, format, *args):
        # Receipt/account identifiers and developer secrets are intentionally absent from access logs.
        pass

    def reply(self, code: int, value: dict):
        raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self):
        self.connection.settimeout(15)
        if self.path != "/verify":
            return self.reply(404, {"error": "Not found"})
        # Proxy must enforce a global and per-client production rate limit, including its own body/time limits.
        with self.attempts_lock:
            now = time.monotonic()
            if len(self.attempts) > 10_000:
                self.attempts.clear()
            bucket = self.attempts[self.client_address[0]]
            while bucket and now - bucket[0] > 60:
                bucket.popleft()
            if len(bucket) >= 30:
                return self.reply(429, {"error": "Please retry later"})
            bucket.append(now)
        try:
            if self.headers.get_content_type() != "application/json":
                raise ValueError()
            length = int(self.headers.get("Content-Length", "0"))
            if not 1 <= length <= 4096:
                raise ValueError()
            body = json.loads(self.rfile.read(length))
            values = [body[key] for key in ("userId", "receiptId", "nonce")]
            if any(not isinstance(value, str) or not 1 <= len(value) <= 1000 for value in values):
                raise ValueError()
            if len(values[2]) > 100:
                raise ValueError()
        except (KeyError, ValueError, TypeError, UnicodeError):
            return self.reply(400, {"error": "Invalid request"})
        try:
            payload = verified_payload(*values, self.expected_sku, self.shared_secret)
            return self.reply(200, signed_envelope(payload, self.private_key))
        except ValueError:
            return self.reply(400, {"error": "Receipt not eligible"})
        except Exception:
            return self.reply(503, {"error": "Verification temporarily unavailable"})


def main():
    VerifierHandler.shared_secret = os.environ["AMAZON_SHARED_SECRET"]
    VerifierHandler.expected_sku = os.environ["AMAZON_PREMIUM_SKU"]
    if not VerifierHandler.shared_secret.strip() or not VerifierHandler.expected_sku.strip():
        raise ValueError("Amazon shared secret and SKU must be configured")
    signing_path = Path(os.environ["ENTITLEMENT_SIGNING_KEY_FILE"])
    key = serialization.load_pem_private_key(signing_path.read_bytes(), password=None)
    if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 3072:
        raise ValueError("Use a dedicated RSA signing key of at least 3072 bits")
    VerifierHandler.private_key = key
    port = int(os.environ.get("PORT", "8081"))
    # Loopback default prevents accidental public, unencrypted exposure.
    ThreadingHTTPServer(("127.0.0.1", port), VerifierHandler).serve_forever()


if __name__ == "__main__":
    main()
