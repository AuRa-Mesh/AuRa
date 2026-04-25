package com.example.aura.mesh.qr

import com.example.aura.meshwire.MeshWireNodeNum

/**
 * Статичный идентификатор узла для QR: строка `!XXXXXXXX` (как в Meshtastic), привязана к nodenum.
 * Сканер принимает также `aura://node/!XXXXXXXX` и устаревший `aurus://…`, варианты с переводами строк.
 */
object NodeIdentityQr {

    const val URI_PREFIX = "aura://node/"
    private const val URI_PREFIX_LEGACY = "aurus://node/"

    /** Полезная нагрузка для QR (то, что кодируется в матрицу). */
    fun payloadForNodeNum(nodeNum: UInt): String = MeshWireNodeNum.formatHex(nodeNum)

    /** URI-форма для совместимости со ссылками. */
    fun uriForNodeNum(nodeNum: UInt): String = URI_PREFIX + MeshWireNodeNum.formatHex(nodeNum).removePrefix("!")

    fun parseNodeNum(raw: String): UInt? {
        var t = raw.trim().replace("\uFEFF", "")
        val firstLine = t.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        t = firstLine.trim()
        t = when {
            t.contains(URI_PREFIX, ignoreCase = true) ->
                t.substringAfter(URI_PREFIX, missingDelimiterValue = t).trim()
            t.contains(URI_PREFIX_LEGACY, ignoreCase = true) ->
                t.substringAfter(URI_PREFIX_LEGACY, missingDelimiterValue = t).trim()
            else -> t
        }
        return MeshWireNodeNum.parseToUInt(t)
    }
}
