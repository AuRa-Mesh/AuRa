package com.example.aura.sync

import android.content.Context
import android.util.Base64
import com.example.aura.BuildConfig
import com.example.aura.mesh.qr.AuraQr
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.security.NodeAuthStore
import org.json.JSONObject
import java.util.Locale

sealed class ProfileExportInAppResult {
    data class RestoredVip(val message: String) : ProfileExportInAppResult()
    data class OpenPeerProfile(val node: MeshWireNodeSummary) : ProfileExportInAppResult()
    data class Error(val message: String) : ProfileExportInAppResult()
}

/**
 * Скан [AuraQr.ProfileExport]: восстановление VIP для **своей** ноды или просмотр карточки **чужого** узла.
 */
object ProfileExportQrInAppHandler {

    private fun nodeId8Hex(s: String): String? {
        val n = MeshWireNodeNum.parseToUInt(s) ?: return null
        return String.format(Locale.US, "%08X", n.toLong() and 0xFFFF_FFFFL)
    }

    /** Как [com.example.aura.ui.chat.meshtasticPeerSummaryForDm] — без зависимости UI-модуля. */
    private fun peerSummaryPlaceholder(peerNodeNum: Long, nodes: List<MeshWireNodeSummary>): MeshWireNodeSummary {
        val n = peerNodeNum and 0xFFFF_FFFFL
        nodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == n }?.let { return it }
        val hex = MeshWireNodeNum.formatHex((n and 0xFFFF_FFFFL).toUInt())
            .trim()
            .removePrefix("!")
            .lowercase(Locale.ROOT)
        val idHex = "!$hex"
        return MeshWireNodeSummary(
            nodeNum = n,
            nodeIdHex = idHex,
            longName = idHex,
            shortName = "?",
            hardwareModel = "",
            roleLabel = "CLIENT",
            userId = null,
            lastHeardLabel = "—",
        )
    }

    private fun decodeProfileJson(export: AuraQr.ProfileExport): String? {
        var b = export.profileB64Url.trim()
        if (b.isNotEmpty() && b.length % 4 != 0) {
            b += "=".repeat(4 - b.length % 4)
        }
        return runCatching {
            String(
                Base64.decode(b, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8,
            )
        }.getOrNull()
    }

    fun handle(
        context: Context,
        export: AuraQr.ProfileExport,
        nodes: List<MeshWireNodeSummary>,
    ): ProfileExportInAppResult {
        val app = context.applicationContext
        val jsonStr = decodeProfileJson(export) ?: return ProfileExportInAppResult.Error("Не удалось прочитать данные в QR")

        val o = runCatching { JSONObject(jsonStr) }.getOrElse { return ProfileExportInAppResult.Error("Некорректный JSON в QR") }

        val idRaw = o.optString("nodeId", "").trim()
            .ifBlank { null }
            ?: o.optString("nodeIdHex", "").trim()
                .ifBlank { null }
            ?: export.nodeIdHex.trim()
        val claim8 = nodeId8Hex(idRaw) ?: return ProfileExportInAppResult.Error("В QR нет корректного nodeId")

        val mine = NodeAuthStore.loadNodeIdForPrefetch(app).trim()
        if (mine.isEmpty()) {
            return ProfileExportInAppResult.Error("Сначала введите Node ID и пароль ноды в приложении")
        }
        val mine8 = nodeId8Hex(mine) ?: return ProfileExportInAppResult.Error("В приложении некорректный Node ID")

        if (claim8 == mine8) {
            val err = SiteProfileQrRestore.applyFromProfileExport(context, export)
            return if (err == null) {
                ProfileExportInAppResult.RestoredVip("Профиль с сайта восстановлен (VIP).")
            } else {
                ProfileExportInAppResult.Error(err)
            }
        }

        val sig = export.sigB64Url?.trim().orEmpty()
        if (sig.isEmpty()) {
            return ProfileExportInAppResult.Error("QR без криптоподписи. Попросите обновить QR в приложении.")
        }
        val secret = BuildConfig.PROFILE_QR_HMAC_SECRET
        if (secret.isBlank() || !ProfileQrSignature.verify(export.profileB64Url, sig, secret)) {
            return ProfileExportInAppResult.Error("Подпись профиля не совпадает. Возможна подмена данных в QR.")
        }

        val n = MeshWireNodeNum.parseToUInt(claim8) ?: return ProfileExportInAppResult.Error("Некорректный nodeId в QR")
        val peerLong = n.toLong() and 0xFFFF_FFFFL
        var summary = peerSummaryPlaceholder(peerLong, nodes)
        val longName = o.optString("longName", "").trim().ifBlank { null }
        if (longName != null) {
            val short = longName.filter { it.isLetterOrDigit() || it == '_' }.take(4).ifBlank { "?" }
            summary = summary.copy(longName = longName, shortName = short)
        }
        return ProfileExportInAppResult.OpenPeerProfile(summary)
    }
}
