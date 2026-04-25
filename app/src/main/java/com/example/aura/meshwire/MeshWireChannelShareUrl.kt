package com.example.aura.meshwire

import android.util.Base64

/**
 * Ссылка вида `https://meshtastic.org/e/#…` с [ChannelSet] в base64url, как в приложении mesh.
 */
object MeshWireChannelShareUrl {
    private const val PREFIX = "https://meshtastic.org/e/#"

    fun buildFromChannelsResult(result: MeshWireChannelsSyncResult): String? {
        val active = result.channels.filter { it.role != MeshStoredChannel.ROLE_DISABLED }
        if (active.isEmpty()) return null
        val raw = MeshWireChannelToRadioEncoder.encodeChannelSetForShare(active)
        val b64 = Base64.encodeToString(
            raw,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return PREFIX + b64
    }

    /** Импорт каналов из URL (как «скан QR» в типичном mesh-клиенте). */
    fun parseChannelsFromUrl(url: String): MeshWireChannelsSyncResult? {
        val fragment = when {
            url.contains("#") -> url.substringAfter("#").trim()
            url.startsWith(PREFIX) -> url.removePrefix(PREFIX).trim()
            else -> return null
        }
        if (fragment.isEmpty()) return null
        val raw = try {
            Base64.decode(fragment, Base64.URL_SAFE or Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            try {
                Base64.decode(fragment, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        val acc = MeshWireChannelsSyncAccumulator()
        acc.ingestChannelSet(raw)
        return acc.toResult()
    }
}
