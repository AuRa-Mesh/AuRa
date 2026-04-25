package com.example.aura.sync

import android.content.Context
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.security.NodeAuthStore
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SiteSyncClient {

    /**
     * Пуш профиля приложения на сайт по одноразовому pairId+secret (QR с сайта).
     *
     * baseUrl обязателен, иначе телефон не знает куда слать (LAN/домены).
     */
    fun pushProfileToSiteBlocking(
        context: Context,
        baseUrl: String,
        pairId: String,
        secret: String,
    ) {
        val appCtx = context.applicationContext
        val nodeId = NodeAuthStore.load(appCtx)?.nodeId ?: NodeAuthStore.loadNodeIdForPrefetch(appCtx)

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
            .put("nodeId", nodeId.trim().removePrefix("!"))
            .put("vip", vipJson)
            .put("lastSyncMs", now)
        if (longName != null) {
            profile.put("longName", longName)
        }

        val payload = JSONObject()
            .put("pairId", pairId)
            .put("secret", secret)
            .put("payloadJson", profile.toString())

        val url = URL(baseUrl.trimEnd('/') + "/api/pair/push")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                w.write(payload.toString())
            }
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
            throw IllegalStateException("Site sync failed: HTTP $code ${err ?: ""}".trim())
        }
    }
}
