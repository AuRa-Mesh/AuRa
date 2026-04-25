package com.example.aura.ui.map

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.json.JSONObject

/** Диплинк «метка на карте» для вставки в чат. */
const val BEACON_SHARE_URI_AUTHORITY = "map"
const val BEACON_SHARE_URI_PATH = "/beacon"
const val BEACON_SHARE_SCHEME = "aura"

/** Старые клиенты и сохранённые сообщения могут содержать `aurus://…`. */
private const val BEACON_SHARE_SCHEME_LEGACY = "aurus"

data class BeaconSharePayload(
    val lat: Double,
    val lon: Double,
    val title: String,
    val ttlMs: Long,
    val color: String,
    val channelId: String,
    val channelIndex: Int,
    val channelTitle: String,
)

object BeaconShareLink {

    /** Совпадает с компактным ключом в [MapBeaconSyncRepository] (`k=c2`). */
    private const val BEACON_CHAT_WIRE_COMPACT = "c2"

    private val beaconShareUriAuthorityPrefix =
        "${BEACON_SHARE_SCHEME}://${BEACON_SHARE_URI_AUTHORITY}"

    private val linkRegex = Regex(
        "(?:${Regex.escape(BEACON_SHARE_SCHEME)}|${Regex.escape(BEACON_SHARE_SCHEME_LEGACY)})://" +
            "${Regex.escape(BEACON_SHARE_URI_AUTHORITY)}${Regex.escape(BEACON_SHARE_URI_PATH)}\\?[^\\s]+",
    )

    private fun rawContainsBeaconMapAuthorityPrefix(raw: String): Boolean =
        raw.contains(beaconShareUriAuthorityPrefix) ||
            raw.contains("${BEACON_SHARE_SCHEME_LEGACY}://${BEACON_SHARE_URI_AUTHORITY}")

    /** Сообщение целиком — только координаты (как из «геометки» или вставка из буфера), без ссылки. */
    private val coordRussianRegex = Regex(
        """^Широта\s+(-?\d+(?:\.\d+)?)\s*,\s*долгота\s+(-?\d+(?:\.\d+)?)$""",
        RegexOption.IGNORE_CASE,
    )
    private val coordCommaRegex = Regex("""^(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)$""")
    /** Две координаты с запятой как десятичным разделителем (копипаст с локали до фикса на карте). */
    private val coordCommaDecimalPairRegex = Regex("""^(-?\d+,\d+)\s*,\s*(-?\d+,\d+)$""")
    private val coordSpaceRegex = Regex("""^(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)$""")
    /** `55.0; 37.0` — иногда вставляется из сторонних приложений. */
    private val coordSemicolonRegex = Regex("""^(-?\d+(?:\.\d+)?)\s*;\s*(-?\d+(?:\.\d+)?)$""")

