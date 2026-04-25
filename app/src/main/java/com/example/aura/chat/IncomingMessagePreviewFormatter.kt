package com.example.aura.chat

import com.example.aura.ui.map.BeaconShareLink
import com.example.aura.ui.map.MapBeaconSyncRepository

/**
 * Короткая подпись для списков диалогов, пузырьков на главном экране и системных уведомлений.
 * Логика совпадает с прежним [com.example.aura.notifications.MeshNotificationDispatcher] (один источник правды).
 */
object IncomingMessagePreviewFormatter {

    private const val LOCAL_GIF_PREFIX = "GIF_LOCAL:"
    private const val POLL_PREFIX = "📊 ОПРОС"
    private const val LIST_PREFIX = "🧾 СПИСОК"
    private const val LIST_UPDATE_PREFIX = "🧾U"
    private const val URGENT_MESH_PREFIX = "⚡URGENT"
    private const val URGENT_ACK_MESH_PREFIX = "⚡ACK"
    private const val POLL_VOTE_FIRST_LINE = "🗳️ VOTE"

    private fun firstNonEmptyLine(text: String): String =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    private fun isPollVoteWireText(trimmed: String): Boolean {
        val lines = trimmed.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 2 && lines[0].equals(POLL_VOTE_FIRST_LINE, ignoreCase = true)) {
            return lines[1].substringAfter("id:", "").trim().isNotEmpty()
        }
        val collapsed = trimmed.replace('\n', ' ').trim()
        if (!collapsed.startsWith(POLL_VOTE_FIRST_LINE, ignoreCase = true)) return false
        val tail = collapsed.substring(POLL_VOTE_FIRST_LINE.length).trimStart()
        return tail.startsWith("id:", ignoreCase = true) &&
            tail.substringAfter("id:", "").trim().isNotEmpty()
    }

    private val checklistToggleCollapsedPattern = Regex(
        """(?i)id:\S+\s+i:\d+\s+v:(?:[01]|true|false)""",
    )

    private fun isChecklistToggleWireText(trimmed: String): Boolean {
        val lines = trimmed.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 4 && lines[0].equals(LIST_UPDATE_PREFIX, ignoreCase = true)) {
            val listId = lines[1].substringAfter("id:", "").trim()
            val itemIdx = lines[2].substringAfter("i:", "").trim().toIntOrNull() ?: return false
            val vRaw = lines[3].substringAfter("v:", "").trim()
            if (listId.isEmpty() || itemIdx < 0 || vRaw.isEmpty()) return false
            return vRaw == "1" || vRaw == "0" ||
                vRaw.equals("true", ignoreCase = true) ||
                vRaw.equals("false", ignoreCase = true)
        }
        val collapsed = trimmed.replace('\n', ' ').trim()
        if (!collapsed.regionMatches(0, LIST_UPDATE_PREFIX, 0, LIST_UPDATE_PREFIX.length, ignoreCase = true)) {
            return false
        }
        val after = collapsed.substring(LIST_UPDATE_PREFIX.length).trimStart()
        return checklistToggleCollapsedPattern.containsMatchIn(after)
    }

    fun previewLabel(
        raw: String?,
        beaconPreviewLabel: String,
        pollVotePreviewLabel: String,
        checklistTogglePreviewLabel: String,
    ): String {
        val cleaned = raw?.let { com.example.aura.vip.VipWireMarker.parseAndStrip(it).first }
        val trimmed = cleaned?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        return when {
            MapBeaconSyncRepository.isAuraBeaconChatWireText(trimmed) ||
                BeaconShareLink.parseUriLenient(trimmed) != null ||
                BeaconShareLink.parseCoordinatesOnlyMessage(trimmed) != null -> beaconPreviewLabel
            trimmed.startsWith(LOCAL_GIF_PREFIX) -> "GIF"
            isPollVoteWireText(trimmed) -> pollVotePreviewLabel
            trimmed.startsWith(POLL_PREFIX, ignoreCase = true) || trimmed.startsWith("📊") -> "Опрос"
            isChecklistToggleWireText(trimmed) -> checklistTogglePreviewLabel
            trimmed.startsWith(LIST_PREFIX, ignoreCase = true) || trimmed.startsWith("🧾") -> "Список"
            trimmed.startsWith(URGENT_MESH_PREFIX, ignoreCase = true) -> "Срочно"
            trimmed.startsWith(URGENT_ACK_MESH_PREFIX, ignoreCase = true) -> "Подтверждение"
            else -> trimmed
        }
    }
}
