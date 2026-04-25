package com.example.aura.sync

import android.content.Context
import android.util.Base64
import com.example.aura.BuildConfig
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.security.NodeAuthStore
import org.json.JSONObject

object AuraProfileExportQr {
    /**
     * QR payload for website camera import.
     * Format: aura://profile?nodeId=...&profile=... (profile is base64url(JSON))
     */
    fun buildQrTextBlocking(context: Context): String {
        val appCtx = context.applicationContext
        val rawNodeId = NodeAuthStore.load(appCtx)?.nodeId ?: NodeAuthStore.loadNodeIdForPrefetch(appCtx)
        val nodeIdHex = rawNodeId.trim().removePrefix("!").uppercase()

        val now = System.currentTimeMillis()
        val expiresAt = VipAccessPreferences.getExpiresAtMs(appCtx)
        val remainingAtSync = if (expiresAt > 0L) (expiresAt - now).coerceAtLeast(0L) else 0L
        val vipJson = JSONObject()
            .put("active", expiresAt > 0L && expiresAt > now)
            .put("remainingMs", remainingAtSync)
        if (expiresAt > 0L) {
            vipJson.put("untilMs", expiresAt)
        } else {
            vipJson.put("untilMs", JSONObject.NULL)
        }

        val longName = SiteProfileLongName.resolve(appCtx)
        val profile = JSONObject()
            .put("nodeId", nodeIdHex)
            .put("vip", vipJson)
            .put("lastSyncMs", now)
        if (longName != null) {
            profile.put("longName", longName)
        }

        val jsonBytes = profile.toString().toByteArray(Charsets.UTF_8)
        val b64url = Base64.encodeToString(
            jsonBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val sig = ProfileQrSignature.sign(b64url, BuildConfig.PROFILE_QR_HMAC_SECRET)
        return "aura://profile?nodeId=$nodeIdHex&profile=$b64url&sig=$sig"
    }
}
