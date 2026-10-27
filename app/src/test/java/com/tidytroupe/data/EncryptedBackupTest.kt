package com.tidytroupe.data


import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith


class EncryptedBackupTest {
    private val crypto = EncryptedBackup()
    private val password = "separate backup passphrase"
    private val json = """{"format":"tidytroupe","version":1,"household":"Our family 🏡"}"""

    @Test fun roundTripIsPortableAndUsesFreshRandomness() = runBlocking {
        val first = crypto.encrypt(json, password)
        val second = crypto.encrypt(json, password)
        assertFalse(first.contentEquals(second))
        assertEquals(json, EncryptedBackup().decrypt(first, password))
        assertFalse(String(first, Charsets.UTF_8).contains("Our family"))
    }

    @Test fun wrongPasswordCannotReadBackup() = runBlocking {
        val encrypted = crypto.encrypt(json, password)
        rejected { crypto.decrypt(encrypted, "another backup passphrase") }
    }

    @Test fun headerAndCiphertextTamperingAreRejected() = runBlocking {
        val encrypted = crypto.encrypt(json, password)
        // Salt and nonce are authenticated as well as the encrypted JSON.
        for (position in listOf(9, 25, encrypted.lastIndex)) {
            val altered = encrypted.copyOf()
            altered[position] = (altered[position].toInt() xor 1).toByte()
            rejected { crypto.decrypt(altered, password) }
        }
    }

    @Test fun invalidEnvelopeShortPasswordAndOversizedInputAreRejected() = runBlocking {
        rejected { crypto.encrypt(json, "482695") }
        rejected { crypto.decrypt(ByteArray(12), password) }
        val unknownVersion = crypto.encrypt(json, password).also { it[8] = 99 }
        rejected { crypto.decrypt(unknownVersion, password) }
        rejected { crypto.encrypt("x".repeat(EncryptedBackup.MAX_PLAINTEXT_BYTES + 1), password) }
    }

    private suspend fun rejected(block: suspend () -> Any?) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("Expected backup authentication or validation failure", rejected)
    }
}

