package com.example.aura.chat

import android.util.Log

/**
 * Подробное логирование байтов protobuf для отладки ToRadio / FromRadio (обрезка по длине).
 */
object ChatProtobufDebugLog {
    private const val TAG = "ChatProto"

    fun hexDump(label: String, bytes: ByteArray, maxBytes: Int = 256) {
        val n = minOf(minOf(bytes.size, maxBytes), 512)
        val sb = StringBuilder(n * 2)
        for (i in 0 until n) {
            sb.append("%02x".format(bytes[i].toInt() and 0xFF))
        }
        val tail = if (bytes.size > n) "…(+${bytes.size - n} B)" else ""
        Log.d(TAG, "$label len=${bytes.size} hex=$sb$tail")
    }
}
