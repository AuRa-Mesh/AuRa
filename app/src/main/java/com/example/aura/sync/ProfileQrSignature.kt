package com.example.aura.sync

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 подпись полезной нагрузки `profile` в `aura://profile?...&profile=...&sig=...`.
 * Секрет хранится на сервере и в приложении ([com.example.aura.BuildConfig.PROFILE_QR_HMAC_SECRET]),
 * не в браузерном JS — подделать VIP без секрета нельзя.
 */
object ProfileQrSignature {

    private const val MAC_ALG = "HmacSHA256"
    private const val PREFIX = "v1\n"

    fun sign(profileB64Url: String, secret: String): String {
        if (secret.isBlank()) return ""
        val mac = Mac.getInstance(MAC_ALG)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), MAC_ALG))
        val sig = mac.doFinal((PREFIX + profileB64Url).toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(sig, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun verify(profileB64Url: String, sigB64Url: String, secret: String): Boolean {
        if (secret.isBlank() || sigB64Url.isBlank()) return false
        val expected = sign(profileB64Url, secret)
        val a = decodeB64UrlToBytes(expected) ?: return false
        val b = decodeB64UrlToBytes(sigB64Url) ?: return false
        return MessageDigest.isEqual(a, b)
    }

    private fun decodeB64UrlToBytes(s: String): ByteArray? =
        runCatching {
            var t = s.trim().replace('-', '+').replace('_', '/')
            val mod = t.length % 4
            if (mod == 2) t += "=="
            else if (mod == 3) t += "="
            else if (mod == 1 && t.isNotEmpty()) return null
            Base64.decode(t, Base64.DEFAULT)
        }.getOrNull()
}
