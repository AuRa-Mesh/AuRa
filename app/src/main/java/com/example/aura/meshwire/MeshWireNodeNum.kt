package com.example.aura.meshwire

import java.util.Locale

object MeshWireNodeNum {

    /**
     * Из строки вида `!a1B2c3D4`, `0xA1B2C3D4`, длиной до 8 hex — значение для `MeshPacket.to`.
     *
     * **Важно:** суффикс из BLE-имени `MeshWire_XXXX` (ровно 4 hex) — это не полный nodenum.
     * Дополнение нулями слева до 8 hex задаёт **другой** узел, чем реальный, если старшие биты
     * nodenum не нули. Надёжный источник — GATT / `my_node_num` с устройства.
     */
    fun parseToUInt(nodeId: String): UInt? {
        var s = nodeId.trim()
        if (s.startsWith("0x", ignoreCase = true)) s = s.drop(2)
        s = s.removePrefix("!")
        val hex = s.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        if (hex.isEmpty()) return null
        val core = when {
            hex.length > 8 -> hex.takeLast(8)
            hex.length < 8 -> hex.padStart(8, '0')
            else -> hex
        }
        return core.toUInt(16)
    }

    /** Как в приложении mesh: `!` + 8 hex. */
    fun formatHex(nodeNum: UInt): String =
        String.format(Locale.US, "!%08X", nodeNum.toLong() and 0xFFFFFFFFL)
}
