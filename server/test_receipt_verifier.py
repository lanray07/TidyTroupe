import base64
import json
import unittest
from unittest.mock import patch
import urllib.error
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from receipt_verifier import signed_envelope, validate_receipt, verified_payload


class VerifierTests(unittest.TestCase):
    def receipt(self):
        return {"receiptId": "receipt", "productId": "premium", "productType": "ENTITLED",
                "testTransaction": False, "betaProduct": False, "purchaseDate": 1700000000000,
                "cancelDate": None}

    def test_only_matching_production_entitlement_is_accepted(self):
        self.assertTrue(validate_receipt(self.receipt(), "premium", "receipt"))
        for key, value in (("testTransaction", True), ("betaProduct", True),
                           ("productType", "CONSUMABLE"), ("productId", "other"),
                           ("receiptId", "other"), ("purchaseDate", None)):
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate_receipt(dict(self.receipt(), **{key: value}), "premium", "receipt")

    def test_refund_cannot_grant_entitlement(self):
        self.assertFalse(validate_receipt(dict(self.receipt(), cancelDate=1700000000010), "premium", "receipt"))

    def test_payload_signature_covers_every_field(self):
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        envelope = signed_envelope({"entitled": True, "userId": "account"}, key)
        raw = base64.b64decode(envelope["payload"])
        key.public_key().verify(base64.b64decode(envelope["signature"]), raw, padding.PKCS1v15(), hashes.SHA256())
        with self.assertRaises(Exception):
            key.public_key().verify(base64.b64decode(envelope["signature"]), raw.replace(b"true", b"false"), padding.PKCS1v15(), hashes.SHA256())

    def test_amazon_revocation_returns_signed_payload_input_without_grant(self):
        error = urllib.error.HTTPError("https://redacted", 410, "Gone", {}, None)
        with patch("receipt_verifier.urllib.request.urlopen", side_effect=error):
            payload = verified_payload("account", "receipt", "nonce", "premium", "secret")
        self.assertFalse(payload["entitled"])
        self.assertEqual(payload["userId"], "account")
        self.assertEqual(payload["nonce"], "nonce")
        self.assertEqual(payload["expiresAt"] - payload["checkedAt"], 604800)

    def test_upstream_outage_never_becomes_a_grant_or_exposes_secret_url(self):
        error = urllib.error.HTTPError("https://redacted/secret", 500, "Error", {}, None)
        with patch("receipt_verifier.urllib.request.urlopen", side_effect=error):
            with self.assertRaises(RuntimeError) as raised:
                verified_payload("account", "receipt", "nonce", "premium", "secret")
        self.assertNotIn("secret", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
