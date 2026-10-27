package com.tidytroupe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable password-protected backups. The password is separate from the parent PIN and is never
 * persisted. Loss of the password means loss of access to the backup. No device-bound key is used.
 * Callers must gate both document export and import with the repository's parent authorization.
 * Version 1: 8-byte magic, 1-byte version, 16-byte salt, 12-byte nonce, ciphertext + 16-byte GCM tag.
 * PBKDF2-HMAC-SHA256, 210,000 iterations, 256-bit AES key. The complete header is authenticated.
 */
class EncryptedBackup {
    suspend fun encrypt(json: String, password: String): ByteArray = withContext(Dispatchers.Default) {
        validatePassword(password)
        require(json.length <= MAX_PLAINTEXT_BYTES) { "Backup is too large." }
        val plaintext = json.toByteArray(Charsets.UTF_8)
        try {
            require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Backup is too large." }
            val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
            val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
            val header = MAGIC + byteArrayOf(VERSION) + salt + nonce
            val key = derive(password, salt)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(header)
                header + cipher.doFinal(plaintext)
            } finally { key.fill(0) }
        } finally { plaintext.fill(0) }
    }

    suspend fun decrypt(bytes: ByteArray, password: String): String = withContext(Dispatchers.Default) {
        validatePassword(password)
        require(bytes.size in (HEADER_SIZE + TAG_SIZE)..(HEADER_SIZE + TAG_SIZE + MAX_PLAINTEXT_BYTES)) { "Invalid or oversized encrypted backup." }
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "This is not a TidyTroupe encrypted backup." }
        require(bytes[MAGIC.size] == VERSION) { "This backup version is not supported." }
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val salt = bytes.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_SIZE)
        val nonce = bytes.copyOfRange(HEADER_SIZE - NONCE_SIZE, HEADER_SIZE)
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            val plaintext = try { cipher.doFinal(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE) }
            catch (_: AEADBadTagException) { throw IllegalArgumentException("Incorrect backup password or damaged backup.") }
            try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plaintext)).toString()
            } finally { plaintext.fill(0) }
        } finally { key.fill(0) }
    }

    private fun validatePassword(password: String) {
        require(password.length in 12..1024) { "Use a separate backup password of at least 12 characters." }
    }
    private fun derive(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword(); chars.fill('\u0000') }
    }
    companion object {
        private val MAGIC = "TIDYBKUP".toByteArray(Charsets.US_ASCII)
        private const val VERSION: Byte = 1
        private const val SALT_SIZE = 16
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private const val HEADER_SIZE = 8 + 1 + SALT_SIZE + NONCE_SIZE
        private const val ITERATIONS = 210_000
        const val MAX_PLAINTEXT_BYTES = 5_000_000
        private val random = SecureRandom()
    }
}
