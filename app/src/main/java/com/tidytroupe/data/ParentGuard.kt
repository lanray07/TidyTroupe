package com.tidytroupe.data

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal val Context.tidyPreferences by preferencesDataStore("tidy_private_settings")

/** Session authorization is separate from UI navigation and never survives process death. */
class ParentGuard(private val context: Context) {
    // Serialize credential attempts across repository/guard instances in this process.
    private val mutex get() = credentialMutex
    private val sessionLock = Any()
    private var sessionGeneration = 0L
    @Volatile private var expires = 0L
    private val saltKey = stringPreferencesKey("pin_salt")
    private val hashKey = stringPreferencesKey("pin_hash")
    private val failuresKey = intPreferencesKey("pin_failures")
    private val blockedKey = longPreferencesKey("pin_blocked_until")
    fun authorized(): Boolean = expires > SystemClock.elapsedRealtime()
    fun lock() = synchronized(sessionLock) { sessionGeneration++; expires = 0L }
    fun requireAdult() { check(authorized()) { "Unlock the parent area first." } }
    suspend fun hasPin(): Boolean = context.tidyPreferences.data.first()[hashKey] != null
    suspend fun setupPin(pin: String) = mutex.withLock {
        val generation = synchronized(sessionLock) { sessionGeneration }
        check(!hasPin()) { "A parent PIN is already set." }
        require(pin.matches(Regex("[0-9]{6,12}"))) { "Use 6–12 digits for your parent PIN." }
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        context.tidyPreferences.edit {
            it[saltKey] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[hashKey] = Base64.encodeToString(hash, Base64.NO_WRAP)
            it[failuresKey] = 0
            it[blockedKey] = 0L
        }
        grantIfCurrent(generation)
        Unit
    }
    suspend fun unlock(pin: String): Boolean = mutex.withLock {
        val generation = synchronized(sessionLock) { sessionGeneration }
        val prefs = context.tidyPreferences.data.first()
        val blocked = prefs[blockedKey] ?: 0L
        check(System.currentTimeMillis() >= blocked) { "Too many attempts. Try again in a minute." }
        val salt = prefs[saltKey] ?: return@withLock false
        val expected = prefs[hashKey] ?: return@withLock false
        val valid = if (pin.matches(Regex("[0-9]{6,12}"))) {
            val actual = derive(pin, Base64.decode(salt, Base64.NO_WRAP))
            MessageDigest.isEqual(actual, Base64.decode(expected, Base64.NO_WRAP))
        } else false
        context.tidyPreferences.edit {
            if (valid) { it[failuresKey] = 0; it[blockedKey] = 0L }
            else {
                val attempts = (it[failuresKey] ?: 0) + 1
                it[failuresKey] = attempts
                if (attempts >= 5) it[blockedKey] = System.currentTimeMillis() + 60_000L
            }
        }
        if (valid) grantIfCurrent(generation)
        else { lock(); false }
    }
    suspend fun changePin(current: String, replacement: String) {
        check(unlock(current)) { "That PIN is incorrect." }
        require(replacement.matches(Regex("[0-9]{6,12}"))) { "Use 6–12 digits for your parent PIN." }
        mutex.withLock {
            requireAdult()
            val generation = synchronized(sessionLock) { sessionGeneration }
            val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
            val hash = derive(replacement, salt)
            check(synchronized(sessionLock) { sessionGeneration == generation } && authorized()) { "Unlock the parent area first." }
            context.tidyPreferences.edit {
                it[saltKey] = Base64.encodeToString(salt, Base64.NO_WRAP)
                it[hashKey] = Base64.encodeToString(hash, Base64.NO_WRAP)
            }
        }
    }
    private suspend fun derive(pin: String, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    /** A background/role-change lock must also revoke a PIN verification already in flight. */
    private fun grantIfCurrent(generation: Long): Boolean = synchronized(sessionLock) {
        if (sessionGeneration != generation) return@synchronized false
        expires = SystemClock.elapsedRealtime() + 300_000L
        true
    }
    companion object { private val credentialMutex = Mutex() }
}
