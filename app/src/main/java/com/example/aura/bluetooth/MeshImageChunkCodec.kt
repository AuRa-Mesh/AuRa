package com.example.aura.bluetooth

import android.util.Base64

/**
 * Простая нарезка JPEG в несколько текстовых сообщений канала (UTF-8 строки с префиксом).
 * Приёмник собирает чанки по [sessionId]; формат только для клиентов Aura.
 */
object MeshImageChunkCodec {
    const val PREFIX = "AUR1:"
    private const val MAX_LINE_UTF8_BYTES = 200

    data class ChunkMeta(
        val sessionId: String,
        val part: Int,
        val total: Int,
        val base64Piece: String,
    )

    /** [sessionId] — короткая строка без ':' (например 8 hex). */
    fun buildLines(sessionId: String, jpegBytes: ByteArray): List<String> {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val overhead = PREFIX.length + sessionId.length + ":0:999:".length
        val chunkChars = (MAX_LINE_UTF8_BYTES - overhead).coerceAtLeast(32)
        val parts = b64.chunked(chunkChars)
        val n = parts.size
        return parts.mapIndexed { i, p ->
            "$PREFIX$sessionId:$i:$n:$p"
        }
    }

    fun tryParseLine(line: String): ChunkMeta? {
        if (!line.startsWith(PREFIX)) return null
        val rest = line.removePrefix(PREFIX)
        val first = rest.indexOf(':')
        if (first <= 0) return null
        val sessionId = rest.substring(0, first)
        val r1 = rest.substring(first + 1)
        val second = r1.indexOf(':')
        if (second <= 0) return null
        val part = r1.substring(0, second).toIntOrNull() ?: return null
        val r2 = r1.substring(second + 1)
        val third = r2.indexOf(':')
        if (third <= 0) return null
        val total = r2.substring(0, third).toIntOrNull() ?: return null
        val b64 = r2.substring(third + 1)
        if (sessionId.isBlank() || b64.isEmpty() || total <= 0 || part !in 0 until total) return null
        return ChunkMeta(sessionId, part, total, b64)
    }

    class Reassembler {
        private val sessions = mutableMapOf<String, Pair<Int, Array<String?>>>()

        /** Вернёт полные JPEG-байты, когда собрано; иначе null. */
        fun ingest(meta: ChunkMeta): ByteArray? {
            val slot = sessions.getOrPut(meta.sessionId) {
                meta.total to arrayOfNulls(meta.total)
            }
            if (slot.first != meta.total) return null
            val arr = slot.second
            if (meta.part >= arr.size) return null
            if (arr[meta.part] != null) return null
            arr[meta.part] = meta.base64Piece
            if (arr.any { it == null }) return null
            val full = arr.joinToString("")
            sessions.remove(meta.sessionId)
            return try {
                Base64.decode(full, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
