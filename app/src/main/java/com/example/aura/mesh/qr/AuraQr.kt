package com.example.aura.mesh.qr

/**
 * Унифицированный разбор QR для Aura:
 * - Профиль узла: `!XXXXXXXX`, `aura://node/...` (см. [NodeIdentityQr])
 * - Синхронизация с сайтом: `aura://pair?pairId=...&secret=...`
 * - Экспорт профиля (для импорта на сайт): `aura://profile?nodeId=...&profile=...`
 */
sealed class AuraQr {
    data class NodeIdentity(val nodeNum: UInt) : AuraQr()
    data class PairSync(val baseUrl: String?, val pairId: String, val secret: String) : AuraQr()
    data class ProfileExport(
        val nodeIdHex: String,
        val profileB64Url: String,
        /** HMAC-SHA256 (base64url), опционально для старых QR. */
        val sigB64Url: String?,
    ) : AuraQr()
    data class Unknown(val raw: String) : AuraQr()

    companion object {
        private const val PAIR_PREFIX = "aura://pair"
        private const val PROFILE_PREFIX = "aura://profile"

        fun parse(raw: String): AuraQr {
            val t = raw.trim().replace("\uFEFF", "")
            // pair/profile ДО node identity: иначе [MeshWireNodeNum.parseToUInt] соберёт «лишние» hex
            // из base64/URL (например в aura://profile?...&profile=...) и ложно вернёт [NodeIdentity].
            if (t.startsWith(PAIR_PREFIX, ignoreCase = true)) {
                val q = parseQuery(t.substringAfter("?", missingDelimiterValue = ""))
                val base = q["base"]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
                val pairId = q["pairId"].orEmpty()
                val secret = q["secret"].orEmpty()
                if (pairId.isNotBlank() && secret.isNotBlank()) return PairSync(baseUrl = base, pairId = pairId, secret = secret)
            }

            if (t.startsWith(PROFILE_PREFIX, ignoreCase = true)) {
                val q = parseQuery(t.substringAfter("?", missingDelimiterValue = ""))
                val nodeId = q["nodeId"].orEmpty()
                val profile = q["profile"].orEmpty()
                val sig = q["sig"]?.trim()?.takeIf { it.isNotEmpty() }
                if (nodeId.isNotBlank() && profile.isNotBlank()) {
                    return ProfileExport(nodeIdHex = nodeId, profileB64Url = profile, sigB64Url = sig)
                }
            }

            NodeIdentityQr.parseNodeNum(t)?.let { return NodeIdentity(it) }

            return Unknown(t)
        }

        private fun parseQuery(qs: String): Map<String, String> {
            if (qs.isBlank()) return emptyMap()
            return qs.split("&")
                .mapNotNull { part ->
                    if (part.isBlank()) return@mapNotNull null
                    val i = part.indexOf('=')
                    if (i <= 0) return@mapNotNull null
                    val k = part.substring(0, i)
                    val v = part.substring(i + 1)
                    k to runCatching { java.net.URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
                }
                .toMap()
        }
    }
}