    /**
     * Убирает невидимые символы и «странные» пробелы/запятые из буфера обмена (Compose, клавиатуры),
     * чтобы `matchEntire` на координатах не ломался из‑за ZWSP/BOM и т.п.
     */
    private fun normalizeRawCoordinateMessageInput(raw: String): String {
        if (raw.isEmpty()) return raw
        val out = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '\uFEFF',
                '\u200B',
                '\u200C',
                '\u200D',
                '\u2060',
                '\u2066',
                '\u2067',
                '\u2068',
                '\u2069',
                -> Unit
                '\uFF0C' -> out.append(',')
                '\u2212',
                '\uFF0D',
                -> out.append('-')
                '\u00A0',
                '\u1680',
                '\u2000',
                '\u2001',
                '\u2002',
                '\u2003',
                '\u2004',
                '\u2005',
                '\u2006',
                '\u2007',
                '\u2008',
                '\u2009',
                '\u200A',
                '\u202F',
                '\u205F',
                '\u3000',
                -> out.append(' ')
                else -> out.append(ch)
            }
        }
        var s = out.toString().replace(Regex("[\r\n]+"), " ").trim()
        while (s.contains("  ")) s = s.replace("  ", " ")
        if (s.length >= 2) {
            val q0 = s.first()
            val q1 = s.last()
            if ((q0 == '"' && q1 == '"') || (q0 == '\u201C' && q1 == '\u201D') || (q0 == '\'' && q1 == '\'')) {
                s = s.substring(1, s.length - 1).trim()
                while (s.contains("  ")) s = s.replace("  ", " ")
            }
        }
        return s
    }

    /** Убирает невидимые символы из вставленного URI (не трогаем переводы строк — их [collapseBeaconUriNewlines]). */
    private fun stripBeaconUriPasteArtifacts(raw: String): String {
        if (raw.isEmpty()) return raw
        val out = StringBuilder(raw.length)
        for (ch in raw.trim()) {
            when (ch) {
                '\uFEFF',
                '\u200B',
                '\u200C',
                '\u200D',
                '\u2060',
                '\u2066',
                '\u2067',
                '\u2068',
                '\u2069',
                -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString().trim()
    }

    /** Склеивает переносы строк внутри вставленной ссылки (копипаст из окна / перенос строки в буфере). */
    private fun collapseBeaconUriNewlines(raw: String): String {
        if (!rawContainsBeaconMapAuthorityPrefix(raw)) return raw
        return raw.replace(Regex("[\r\n]+"), "")
    }

    /**
     * Разбор `aura://map/beacon?…` (и устаревшего `aurus://…`) после вставки из буфера: ZWSP/BOM, переносы строк, лишние кавычки.
     */
    fun parseUriLenient(uriString: String): BeaconSharePayload? {
        val a = uriString.trim()
        parseUri(a)?.let { return it }
        parseUri(stripBeaconUriPasteArtifacts(a))?.let { return it }
        parseUri(collapseBeaconUriNewlines(a))?.let { return it }
        parseUri(collapseBeaconUriNewlines(stripBeaconUriPasteArtifacts(a)))?.let { return it }
        val unquoted = stripOuterQuotesForPaste(a)
        if (unquoted != a) {
            parseUri(unquoted)?.let { return it }
            parseUri(stripBeaconUriPasteArtifacts(unquoted))?.let { return it }
            parseUri(collapseBeaconUriNewlines(unquoted))?.let { return it }
            parseUri(collapseBeaconUriNewlines(stripBeaconUriPasteArtifacts(unquoted)))?.let { return it }
        }
        return null
    }

    private fun stripOuterQuotesForPaste(s: String): String {
        var t = s.trim()
        if (t.length >= 2) {
            val q0 = t.first()
            val q1 = t.last()
            if ((q0 == '"' && q1 == '"') || (q0 == '\u201C' && q1 == '\u201D') || (q0 == '\'' && q1 == '\'')) {
                t = t.substring(1, t.length - 1).trim()
            }
        }
        return t
    }

    /**
     * Если [text] целиком похож на пару координат (русская подпись или два числа), возвращает (lat, lon).
     * Уже готовая ссылка `aura://map/beacon?…` (или `aurus://…`) не трогаем.
     */
    fun parseCoordinatesOnlyMessage(text: String): Pair<Double, Double>? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        // Не прогоняем «координатную» нормализацию (перевод строк → пробел) по нашей ссылке — она ломает URI.
        if (rawContainsBeaconMapAuthorityPrefix(raw)) {
            if (parseUriLenient(raw) != null) return null
            return null
        }
        val t = normalizeRawCoordinateMessageInput(text)
        if (t.isEmpty()) return null
        val onlyLink = firstLinkInText(t)
        if (onlyLink != null && onlyLink == t) return null
        fun parsePair(latStr: String, lonStr: String): Pair<Double, Double>? {
            val lat = latStr.toDoubleOrNull() ?: return null
            val lon = lonStr.toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return lat to lon
        }
        coordRussianRegex.matchEntire(t)?.let { m ->
            return parsePair(m.groupValues[1], m.groupValues[2])
        }
        coordCommaRegex.matchEntire(t)?.let { m ->
            return parsePair(m.groupValues[1], m.groupValues[2])
        }
        coordCommaDecimalPairRegex.matchEntire(t)?.let { m ->
            return parsePair(
                m.groupValues[1].replace(',', '.'),
                m.groupValues[2].replace(',', '.'),
            )
        }
        coordSpaceRegex.matchEntire(t)?.let { m ->
            return parsePair(m.groupValues[1], m.groupValues[2])
        }
        coordSemicolonRegex.matchEntire(t)?.let { m ->
            return parsePair(m.groupValues[1], m.groupValues[2])
        }
        return null
    }

    /** Текст ссылки на карту в пузырьке: компактно, как в чате/уведомлениях. */
    fun linkDisplayLabel(uri: String): String {
        return "Метка"
    }

    fun buildUri(payload: BeaconSharePayload): String =
        Uri.Builder()
            .scheme(BEACON_SHARE_SCHEME)
            .authority(BEACON_SHARE_URI_AUTHORITY)
            .path(BEACON_SHARE_URI_PATH)
            .appendQueryParameter("lat", payload.lat.toString())
            .appendQueryParameter("lon", payload.lon.toString())
            .appendQueryParameter("title", payload.title)
            .appendQueryParameter("ttlMs", payload.ttlMs.toString())
            .appendQueryParameter("color", payload.color)
            .appendQueryParameter("channelId", payload.channelId)
            .appendQueryParameter("channelIndex", payload.channelIndex.toString())
            .appendQueryParameter("channelTitle", payload.channelTitle)
            .build()
            .toString()

    fun parseUri(uriString: String): BeaconSharePayload? {
        val trimmed = uriString.trim()
        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme != BEACON_SHARE_SCHEME && uri.scheme != BEACON_SHARE_SCHEME_LEGACY) return null
        if (uri.authority != BEACON_SHARE_URI_AUTHORITY) return null
        val path = uri.path ?: return null
        if (path != BEACON_SHARE_URI_PATH && path != "/beacon" && path != "beacon") return null
        val lat = uri.getQueryParameter("lat")?.toDoubleOrNull() ?: return null
        val lon = uri.getQueryParameter("lon")?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val title = uri.getQueryParameter("title")?.trim().orEmpty().ifBlank { "Метка" }
        val ttlMs = uri.getQueryParameter("ttlMs")?.toLongOrNull()?.coerceAtLeast(MapBeaconViewModel.MIN_BEACON_TTL_MS)
            ?: MapBeaconViewModel.LEGACY_BEACON_TTL_MS
        val color = uri.getQueryParameter("color")?.trim().orEmpty().ifBlank { "#39E7FF" }
        val channelId = uri.getQueryParameter("channelId")?.trim().orEmpty().ifBlank { "ch_0" }
        val channelIndex = uri.getQueryParameter("channelIndex")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val channelTitle = uri.getQueryParameter("channelTitle")?.trim().orEmpty().ifBlank { "ch $channelIndex" }
        return BeaconSharePayload(
            lat = lat,
            lon = lon,
            title = title,
            ttlMs = ttlMs,
            color = color,
            channelId = channelId,
            channelIndex = channelIndex,
            channelTitle = channelTitle,
        )
    }

    /** Каноническая строка URI для отправки в mesh после вставки из буфера. */
    fun canonicalBeaconShareUriForSend(pasted: String): String? =
        parseUriLenient(pasted)?.let { buildUri(it) }

    fun firstLinkInText(text: String): String? = linkRegex.find(text)?.value

    private fun linkStylesFor(linkColor: Color) = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )

    private fun linkListener(onLinkUriClicked: (String) -> Unit) = LinkInteractionListener { link ->
        val u = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
        onLinkUriClicked(u)
    }

    private fun isAuraBeaconWireProtocol(aura: String): Boolean =
        aura == "beacon_v2" || aura == "beacon_v1"

    /**
     * JSON метки Aura в тексте чата (PRIVATE_APP wire) → payload для `aura://map/beacon?…`
     * и клик по алгоритму [onLinkUriClicked] (карта / импорт), без показа сырого JSON в пузыре.
     */
    private fun auraBeaconChatWirePayloadOrNull(raw: String): BeaconSharePayload? {
        val text = raw.trimStart('\uFEFF').trim()
        if (!MapBeaconSyncRepository.isAuraBeaconChatWireText(text)) return null
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return when {
            obj.optString("k") == BEACON_CHAT_WIRE_COMPACT -> auraBeaconCompactAddPayload(obj)
            else -> auraBeaconVerboseAddPayload(obj)
        }
    }

    private fun auraBeaconVerboseAddPayload(obj: JSONObject): BeaconSharePayload? {
        if (!isAuraBeaconWireProtocol(obj.optString("aura"))) return null
        if (obj.optString("op") != "add") return null
        if (obj.optBoolean("local_map", false)) return null
        val id = obj.optLong("id", 0L)
        val title = obj.optString("title", "").trim()
        val lat = obj.optDouble("lat", Double.NaN)
        val lon = obj.optDouble("lon", Double.NaN)
        if (id == 0L || title.isEmpty() || lat.isNaN() || lon.isNaN()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val chIndex = obj.optInt("ch", 0).coerceAtLeast(0)
        val ttlMs = obj.optLong("ttl_ms", MapBeaconViewModel.LEGACY_BEACON_TTL_MS)
            .coerceAtLeast(MapBeaconViewModel.MIN_BEACON_TTL_MS)
        val color = obj.optString("color", "").trim().ifBlank { "#39E7FF" }
        return BeaconSharePayload(
            lat = lat,
            lon = lon,
            title = title,
            ttlMs = ttlMs,
            color = color,
            channelId = mapChannelIdForIndex(chIndex),
            channelIndex = chIndex,
            channelTitle = "ch $chIndex",
        )
    }

    private fun auraBeaconCompactAddPayload(obj: JSONObject): BeaconSharePayload? {
        if (obj.optString("o") != "a") return null
        val id = obj.optLong("i", 0L)
        val title = obj.optString("t", "").trim()
        val lat = obj.optDouble("la", Double.NaN)
        val lon = obj.optDouble("lo", Double.NaN)
        if (id == 0L || title.isEmpty() || lat.isNaN() || lon.isNaN()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val chIndex = obj.optInt("c", 0).coerceAtLeast(0)
        val ttlMs = obj.optLong("l", MapBeaconViewModel.LEGACY_BEACON_TTL_MS)
            .coerceAtLeast(MapBeaconViewModel.MIN_BEACON_TTL_MS)
        val color = obj.optString("x", "").trim().ifBlank { "#39E7FF" }
        return BeaconSharePayload(
            lat = lat,
            lon = lon,
            title = title,
            ttlMs = ttlMs,
            color = color,
            channelId = mapChannelIdForIndex(chIndex),
            channelIndex = chIndex,
            channelTitle = "ch $chIndex",
        )
    }

    /** Одна кликабельная ссылка на всю строку (URI уже собран). */
    private fun annotatedSingleBeaconUrl(
        uri: String,
        linkColor: Color,
        onLinkUriClicked: (String) -> Unit,
    ): AnnotatedString {
        val styles = linkStylesFor(linkColor)
        val listener = linkListener(onLinkUriClicked)
        return buildAnnotatedString {
            withLink(
                LinkAnnotation.Url(
                    url = uri,
                    styles = styles,
                    linkInteractionListener = listener,
                ),
            ) {
                append(linkDisplayLabel(uri))
            }
        }
    }

    /**
     * Текст пузыря с кликабельными ссылками ([LinkAnnotation.Url]); [onLinkUriClicked] — полная строка URI.
     *
     * @param coordinatesAsLinkChannelIndex если вместе с [coordinatesAsLinkChannelTitle] заданы, сообщение,
     * которое целиком распознано как координаты без `aura://…`, рисуется как одна ссылка на карту (подпись см. [linkDisplayLabel]) для этого канала
     * (старые сообщения до отправки диплинком).
     */
    fun buildAnnotated(
        text: String,
        bodyColor: Color,
        linkColor: Color,
        onLinkUriClicked: (String) -> Unit,
        coordinatesAsLinkChannelIndex: Int? = null,
        coordinatesAsLinkChannelTitle: String? = null,
    ): AnnotatedString {
        if (text.isBlank()) return AnnotatedString("")
        auraBeaconChatWirePayloadOrNull(text)?.let { p ->
            return annotatedSingleBeaconUrl(
                buildUri(p),
                linkColor,
                onLinkUriClicked,
            )
        }
        val chIdx = coordinatesAsLinkChannelIndex
        val chTitle = coordinatesAsLinkChannelTitle
        if (chIdx != null && chTitle != null) {
            val coords = parseCoordinatesOnlyMessage(text)
            if (coords != null && linkRegex.find(text) == null) {
                val uri = buildUri(
                    BeaconSharePayload(
                        lat = coords.first,
                        lon = coords.second,
                        title = "Координаты",
                        ttlMs = MapBeaconViewModel.LEGACY_BEACON_TTL_MS,
                        color = "#39E7FF",
                        channelId = mapChannelIdForIndex(chIdx),
                        channelIndex = chIdx,
                        channelTitle = chTitle.ifBlank { "ch $chIdx" },
                    ),
                )
                return annotatedSingleBeaconUrl(uri, linkColor, onLinkUriClicked)
            }
        }
        val matches = linkRegex.findAll(text).toList()
        if (matches.isEmpty()) {
            return AnnotatedString(text)
        }
        val linkStyles = linkStylesFor(linkColor)
        val listener = linkListener(onLinkUriClicked)
        return buildAnnotatedString {
            var idx = 0
            for (m in matches) {
                if (m.range.first > idx) {
                    withStyle(SpanStyle(color = bodyColor)) {
                        append(text.substring(idx, m.range.first))
                    }
                }
                val uri = m.value
                withLink(
                    LinkAnnotation.Url(
                        url = uri,
                        styles = linkStyles,
                        linkInteractionListener = listener,
                    ),
                ) {
                    append(linkDisplayLabel(uri))
                }
                idx = m.range.last + 1
            }
            if (idx < text.length) {
                withStyle(SpanStyle(color = bodyColor)) {
                    append(text.substring(idx))
                }
            }
        }
    }
}
