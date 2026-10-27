package com.tidytroupe

import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Bounded allocation on every supported Fire OS version, including API 26. */
internal fun InputStream.boundedBytes(limit: Int): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read == -1) return result.toByteArray()
        require(result.size() + read <= limit) { "Backup is too large." }
        result.write(buffer, 0, read)
    }
}
