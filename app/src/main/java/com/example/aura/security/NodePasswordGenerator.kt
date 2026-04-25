package com.example.aura.security

import androidx.annotation.Keep
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Детерминированный генератор паролей на основе Node ID.
 * Алгоритм: HMAC-SHA256(secret, canonicalHex) → первые 8 байт → кодирование в алфавите без омонимов.
 * [canonicalNodeIdHexForHmac] совпадает по смыслу с [com.example.aura.meshwire.MeshWireNodeNum.parseToUInt]
 * (только hex, `0x`, `!`, до 8 hex → 8 символов A–F0–9), как на экране пароля после BLE.
 * Формат пароля: XXXX-XXXX (8 символов + 1 дефис).
 */
@Keep
object NodePasswordGenerator {

    // Секретный ключ — одинаковый во всех экземплярах приложения
    private const val HMAC_SECRET = "AuRusMesh_NodeKey_v1_2024"

    // 32 символа без визуально схожих пар (0/О, 1/I/L)
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /**
     * Та же канонизация nodenum, что при разборе ID узла (см. MeshWireNodeNum.parseToUInt).
     */
    private fun canonicalNodeIdHexForHmac(nodeId: String): String {
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

    private fun normalizePasswordInput(input: String): String =
        input.trim().uppercase(Locale.US).replace("-", "").replace(" ", "")

    /**
     * Генерирует пароль для указанного Node ID.
     * @param nodeId — идентификатор узла Meshtastic, например "!a1b2c3d4", `0x…`, с пробелами вокруг hex
     * @return пароль в формате "XXXX-XXXX"
     */
    fun generate(nodeId: String): String {
        val normalized = canonicalNodeIdHexForHmac(nodeId)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(normalized.toByteArray(Charsets.UTF_8))

        return buildString {
            for (i in 0 until 8) {
                if (i == 4) append('-')
                append(ALPHABET[(hash[i].toInt() and 0xFF) % ALPHABET.length])
            }
        }
    }

    /**
     * Проверяет введённый пароль для указанного Node ID.
     * Нечувствителен к регистру, пробелам по краям, дефисам и пробелам внутри пароля.
     */
    fun verify(nodeId: String, input: String): Boolean =
        normalizePasswordInput(generate(nodeId)) == normalizePasswordInput(input)
}
