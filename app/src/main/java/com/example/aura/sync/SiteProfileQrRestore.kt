package com.example.aura.sync

import android.content.Context
import android.util.Base64
import com.example.aura.BuildConfig
import com.example.aura.mesh.qr.AuraQr
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.security.NodeAuthStore
import org.json.JSONObject
import java.util.Locale

/**
 * Импорт профиля с сайта по QR `aura://profile?...` (восстановление **своей** ноды), а не открытие чужого узла.
 */
object SiteProfileQrRestore {

    private fun nodeId8Hex(s: String): String? {
        val n = MeshWireNodeNum.parseToUInt(s) ?: return null
        return String.format(Locale.US, "%08X", n.toLong() and 0xFFFF_FFFFL)
    }

    /**
     * @return `null` при успехе, иначе текст ошибки для Toast.
     */
    fun applyFromProfileExport(
        context: Context,
        export: AuraQr.ProfileExport,
    ): String? {
        val app = context.applicationContext
        var b = export.profileB64Url.trim()
        if (b.isNotEmpty() && b.length % 4 != 0) {
            b += "=".repeat(4 - b.length % 4)
        }
        val jsonStr = runCatching {
            String(
                Base64.decode(b, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8,
            )
        }.getOrElse { return "Не удалось прочитать данные в QR" }

        val o = runCatching { JSONObject(jsonStr) }.getOrElse { return "Некорректный JSON в QR" }

        val idRaw = o.optString("nodeId", "").trim()
            .ifBlank { null }
            ?: o.optString("nodeIdHex", "").trim()
                .ifBlank { null }
            ?: export.nodeIdHex.trim()
        val claim8 = nodeId8Hex(idRaw) ?: return "В QR нет корректного nodeId"

        val mine = NodeAuthStore.loadNodeIdForPrefetch(app).trim()
        if (mine.isEmpty()) {
            return "Сначала введите Node ID и пароль ноды в приложении"
        }
        val mine8 = nodeId8Hex(mine) ?: return "В приложении некорректный Node ID"
        if (claim8 != mine8) {
            return "Этот QR с сайта — для ноды !$claim8. У вас в приложении: !$mine8"
        }

        val sig = export.sigB64Url?.trim().orEmpty()
        if (sig.isEmpty()) {
            return "QR без криптоподписи (устарел). Откройте сайт и сгенерируйте QR восстановления заново."
        }
        val secret = BuildConfig.PROFILE_QR_HMAC_SECRET
        if (secret.isBlank() ||
            !ProfileQrSignature.verify(export.profileB64Url, sig, secret)
        ) {
            return "Подпись профиля не совпадает. Возможна подмена данных в QR."
        }

        applyVipFromJson(app, o)
        return null
    }

    private fun applyVipFromJson(ctx: Context, o: JSONObject) {
        if (o.isNull("vip")) return
        val v = o.optJSONObject("vip") ?: return
        val lastSync = o.optLong("lastSyncMs", 0L)
        var until = if (v.isNull("untilMs")) 0L else v.optLong("untilMs", 0L)
        if (until <= 0L && lastSync > 0L) {
            val rem = v.optLong("remainingMs", -1L)
            if (rem > 0L) {
                until = lastSync + rem
            }
        }
        if (until > 0L) {
            VipAccessPreferences.setExpiresAtMs(ctx, until)
            return
        }
        if (v.isNull("untilMs") && !v.optBoolean("active", false)) {
            VipAccessPreferences.setExpiresAtMs(ctx, 0L)
            return
        }
        if (!v.isNull("untilMs") && v.optLong("untilMs", 0L) <= 0L && !v.optBoolean("active", true)) {
            VipAccessPreferences.setExpiresAtMs(ctx, 0L)
        }
    }
}
