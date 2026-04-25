package com.example.aura.passwordgen

import androidx.annotation.Keep
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Дубль [com.example.aura.security.VipExtensionCodeCodec] из главного модуля — для автономности
 * генератора. **Любое изменение алгоритма должно быть синхронизировано в обоих файлах.**
 *
 * Формат кода (60 бит, отображение `XXXX-XXXX-XXXX`):
 *   bits [50..59] days - 1            (1..1024)
 *   bits [30..49] nonce               (20 бит)
 *   bits [ 0..29] HMAC-SHA256 truncate (30 бит)
 */
@Keep
internal object VipExtensionCodec {

    private const val HMAC_SECRET: String = "AuRusMesh_VipExtendKey_v1_2024"
    internal const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    internal const val CODE_LENGTH: Int = 12

    const val MIN_DAYS: Int = 1
    const val MAX_DAYS: Int = 1024

    private const val DAYS_BITS: Int = 10
    private const val NONCE_BITS: Int = 20
    private const val MAC_BITS: Int = 30
    private const val DAYS_MASK: Long = (1L shl DAYS_BITS) - 1L
    private const val NONCE_MASK: Long = (1L shl NONCE_BITS) - 1L
    private const val MAC_MASK: Long = (1L shl MAC_BITS) - 1L

    internal fun canonicalNodeIdHexForHmac(nodeId: String): String {
        var s = nodeId.trim()
        if (s.startsWith("0x", ignoreCase = true)) s = s.drop(2)
        s = s.removePrefix("!")
        val hex = s.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        if (hex.isEmpty()) return ""
        val core = when {
            hex.length > 8 -> hex.takeLast(8)
            hex.length < 8 -> hex.padStart(8, '0')
            else -> hex
        }
        return core.uppercase(Locale.US)
    }

    fun encode(nodeId: String, days: Int, nonce: Int): String {
        require(days in MIN_DAYS..MAX_DAYS) { "days out of range: $days" }
        require(nonce in 0..NONCE_MASK.toInt()) { "nonce out of range: $nonce" }
        val daysField = (days - 1).toLong() and DAYS_MASK
        val nonceField = nonce.toLong() and NONCE_MASK
        val mac = macBits(nodeId, days, nonce)
        val payload = (daysField shl (NONCE_BITS + MAC_BITS)) or
            (nonceField shl MAC_BITS) or
            mac
        return formatPretty(encodeBase32(payload))
    }

    fun formatPretty(normalized: String): String {
        if (normalized.length != CODE_LENGTH) return normalized
        return buildString(CODE_LENGTH + 2) {
            append(normalized, 0, 4)
            append('-')
            append(normalized, 4, 8)
            append('-')
            append(normalized, 8, CODE_LENGTH)
        }
    }

    private fun macBits(nodeId: String, days: Int, nonce: Int): Long {
        val canonical = canonicalNodeIdHexForHmac(nodeId)
        if (canonical.isEmpty()) return -1L
        val bytes = ByteArray(8 + 2 + 3)
        val nodeBytes = canonical.toByteArray(Charsets.US_ASCII)
        System.arraycopy(nodeBytes, 0, bytes, 0, 8)
        bytes[8] = ((days ushr 8) and 0xFF).toByte()
        bytes[9] = (days and 0xFF).toByte()
        bytes[10] = ((nonce ushr 16) and 0xFF).toByte()
        bytes[11] = ((nonce ushr 8) and 0xFF).toByte()
        bytes[12] = (nonce and 0xFF).toByte()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val h = mac.doFinal(bytes)
        val raw = (
            (h[0].toLong() and 0xFFL) shl 24
            ) or (
            (h[1].toLong() and 0xFFL) shl 16
            ) or (
            (h[2].toLong() and 0xFFL) shl 8
            ) or (
            h[3].toLong() and 0xFFL
            )
        return raw and MAC_MASK
    }

    private fun encodeBase32(payload: Long): String {
        val sb = StringBuilder(CODE_LENGTH)
        for (i in CODE_LENGTH - 1 downTo 0) {
            val shift = i * 5
            val v = ((payload ushr shift) and 0x1FL).toInt()
            sb.append(ALPHABET[v])
        }
        return sb.toString()
    }
}
