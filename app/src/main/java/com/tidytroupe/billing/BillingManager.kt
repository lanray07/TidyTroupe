package com.tidytroupe.billing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import com.tidytroupe.data.ParentGuard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URL
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

/** Public configuration only. Never put Amazon's shared secret or a signing private key in the APK. */
data class BillingConfig(val sku: String = "", val verifierUrl: String = "", val verifierPublicKeyPem: String = "")
data class BillingState(val price: String? = null, val premium: Boolean = false, val busy: Boolean = false, val available: Boolean = false, val message: String = "Purchases are not configured in this development build.")

/** One application-scoped listener. All interactive store entry points require the repository's parent grant. */
class BillingManager(context: Context, private val guard: ParentGuard, private val config: BillingConfig = BillingConfig()) : PurchasingListener {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val verifyMutex = Mutex()
    private val cache = SecureCache(this.context)
    private val mutable = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = mutable.asStateFlow()
    private var configured = false
    private var currentUser: String? = null
    private var resetNext = false
    private var lastCheckedAt = 0L
    private var validUntil = 0L
    private var latestUserRequest: String? = null
    private var latestProductRequest: String? = null
    private var closed = false
    private var latestStorePurchaseAt = 0L
    private data class HistoryBatch(val user: String, val full: Boolean, val receipts: MutableList<Receipt> = mutableListOf())
    private val historyRequests = mutableMapOf<String, HistoryBatch>()

    init {
        configured = runCatching {
            require(config.sku.isNotBlank() && config.sku.length <= 200)
            val endpoint = URL(config.verifierUrl)
            require(endpoint.protocol == "https" && endpoint.userInfo == null && endpoint.host.isNotBlank())
            publicKey()
            this.context.assets.open("AppstoreAuthenticationKey.pem").use { require(it.read() >= 0) }
            PurchasingService.registerListener(this.context, this)
            true
        }.getOrDefault(false)
        if (configured) scope.launch {
            runCatching { withContext(Dispatchers.IO) { cache.read() } }.getOrNull()?.let { envelope ->
                runCatching {
                    val payload = validate(envelope, null)
                    if (currentUser == null) {
                        currentUser = payload.getString("userId")
                        lastCheckedAt = payload.getLong("checkedAt")
                        validUntil = payload.getLong("expiresAt")
                        latestStorePurchaseAt = payload.optLong("purchaseDate", 0L)
                        mutable.update { it.copy(premium = payload.getBoolean("entitled"), message = "Previously verified purchase available offline.") }
                    }
                }.onFailure { if (currentUser == null) clearEntitlement() }
            }
        }
        if (configured) scope.launch { while (true) { delay(60_000); expireIfNecessary() } }
    }

    /** Automatic non-interactive synchronization must run on every activity resume, including child mode. */
    fun onResume() {
        if (!configured || closed) return
        expireIfNecessary()
        runCatching { latestUserRequest = PurchasingService.getUserData().toString() }.onFailure { fail("Amazon Appstore is unavailable. A previously verified entitlement can remain available offline until its verification expires.") }
    }
    fun refresh() { guard.requireAdult(); onResume() }
    fun restore() {
        guard.requireAdult()
        if (!configured) return fail("Purchases are not configured in this development build.")
        resetNext = true
        mutable.update { it.copy(busy = true, message = "Checking Amazon purchases…") }
        onResume()
    }
    fun purchase() {
        guard.requireAdult()
        check(configured && state.value.available && state.value.price != null && currentUser != null) { "Amazon purchase details are not ready." }
        check(!state.value.busy) { "A store request is already running." }
        mutable.update { it.copy(busy = true, message = "Opening Amazon purchase confirmation…") }
        runCatching { PurchasingService.purchase(config.sku) }.onFailure { fail("The purchase could not be started. No entitlement was granted.") }
    }

