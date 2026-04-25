package com.example.aura.security

import androidx.annotation.Keep
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Кодек одноразовых кодов продления VIP-тарифа.
 *
 * Формат кода (60 бит, отображение `XXXX-XXXX-XXXX`, 12 base32-символов без омонимов):
 * ```
 *  bits [50..59] (10 bits) : days - 1          → значения 1..1024
 *  bits [30..49] (20 bits) : случайный nonce    → 1 048 576 возможных кодов на одну комбинацию
 *  bits [ 0..29] (30 bits) : HMAC-SHA256(secret, canonicalNodeIdHex || daysBE || nonceBE)
 *                            truncate → 30 бит
 * ```
 *
 * Свойства:
 *  - **Привязка к узлу**: подпись охватывает [canonicalNodeIdHexForHmac]. Код сгенерированный
 *    для одного node id, отклоняется другим узлом с вероятностью 2^-30.
 *  - **Уникальность**: 20 бит nonce ⇒ на одну комбинацию `(node, days)` возможно ≈1M разных кодов.
 *  - **Самоописательность**: количество дней закодировано прямо в code payload и извлекается
 *    верификатором после валидации HMAC (нет нужды в справочнике на устройстве).
 *
 * Алгоритм идентичен в `:app` и `:passwordgen` — держите файлы синхронно.
 */
@Keep
object VipExtensionCodeCodec {

    /** Секрет HMAC. Одинаков на генераторе и верификаторе. */
    private const val HMAC_SECRET: String = "AuRusMesh_VipExtendKey_v1_2024"

    /** 32 символа, исключающие визуально похожие пары (0/O, 1/I/L). */
    internal const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Длина кода в base32-символах без дефисов. */
    internal const val CODE_LENGTH: Int = 12

    /** Минимум/максимум значений `days` (1..1024). */
    const val MIN_DAYS: Int = 1
    const val MAX_DAYS: Int = 1024

    private const val DAYS_BITS: Int = 10
    private const val NONCE_BITS: Int = 20
    private const val MAC_BITS: Int = 30
    private const val DAYS_MASK: Long = (1L shl DAYS_BITS) - 1L
    private const val NONCE_MASK: Long = (1L shl NONCE_BITS) - 1L
    private const val MAC_MASK: Long = (1L shl MAC_BITS) - 1L

    /**
     * Канонизация Node ID: 8 hex-символов uppercase (как в [NodePasswordGenerator]).
     */
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

    /** Нормализует пользовательский ввод кода: upper-case без дефисов и пробелов. */
    fun normalizeCodeInput(input: String): String =
        input.trim().uppercase(Locale.US).replace("-", "").replace(" ", "")

    /** Красивое представление: `XXXX-XXXX-XXXX` из 12 base32-символов. */
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

    /**
     * Сформировать код продления на [days] дней для [nodeId] с случайным [nonce] (20 бит).
     * Возвращает красиво отформатированную строку `XXXX-XXXX-XXXX`.
     */
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

    /** Полный результат верификации кода. */
    data class Decoded(
        val days: Int,
        val nonce: Int,
        /** Нормализованная 12-символьная форма для сохранения в реестре использованных кодов. */
        val normalized: String,
    )

    /**
     * Декодировать и проверить подпись. Возвращает `null`, если формат некорректен или HMAC не
     * совпадает с [nodeId]. **Не** проверяет уникальность — это делает [VipExtensionUsedCodes].
     */
    fun decodeAndVerify(nodeId: String, input: String): Decoded? {
        val normalized = normalizeCodeInput(input)
        if (normalized.length != CODE_LENGTH) return null
        val payload = decodeBase32(normalized) ?: return null
        val daysField = ((payload ushr (NONCE_BITS + MAC_BITS)) and DAYS_MASK).toInt()
        val nonceField = ((payload ushr MAC_BITS) and NONCE_MASK).toInt()
        val mac = payload and MAC_MASK
        val days = daysField + 1
        if (days !in MIN_DAYS..MAX_DAYS) return null
        val expected = macBits(nodeId, days, nonceField)
        if (expected != mac) return null
        return Decoded(days = days, nonce = nonceField, normalized = normalized)
    }

    // ──────────────────────────────── internals ────────────────────────────────

    private fun macBits(nodeId: String, days: Int, nonce: Int): Long {
        val canonical = canonicalNodeIdHexForHmac(nodeId)
        if (canonical.isEmpty()) return -1L // никогда не совпадёт с валидным 30-битным mac
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
        // Первые 4 байта → 32-битное число → оставляем 30 младших бит.
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

    private fun decodeBase32(normalized: String): Long? {
        var payload = 0L
        for (ch in normalized) {
            val idx = ALPHABET.indexOf(ch)
            if (idx < 0) return null
            payload = (payload shl 5) or idx.toLong()
        }
        return payload
    }
}
