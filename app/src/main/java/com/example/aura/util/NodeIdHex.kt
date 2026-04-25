package com.example.aura.util

import java.util.Locale

/** Нормализация Node ID mesh (!… / hex) для путей и отображения. */
object NodeIdHex {
    fun normalize(raw: String): String {
        var s = raw.trim().removePrefix("!").uppercase(Locale.US)
        val hex = s.filter { it in '0'..'9' || it in 'A'..'F' }
        if (hex.isEmpty()) return ""
        val core = when {
            hex.length > 8 -> hex.takeLast(8)
            hex.length < 8 -> hex.padStart(8, '0')
            else -> hex
        }
        return core
    }
}