    override fun onUserDataResponse(response: UserDataResponse) {
        if (closed || response.requestId.toString() != latestUserRequest) return
        if (response.requestStatus != UserDataResponse.RequestStatus.SUCCESSFUL) return fail("Amazon account is unavailable. Connect to the Appstore to check purchases.")
        val user = response.userData.userId
        val changed = currentUser != null && currentUser != user
        if (changed) { clearEntitlement(); latestStorePurchaseAt = 0L }
        currentUser = user
        runCatching {
            latestProductRequest = PurchasingService.getProductData(setOf(config.sku)).toString()
            val full = resetNext || changed
            val batch = HistoryBatch(user, full)
            historyRequests[PurchasingService.getPurchaseUpdates(full).toString()] = batch
            resetNext = false
        }.onFailure { fail("Amazon purchase history could not be requested.") }
    }
    override fun onProductDataResponse(response: ProductDataResponse) {
        if (closed || response.requestId.toString() != latestProductRequest) return
        if (response.requestStatus != ProductDataResponse.RequestStatus.SUCCESSFUL) return fail("Store prices are unavailable. Please try again later.")
        val product = response.productData[config.sku]
        val valid = product != null && product.productType == ProductType.ENTITLED
        mutable.update { it.copy(price = if (valid) product?.price else null, available = valid, busy = false, message = if (valid) "One-time household upgrade. Price supplied by Amazon." else "The Premium entitlement product is unavailable in this marketplace.") }
    }
    override fun onPurchaseResponse(response: PurchaseResponse) {
        if (closed) return
        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                if (response.userData.userId != currentUser) return fail("The Amazon account changed. Restore purchases for the current account.")
                scope.launch { verify(response.receipt, response.userData.userId, acknowledge = true) }
            }
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> { resetNext = true; onResume() }
            else -> fail("Purchase canceled, pending or unavailable. No unverified entitlement was granted.")
        }
    }
    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (closed) return
        val batch = historyRequests.remove(response.requestId.toString()) ?: return
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) return fail("Purchase history is unavailable. Please try restoring later.")
        val user = response.userData.userId
        if (user != currentUser || batch.user != user) return
        scope.launch {
            batch.receipts += response.receipts.filter { it.sku == config.sku && it.productType == ProductType.ENTITLED }
            if (user != currentUser) return@launch
            if (response.hasMore()) historyRequests[PurchasingService.getPurchaseUpdates(false).toString()] = batch
            else {
                // Amazon receipts are unordered. Resolve after all pages, so an old refund
                // cannot override a more recent repurchase just because it arrived later.
                val newest = batch.receipts.maxWithOrNull(compareBy<Receipt> { it.purchaseDate?.time ?: 0L }.thenBy { it.isCanceled })
                if (newest != null) verify(newest, user, acknowledge = true)
                else if (batch.full) clearEntitlement()
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun verify(receipt: Receipt, user: String, acknowledge: Boolean) = verifyMutex.withLock {
        if (receipt.sku != config.sku || receipt.productType != ProductType.ENTITLED || user != currentUser) return@withLock
        val purchaseAt = receipt.purchaseDate?.time ?: 0L
        if (purchaseAt < latestStorePurchaseAt) return@withLock
        if (receipt.isCanceled) { clearEntitlement(); return@withLock }
        mutable.update { it.copy(busy = true, message = "Verifying the Amazon receipt…") }
        val nonce = ByteArray(24).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        try {
            val envelope = withContext(Dispatchers.IO) {
                val connection = URL(config.verifierUrl).openConnection() as HttpsURLConnection
                connection.connectTimeout = 15_000; connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                try {
                    val request = JSONObject().put("userId", user).put("receiptId", receipt.receiptId).put("nonce", nonce)
                    connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                    check(connection.responseCode == 200) { "Receipt verifier unavailable." }
                    connection.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(16_385); var count = 0
                        while (count < buffer.size) { val read = reader.read(buffer, count, buffer.size - count); if (read < 0) break; count += read }
                        check(count <= 16_384); JSONObject(String(buffer, 0, count))
                    }
                } finally { connection.disconnect() }
            }
            val payload = validate(envelope, nonce)
            check(payload.getString("userId") == user && payload.getString("receiptId") == receipt.receiptId)
            if (user != currentUser) return@withLock
            if (payload.getLong("checkedAt") < lastCheckedAt) return@withLock
            lastCheckedAt = payload.getLong("checkedAt")
            validUntil = payload.getLong("expiresAt")
            latestStorePurchaseAt = maxOf(purchaseAt, payload.optLong("purchaseDate", 0L))
            withContext(Dispatchers.IO) { cache.write(envelope) }
            if (user != currentUser) { cache.clear(); return@withLock }
            mutable.update { it.copy(premium = payload.getBoolean("entitled"), busy = false, message = if (payload.getBoolean("entitled")) "Premium purchase verified." else "This purchase is no longer active.") }
            if (acknowledge && payload.getBoolean("entitled")) PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED)
        } catch (_: Exception) { fail("Receipt verification did not complete. Premium was not granted from this receipt. Restore purchases when connected.") }
    }

    private fun publicKey() = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(config.verifierPublicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace(Regex("\\s+"), ""), Base64.DEFAULT)))
    private fun validate(envelope: JSONObject, nonce: String?): JSONObject {
        val bytes = Base64.decode(envelope.getString("payload"), Base64.NO_WRAP)
        val signature = Base64.decode(envelope.getString("signature"), Base64.NO_WRAP)
        check(Signature.getInstance("SHA256withRSA").run { initVerify(publicKey()); update(bytes); verify(signature) })
        val payload = JSONObject(bytes.toString(Charsets.UTF_8))
        val now = System.currentTimeMillis() / 1000
        check(payload.getInt("version") == 1 && payload.getString("sku") == config.sku)
        check(payload.getLong("checkedAt") <= now + 300 && payload.getLong("expiresAt") >= now)
        check(payload.getLong("expiresAt") - payload.getLong("checkedAt") in 1..604800)
        check(payload.getString("userId").isNotBlank() && payload.getString("receiptId").isNotBlank())
        if (nonce != null) check(payload.getString("nonce") == nonce)
        return payload
    }
    private fun clearEntitlement() {
        mutable.update { it.copy(premium = false) }; lastCheckedAt = 0L; validUntil = 0L
        cache.clear()
    }
    private fun expireIfNecessary() {
        val now = System.currentTimeMillis() / 1000
        if (state.value.premium && (now > validUntil || now < lastCheckedAt - 300)) clearEntitlement()
    }
    private fun fail(message: String) { mutable.update { it.copy(busy = false, message = message) } }
    fun close() { closed = true; scope.cancel(); historyRequests.clear() }

    /** OS keystore keeps cache authentication keys out of preference files and exports. */
    private class SecureCache(context: Context) {
        private val prefs = context.getSharedPreferences("verified_amazon_entitlement", Context.MODE_PRIVATE)
        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey("tidy_purchase_cache_v1", null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("tidy_purchase_cache_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
        }
        fun write(envelope: JSONObject) { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); val encrypted = cipher.doFinal(envelope.toString().toByteArray(Charsets.UTF_8)); check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) }
        fun read(): JSONObject? { val iv = prefs.getString("iv", null) ?: return null; val data = prefs.getString("data", null) ?: return null; val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))); return JSONObject(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).toString(Charsets.UTF_8)) }
        fun clear() { prefs.edit().clear().commit() }
    }
}
