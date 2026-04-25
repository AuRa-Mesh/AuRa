package com.example.aura.ui.history

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aura.R
import com.example.aura.AuraApplication
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.util.NodeIdHex
import com.example.aura.data.local.MessageHistoryEntryEntity
import com.example.aura.data.local.MessageHistoryType
import com.example.aura.history.MessageHistoryRepository
import com.example.aura.history.MessageHistorySecureAuth
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.ui.map.BeaconShareLink
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val BgDeep = Color(0xFF0A0A14)
private val AccentNeon = Color(0xFF00E676)
private val BubbleBg = Color(0xFF152028)

private val DialogBg = Color(0xFF0D1A2E)
private val DialogCard = Color(0xFF0D2535)
private val DialogAccent = Color(0xFF00E676)
private val DialogText = Color(0xFFDDE8F0)
private val DialogTextSec = Color(0xFF8A9AAA)
private val DialogRed = Color(0xFFFF5252)
private const val HistoryPinnedBlueNodeIdHex = "2ACBCF40"
private val HistoryPinnedBlue = Color(0xFF6C94EC)
private val HistoryBubblePalette = listOf(
    Color(0xFF2F7D44),
    Color(0xFF3A8F55),
    Color(0xFF5A6473),
    Color(0xFF6A7280),
    Color(0xFF2E6FD6),
    Color(0xFF3F7FE0),
    Color(0xFF3A7E86),
    Color(0xFF4B6B89),
)
private const val HistoryPollMessagePrefix = "📊 ОПРОС"
private const val HistoryChecklistMessagePrefix = "🧾 СПИСОК"
private const val HistoryChecklistUpdatePrefix = "🧾U"
private const val HistoryUrgentMeshFirstLine = "⚡URGENT"
private const val HistoryUrgentAckMeshFirstLine = "⚡ACK"
private val HistoryUrlRegex = Regex("""((https?|aura)://\S+)""", RegexOption.IGNORE_CASE)

private data class HistoryUrgentMeshPayload(val id: String, val body: String)
private data class HistoryUrgentAckMeshPayload(val id: String, val timeMs: Long?)
private data class HistoryPollPayload(
    val pollId: String?,
    val question: String,
    val options: List<String>,
    val anonymous: Boolean,
)
private data class HistoryPollVotePayload(
    val pollId: String,
    val optionIndexes: Set<Int>,
)
private data class HistoryPollVoteSnapshot(
    val totalVotes: Int,
    val optionVoteCounts: Map<Int, Int>,
    val optionVoters: Map<Int, List<Long>>,
)
private data class HistoryChecklistPayload(
    val listId: String?,
    val title: String,
    val items: List<String>,
)
private data class HistoryChecklistTogglePayload(
    val listId: String,
    val itemIndex: Int,
    val checked: Boolean,
)
private data class HistoryChecklistItemState(
    val checked: Boolean,
    val ownerNodeNum: Long? = null,
)

private fun historyBubbleShape(outgoing: Boolean): RoundedCornerShape =
    if (outgoing) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 0.dp, bottomStart = 12.dp)
    } else {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 0.dp)
    }

private fun historyBubbleColorIndex(nodeIdHex: String): Int {
    var h = 2166136261L
    for (ch in nodeIdHex) {
        h = (h xor ch.code.toLong()) * 16777619L
        h = h and 0xFFFF_FFFFL
    }
    return (h % HistoryBubblePalette.size.toLong()).toInt()
}

private fun historyBubbleColorForNode(nodeIdHex: String?): Color {
    val normalized = nodeIdHex?.let { NodeIdHex.normalize(it) }?.takeIf { it.isNotBlank() } ?: return Color(0xFF5A6473)
    if (normalized == HistoryPinnedBlueNodeIdHex) return HistoryPinnedBlue
    return HistoryBubblePalette[historyBubbleColorIndex(normalized)]
}

private fun historyBubbleTextColor(bg: Color): Color =
    if (bg.luminance() < 0.42f) Color.White else Color(0xFF12263A)

private fun messageHistoryEntrySearchHaystack(
    entry: MessageHistoryEntryEntity,
    voicePreviewLabel: String,
    imagePreviewLabel: String,
    historyNodes: List<MeshWireNodeSummary>,
): String {
    val sb = StringBuilder()
    sb.append(entry.textBody.orEmpty())
    sb.append(' ')
    if (entry.fromNodeNum != 0L) {
        val u = (entry.fromNodeNum and 0xFFFF_FFFFL).toUInt()
        val hex = MeshWireNodeNum.formatHex(u).lowercase(Locale.ROOT)
        sb.append(hex).append(' ').append(hex.removePrefix("!")).append(' ')
        val node = historyNodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == (entry.fromNodeNum and 0xFFFF_FFFFL) }
        node?.longName?.trim()?.takeIf { it.isNotBlank() }?.let {
            sb.append(it.lowercase(Locale.getDefault())).append(' ')
        }
        node?.shortName?.trim()?.takeIf { it.isNotBlank() && it != "?" }?.let {
            sb.append(it.lowercase(Locale.getDefault())).append(' ')
        }
    }
    when (MessageHistoryType.fromCode(entry.type)) {
        MessageHistoryType.TEXT -> Unit
        MessageHistoryType.VOICE -> sb.append(voicePreviewLabel)
        MessageHistoryType.IMAGE -> sb.append(imagePreviewLabel)
        MessageHistoryType.COORDINATES -> {
            entry.latitude?.let { sb.append(it).append(' ') }
            entry.longitude?.let { sb.append(it).append(' ') }
        }
        MessageHistoryType.SERVICE -> {
            sb.append(entry.serviceKind.orEmpty()).append(' ')
            sb.append(entry.servicePayloadJson.orEmpty())
        }
    }
    return sb.toString().lowercase(Locale.getDefault())
}

private fun parseHistoryUrgentMeshMessage(text: String): HistoryUrgentMeshPayload? {
    val lines = text.trimEnd().split('\n').map { it.trimEnd() }
    if (lines.size < 3) return null
    if (!lines[0].equals(HistoryUrgentMeshFirstLine, ignoreCase = true)) return null
    val idLine = lines[1].trim()
    if (!idLine.startsWith("id:", ignoreCase = true)) return null
    val id = idLine.substringAfter(":", "").trim()
    if (id.isBlank()) return null
    val body = lines.drop(2).joinToString("\n").trim()
    return HistoryUrgentMeshPayload(id = id, body = body)
}

private fun parseHistoryUrgentAckMeshMessage(text: String): HistoryUrgentAckMeshPayload? {
    val lines = text.trim().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 2) return null
    if (!lines[0].equals(HistoryUrgentAckMeshFirstLine, ignoreCase = true)) return null
    val idLine = lines[1]
    if (!idLine.startsWith("id:", ignoreCase = true)) return null
    val id = idLine.substringAfter(":", "").trim()
    if (id.isBlank()) return null
    val timeLine = lines.getOrNull(2).orEmpty()
    val timeMs = if (timeLine.startsWith("t:", ignoreCase = true)) {
        timeLine.substringAfter(":", "").trim().toLongOrNull()
    } else {
        null
    }
    return HistoryUrgentAckMeshPayload(id = id, timeMs = timeMs)
}

private fun compactHistoryStructuredLabel(raw: String): String? {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith(HistoryPollMessagePrefix, ignoreCase = true) || trimmed.startsWith("📊") -> "Опрос"
        trimmed.startsWith(HistoryChecklistUpdatePrefix, ignoreCase = true) ||
            trimmed.startsWith(HistoryChecklistMessagePrefix, ignoreCase = true) ||
            trimmed.startsWith("🧾") -> "Список"
        trimmed.startsWith(HistoryUrgentMeshFirstLine, ignoreCase = true) -> "Срочно"
        trimmed.startsWith(HistoryUrgentAckMeshFirstLine, ignoreCase = true) -> "Подтверждение"
        else -> null
    }
}

private fun formatHistoryCoordinates(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.6f, %.6f", lat, lon)

private fun parseHistoryPollMessage(text: String): HistoryPollPayload? {
    val lines = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.size < 4) return null
    if (!lines.first().startsWith("📊 ОПРОС")) return null
    val hasIdLine = lines.getOrNull(1)?.lowercase(Locale.ROOT)?.startsWith("id:") == true
    val pollId = if (hasIdLine) lines.getOrNull(1)?.substringAfter("id:", "")?.trim()?.takeIf { it.isNotBlank() } else null
    val questionIndex = if (hasIdLine) 2 else 1
    val metaIndex = questionIndex + 1
    val optionsStart = metaIndex + 1
    val question = lines.getOrNull(questionIndex)?.takeIf { it.isNotBlank() } ?: return null
    val meta = lines.getOrNull(metaIndex)?.lowercase(Locale.getDefault()).orEmpty()
    val options = lines.drop(optionsStart).mapNotNull { line ->
        val idx = line.indexOf(")")
        if (idx <= 0 || idx >= line.lastIndex) return@mapNotNull null
        line.substring(idx + 1).trim().takeIf { it.isNotEmpty() }
    }
    if (options.size < 2) return null
    val anonymous = "неанонимный" !in meta
    return HistoryPollPayload(
        pollId = pollId,
        question = question,
        options = options,
        anonymous = anonymous,
    )
}

private fun parseHistoryPollVoteMessage(text: String): HistoryPollVotePayload? {
    val lines = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.size < 2) return null
    if (!lines.first().equals("🗳️ VOTE", ignoreCase = true)) return null
    val pollId = lines.getOrNull(1)?.substringAfter("id:", "")?.trim().orEmpty()
    if (pollId.isBlank()) return null
    val options = lines.getOrNull(2)
        ?.substringAfter("opts:", "")
        ?.split(',')
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.filter { it >= 0 }
        ?.toSet()
        ?: emptySet()
    return HistoryPollVotePayload(
        pollId = pollId,
        optionIndexes = options,
    )
}

private fun parseHistoryChecklistMessage(text: String): HistoryChecklistPayload? {
    val lines = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.size < 3) return null
    if (!lines.first().startsWith("🧾 СПИСОК")) return null
    val hasIdLine = lines.getOrNull(1)?.lowercase(Locale.ROOT)?.startsWith("id:") == true
    val listId = if (hasIdLine) lines.getOrNull(1)?.substringAfter("id:", "")?.trim()?.takeIf { it.isNotBlank() } else null
    val titleIndex = if (hasIdLine) 2 else 1
    val itemsStart = titleIndex + 1
    val title = lines.getOrNull(titleIndex)?.takeIf { it.isNotBlank() } ?: return null
    val items = lines.drop(itemsStart).mapNotNull { line ->
        val idx = line.indexOf(")")
        if (idx <= 0 || idx >= line.lastIndex) return@mapNotNull null
        line.substring(idx + 1).trim().takeIf { it.isNotEmpty() }
    }
    if (items.isEmpty()) return null
    return HistoryChecklistPayload(
        listId = listId,
        title = title,
        items = items,
    )
}

private fun parseHistoryChecklistToggleMessage(text: String): HistoryChecklistTogglePayload? {
    val lines = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.size < 4) return null
    if (!lines.first().equals("🧾U", ignoreCase = true)) return null
    val listId = lines.getOrNull(1)?.substringAfter("id:", "")?.trim().orEmpty()
    if (listId.isBlank()) return null
    val itemIndex = lines.getOrNull(2)?.substringAfter("i:", "")?.trim()?.toIntOrNull() ?: return null
    if (itemIndex < 0) return null
    val checkedRaw = lines.getOrNull(3)?.substringAfter("v:", "")?.trim().orEmpty()
    val checked = checkedRaw == "1" || checkedRaw.equals("true", ignoreCase = true)
    return HistoryChecklistTogglePayload(
        listId = listId,
        itemIndex = itemIndex,
        checked = checked,
    )
}

private fun buildHistoryPollVoteSnapshots(entries: List<MessageHistoryEntryEntity>): Map<String, HistoryPollVoteSnapshot> {
    val latestByPollAndVoter = mutableMapOf<String, MutableMap<Long, Pair<Long, Set<Int>>>>()
    entries.sortedBy { it.createdAtEpochMs }.forEach { entry ->
        if (MessageHistoryType.fromCode(entry.type) != MessageHistoryType.TEXT) return@forEach
        val vote = parseHistoryPollVoteMessage(entry.textBody.orEmpty()) ?: return@forEach
        val voter = entry.fromNodeNum and 0xFFFF_FFFFL
        val perPoll = latestByPollAndVoter.getOrPut(vote.pollId) { mutableMapOf() }
        val prev = perPoll[voter]
        if (prev == null || entry.createdAtEpochMs >= prev.first) {
            perPoll[voter] = entry.createdAtEpochMs to vote.optionIndexes
        }
    }
    return latestByPollAndVoter.mapValues { (_, perVoter) ->
        val optionCounts = mutableMapOf<Int, Int>()
        val optionVoters = mutableMapOf<Int, MutableList<Long>>()
        val effective = perVoter.entries
            .map { it.key to it.value.second }
            .filter { it.second.isNotEmpty() }
        effective.forEach { (voter, selected) ->
            selected.forEach { idx ->
                optionCounts[idx] = (optionCounts[idx] ?: 0) + 1
                optionVoters.getOrPut(idx) { mutableListOf() }.add(voter)
            }
        }
        HistoryPollVoteSnapshot(
            totalVotes = effective.size,
            optionVoteCounts = optionCounts,
            optionVoters = optionVoters.mapValues { (_, voters) -> voters.distinct().sorted() },
        )
    }
}

private fun buildHistoryChecklistStateSnapshots(entries: List<MessageHistoryEntryEntity>): Map<String, Map<Int, HistoryChecklistItemState>> {
    val byList = mutableMapOf<String, MutableMap<Int, HistoryChecklistItemState>>()
    entries.sortedBy { it.createdAtEpochMs }.forEach { entry ->
        if (MessageHistoryType.fromCode(entry.type) != MessageHistoryType.TEXT) return@forEach
        val upd = parseHistoryChecklistToggleMessage(entry.textBody.orEmpty()) ?: return@forEach
        val map = byList.getOrPut(upd.listId) { mutableMapOf() }
        val actor = entry.fromNodeNum and 0xFFFF_FFFFL
        val current = map[upd.itemIndex]
        if (upd.checked) {
            if (current?.checked == true) return@forEach
            map[upd.itemIndex] = HistoryChecklistItemState(checked = true, ownerNodeNum = actor)
        } else {
            if (current?.checked == true && current.ownerNodeNum == actor) {
                map[upd.itemIndex] = HistoryChecklistItemState(checked = false, ownerNodeNum = null)
            }
        }
    }
    return byList.mapValues { it.value.toMap() }
}

private fun isHistoryControlText(raw: String): Boolean {
    val text = raw.trim()
    return parseHistoryPollVoteMessage(text) != null ||
        parseHistoryChecklistToggleMessage(text) != null ||
        parseHistoryUrgentAckMeshMessage(text) != null
}

private fun filterMessageHistoryDaySections(
    sections: List<MessageHistoryDaySection>,
    query: String,
    voiceLabel: String,
    imageLabel: String,
    historyNodes: List<MeshWireNodeSummary>,
): List<MessageHistoryDaySection> {
    val q = query.trim().lowercase(Locale.getDefault())
    if (q.isEmpty()) return sections
    return sections.mapNotNull { sec ->
        val filtered = sec.items.filter { e ->
            messageHistoryEntrySearchHaystack(e, voiceLabel, imageLabel, historyNodes).contains(q)
        }
        if (filtered.isEmpty()) null else MessageHistoryDaySection(sec.date, filtered)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageHistoryScreen(
    meshNodeId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AuraApplication
    val vm: MessageHistoryViewModel = viewModel(factory = MessageHistoryViewModel.Factory(app.messageHistoryRepository))

    val enteredWithExistingPassword = remember { MessageHistorySecureAuth.hasPassword(context) }
    var passwordSetup by remember { mutableStateOf(!enteredWithExistingPassword) }
    var passwordSetupDismissToBack by remember { mutableStateOf(true) }
    var unlocked by remember { mutableStateOf(false) }
    var pwdField by remember { mutableStateOf("") }
    var pwdSetupConfirm by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf<String?>(null) }
    var resetPasswordConfirmOpen by remember { mutableStateOf(false) }
    var requestCurrentPasswordOpen by remember { mutableStateOf(false) }
    var currentPwdForReset by remember { mutableStateOf("") }
    var currentPwdError by remember { mutableStateOf<String?>(null) }

    val selectedGroupId by vm.selectedGroupId.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val daySections by vm.daySections.collectAsStateWithLifecycle()

    var historySearchOpen by remember { mutableStateOf(false) }
    var historySearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(selectedGroupId) {
        historySearchOpen = false
        historySearchQuery = ""
    }

    if (passwordSetup) {
        MessageHistoryPasswordSetupDialog(
            onDismissRequest = {
                if (passwordSetupDismissToBack) onBack()
                else {
                    passwordSetup = false
                    pwdField = ""
                    pwdSetupConfirm = ""
                    pwdError = null
                }
            },
            pwdField = pwdField,
            pwdSetupConfirm = pwdSetupConfirm,
            pwdError = pwdError,
            onPwdChange = { pwdField = it; pwdError = null },
            onPwdConfirmChange = { pwdSetupConfirm = it; pwdError = null },
            onConfirm = {
                if (pwdField.length < 4) {
                    pwdError = context.getString(R.string.message_history_password_short)
                    return@MessageHistoryPasswordSetupDialog
                }
                if (pwdField != pwdSetupConfirm) {
                    pwdError = context.getString(R.string.message_history_password_mismatch)
                    return@MessageHistoryPasswordSetupDialog
                }
                MessageHistorySecureAuth.setPassword(context, pwdField)
                passwordSetup = false
                passwordSetupDismissToBack = true
                unlocked = true
                pwdField = ""
                pwdSetupConfirm = ""
            },
        )
        return
    }

    if (resetPasswordConfirmOpen) {
        AlertDialog(
            onDismissRequest = { resetPasswordConfirmOpen = false },
            title = { Text("Сброс пароля", color = DialogText) },
            text = { Text("При сбросе пароля вся история удалится", color = DialogTextSec) },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetPasswordConfirmOpen = false
                        vm.clearHistory(
                            groupId = null,
                            onDone = {},
                            onError = {},
                        )
                        passwordSetupDismissToBack = false
                        passwordSetup = true
                        unlocked = false
                        pwdField = ""
                        pwdSetupConfirm = ""
                        pwdError = null
                    },
                ) {
                    Text("Да", color = DialogAccent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        resetPasswordConfirmOpen = false
                    },
                ) {
                    Text("Нет", color = DialogTextSec)
                }
            },
            containerColor = DialogBg,
            titleContentColor = DialogText,
            textContentColor = DialogTextSec,
        )
    }

    if (requestCurrentPasswordOpen) {
        MessageHistoryCurrentPasswordDialog(
            onDismissRequest = {
                requestCurrentPasswordOpen = false
                currentPwdForReset = ""
                currentPwdError = null
            },
            pwdField = currentPwdForReset,
            pwdError = currentPwdError,
            onPwdChange = {
                currentPwdForReset = it
                currentPwdError = null
            },
            onConfirm = {
                if (MessageHistorySecureAuth.verify(context, currentPwdForReset)) {
                    requestCurrentPasswordOpen = false
                    currentPwdForReset = ""
                    currentPwdError = null
                    passwordSetupDismissToBack = false
                    passwordSetup = true
                    unlocked = false
                    pwdField = ""
                    pwdSetupConfirm = ""
                    pwdError = null
                } else {
                    currentPwdError = context.getString(R.string.message_history_password_wrong)
                }
            },
        )
    }

    if (!unlocked) {
        MessageHistoryLoginDialog(
            meshNodeId = meshNodeId,
            onDismissRequest = onBack,
            pwdField = pwdField,
            pwdError = pwdError,
            onPwdChange = { pwdField = it; pwdError = null },
            onResetPassword = { resetPasswordConfirmOpen = true },
            onUnlock = {
                if (MessageHistorySecureAuth.verify(context, pwdField)) {
                    unlocked = true
                    pwdField = ""
                    pwdError = null
                } else {
                    pwdError = context.getString(R.string.message_history_password_wrong)
                }
            },
        )
        return
    }

    BackHandler {
        if (selectedGroupId != null) vm.selectGroup(null) else onBack()
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.message_history_title),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedGroupId != null) vm.selectGroup(null)
                        else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedGroupId != null) {
                                historySearchOpen = !historySearchOpen
                                if (!historySearchOpen) historySearchQuery = ""
                            }
                        },
                        enabled = selectedGroupId != null,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.message_history_search),
                            tint = if (historySearchOpen) AccentNeon else Color.White,
                        )
                    }
                    IconButton(
                        onClick = {
                            vm.clearHistory(
                                groupId = selectedGroupId,
                                onDone = {},
                                onError = {},
                            )
                        },
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.message_history_clear),
                            tint = DialogRed,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1525)),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgDeep),
        ) {
            if (selectedGroupId == null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(groups, key = { it.groupId }) { g ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.selectGroup(g.groupId) },
                            shape = RoundedCornerShape(18.dp),
                            color = BubbleBg,
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    MessageHistoryRepository.displayTitleForGroup(g),
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    g.groupId,
                                    color = Color(0xFF78909C),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            } else {
                val timeFmt = remember {
                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
                        .withZone(ZoneId.systemDefault())
                }
                val dayFmt = remember {
                    DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
                }
                val selectedGroup = remember(selectedGroupId, groups) {
                    groups.firstOrNull { it.groupId == selectedGroupId }
                }
                val historyNodes = remember(selectedGroup?.deviceMac, context) {
                    val mac = selectedGroup?.deviceMac?.trim().orEmpty()
                    if (mac.isBlank()) emptyList()
                    else MeshNodeListDiskCache.load(context.applicationContext, mac).orEmpty()
                }
                val voicePreview = stringResource(R.string.channel_preview_voice)
                val imagePreview = stringResource(R.string.channel_preview_image)
                val displaySections = remember(daySections, historySearchQuery, voicePreview, imagePreview, historyNodes) {
                    filterMessageHistoryDaySections(
                        daySections,
                        historySearchQuery,
                        voicePreview,
                        imagePreview,
                        historyNodes,
                    )
                }
                val allEntries = remember(daySections) {
                    daySections.flatMap { it.items }
                }
                val pollVoteSnapshots = remember(allEntries) {
                    buildHistoryPollVoteSnapshots(allEntries)
                }
                val checklistStateSnapshots = remember(allEntries) {
                    buildHistoryChecklistStateSnapshots(allEntries)
                }
                val flatRows = remember(displaySections) {
                    buildList {
                        for (section in displaySections) {
                            add(HistoryListRow.DayHeader(section.date))
                            for (e in section.items) {
                                if (
                                    MessageHistoryType.fromCode(e.type) == MessageHistoryType.TEXT &&
                                    isHistoryControlText(e.textBody.orEmpty())
                                ) {
                                    continue
                                }
                                add(HistoryListRow.EntryRow(e))
                            }
                        }
                    }
                }
                val searchTrimmed = historySearchQuery.trim()
                val showSearchEmpty = searchTrimmed.isNotEmpty() && flatRows.isEmpty()
                Column(Modifier.fillMaxSize()) {
                    if (historySearchOpen) {
                        OutlinedTextField(
                            value = historySearchQuery,
                            onValueChange = { historySearchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            placeholder = {
                                Text(
                                    stringResource(R.string.message_history_search_hint),
                                    color = Color(0xFF78909C),
                                )
                            },
                            trailingIcon = {
                                if (historySearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { historySearchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.message_history_clear_search),
                                            tint = Color(0xFF78909C),
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentNeon,
                                unfocusedBorderColor = Color(0xFF455A64),
                                cursorColor = AccentNeon,
                                focusedContainerColor = BubbleBg,
                                unfocusedContainerColor = BubbleBg,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    if (showSearchEmpty) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.message_history_search_no_results),
                                color = Color(0xFF78909C),
                                fontSize = 14.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(flatRows, key = { it.stableKey }) { row ->
                                when (row) {
                                    is HistoryListRow.DayHeader -> Text(
                                        text = row.date.format(dayFmt),
                                        color = Color(0xFF78909C),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                    )
                                    is HistoryListRow.EntryRow -> HistoryMessageRow(
                                        entry = row.entity,
                                        repository = app.messageHistoryRepository,
                                        timeFmt = timeFmt,
                                        historyNodes = historyNodes,
                                        meshNodeId = meshNodeId,
                                        pollVoteSnapshots = pollVoteSnapshots,
                                        checklistStateSnapshots = checklistStateSnapshots,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageHistoryCurrentPasswordDialog(
    onDismissRequest: () -> Unit,
    pwdField: String,
    pwdError: String?,
    onPwdChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    win.attributes = win.attributes.also { attrs ->
                        attrs.blurBehindRadius = 48
                    }
                }
                win.setDimAmount(0.55f)
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(20.dp),
            color = DialogBg,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = DialogAccent,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            "Текущий пароль",
                            color = DialogText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(DialogCard)
                            .clickable(remember { MutableInteractionSource() }, null) { onDismissRequest() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = DialogTextSec,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = pwdField,
                        onValueChange = onPwdChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.message_history_password), color = DialogTextSec) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DialogText,
                            unfocusedTextColor = DialogText,
                            focusedBorderColor = DialogAccent,
                            unfocusedBorderColor = DialogTextSec.copy(alpha = 0.5f),
                            cursorColor = DialogAccent,
                            focusedLabelColor = DialogAccent,
                            unfocusedLabelColor = DialogTextSec,
                        ),
                    )
                    pwdError?.let {
                        Text(
                            it,
                            color = DialogRed,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.action_cancel), color = DialogTextSec)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(android.R.string.ok), color = DialogAccent)
                    }
                }
            }
        }
    }
}

private sealed class HistoryListRow {
    abstract val stableKey: String
    data class DayHeader(val date: LocalDate) : HistoryListRow() {
        override val stableKey get() = "day_$date"
    }
    data class EntryRow(val entity: MessageHistoryEntryEntity) : HistoryListRow() {
        override val stableKey get() = "e_${entity.id}"
    }
}

@Composable
private fun MessageHistoryPasswordSetupDialog(
    onDismissRequest: () -> Unit,
    pwdField: String,
    pwdSetupConfirm: String,
    pwdError: String?,
    onPwdChange: (String) -> Unit,
    onPwdConfirmChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    win.attributes = win.attributes.also { attrs ->
                        attrs.blurBehindRadius = 48
                    }
                }
                win.setDimAmount(0.55f)
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(20.dp),
            color = DialogBg,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = DialogAccent,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            stringResource(R.string.message_history_setup_title),
                            color = DialogText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(DialogCard)
                            .clickable(remember { MutableInteractionSource() }, null) {
                                onDismissRequest()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = DialogTextSec,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = pwdField,
                        onValueChange = onPwdChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.message_history_password), color = DialogTextSec)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DialogText,
                            unfocusedTextColor = DialogText,
                            focusedBorderColor = DialogAccent,
                            unfocusedBorderColor = DialogTextSec.copy(alpha = 0.5f),
                            cursorColor = DialogAccent,
                            focusedLabelColor = DialogAccent,
                            unfocusedLabelColor = DialogTextSec,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pwdSetupConfirm,
                        onValueChange = onPwdConfirmChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.message_history_password_repeat), color = DialogTextSec)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DialogText,
                            unfocusedTextColor = DialogText,
                            focusedBorderColor = DialogAccent,
                            unfocusedBorderColor = DialogTextSec.copy(alpha = 0.5f),
                            cursorColor = DialogAccent,
                            focusedLabelColor = DialogAccent,
                            unfocusedLabelColor = DialogTextSec,
                        ),
                    )
                    pwdError?.let {
                        Text(
                            it,
                            color = DialogRed,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.action_cancel), color = DialogTextSec)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(android.R.string.ok), color = DialogAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageHistoryLoginDialog(
    meshNodeId: String,
    onDismissRequest: () -> Unit,
    pwdField: String,
    pwdError: String?,
    onPwdChange: (String) -> Unit,
    onResetPassword: (() -> Unit)? = null,
    onUnlock: () -> Unit,
) {
    val nodeDisplay = remember(meshNodeId) {
        val n = NodeIdHex.normalize(meshNodeId)
        if (n.isNotEmpty()) "!$n" else meshNodeId.ifBlank { "—" }
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    win.attributes = win.attributes.also { attrs ->
                        attrs.blurBehindRadius = 48
                    }
                }
                win.setDimAmount(0.55f)
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(20.dp),
            color = DialogBg,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = DialogAccent,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            stringResource(R.string.message_history_login_title),
                            color = DialogText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onResetPassword != null) {
                            TextButton(onClick = onResetPassword) {
                                Text("Сброс пароля", color = DialogRed, fontSize = 13.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(DialogCard)
                                .clickable(remember { MutableInteractionSource() }, null) {
                                    onDismissRequest()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = DialogTextSec,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = nodeDisplay,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = {
                            Text(stringResource(R.string.message_history_node_id), color = DialogTextSec)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DialogText,
                            unfocusedTextColor = DialogText,
                            focusedBorderColor = DialogTextSec.copy(alpha = 0.5f),
                            unfocusedBorderColor = DialogTextSec.copy(alpha = 0.5f),
                            disabledTextColor = DialogText,
                            disabledBorderColor = DialogTextSec.copy(alpha = 0.5f),
                            disabledLabelColor = DialogTextSec,
                        ),
                        enabled = false,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pwdField,
                        onValueChange = onPwdChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.message_history_password), color = DialogTextSec)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DialogText,
                            unfocusedTextColor = DialogText,
                            focusedBorderColor = DialogAccent,
                            unfocusedBorderColor = DialogTextSec.copy(alpha = 0.5f),
                            cursorColor = DialogAccent,
                            focusedLabelColor = DialogAccent,
                            unfocusedLabelColor = DialogTextSec,
                        ),
                    )
                    pwdError?.let {
                        Text(
                            it,
                            color = DialogRed,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.action_cancel), color = DialogTextSec)
                    }
                    TextButton(onClick = onUnlock) {
                        Text(stringResource(R.string.message_history_unlock), color = DialogAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMessageRow(
    entry: MessageHistoryEntryEntity,
    repository: com.example.aura.history.MessageHistoryRepository,
    timeFmt: java.time.format.DateTimeFormatter,
    historyNodes: List<MeshWireNodeSummary>,
    meshNodeId: String,
    pollVoteSnapshots: Map<String, HistoryPollVoteSnapshot>,
    checklistStateSnapshots: Map<String, Map<Int, HistoryChecklistItemState>>,
) {
    val t = remember(entry.createdAtEpochMs) {
        java.time.Instant.ofEpochMilli(entry.createdAtEpochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .format(timeFmt)
    }
    val senderShortBadge = remember(entry.fromNodeNum, historyNodes) {
        historySenderShortBadge(entry.fromNodeNum, historyNodes)
    }
    val nodeHexForColor = remember(entry.fromNodeNum, entry.isOutgoing, meshNodeId) {
        when {
            entry.fromNodeNum != 0L -> NodeIdHex.normalize(MeshWireNodeNum.formatHex((entry.fromNodeNum and 0xFFFF_FFFFL).toUInt()))
            entry.isOutgoing -> NodeIdHex.normalize(meshNodeId)
            else -> null
        }
    }
    val bubbleBg = historyBubbleColorForNode(nodeHexForColor)
    val bubbleText = historyBubbleTextColor(bubbleBg)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (entry.isOutgoing) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bubbleBg,
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Text(
                    text = senderShortBadge,
                    color = bubbleText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 2,
                )
            }
            Surface(
                shape = historyBubbleShape(entry.isOutgoing),
                color = bubbleBg,
                modifier = Modifier.widthIn(max = 360.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    when (MessageHistoryType.fromCode(entry.type)) {
                        MessageHistoryType.TEXT -> HistoryStructuredTextMessage(
                            rawText = entry.textBody.orEmpty(),
                            textColor = bubbleText,
                            pollVoteSnapshot = pollVoteSnapshots,
                            checklistStateSnapshot = checklistStateSnapshots,
                            historyNodes = historyNodes,
                            sourceEntry = entry,
                        )
                        MessageHistoryType.VOICE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.channel_preview_voice),
                                    color = bubbleText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                entry.voiceFileRelativePath?.let { rel ->
                                    VoiceRowPlayer(file = repository.absoluteFileForRelative(rel))
                                }
                            }
                        }
                        MessageHistoryType.COORDINATES -> Text(
                            "${entry.latitude}, ${entry.longitude}",
                            color = bubbleText,
                            fontSize = 14.sp,
                        )
                        MessageHistoryType.SERVICE -> Text(
                            "[${entry.serviceKind}] ${entry.servicePayloadJson ?: ""}",
                            color = bubbleText.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                        )
                        MessageHistoryType.IMAGE -> Text(
                            stringResource(R.string.channel_preview_image),
                            color = bubbleText,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        t,
                        color = bubbleText.copy(alpha = 0.62f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryStructuredTextMessage(
    rawText: String,
    textColor: Color,
    pollVoteSnapshot: Map<String, HistoryPollVoteSnapshot>,
    checklistStateSnapshot: Map<String, Map<Int, HistoryChecklistItemState>>,
    historyNodes: List<MeshWireNodeSummary>,
    sourceEntry: MessageHistoryEntryEntity,
) {
    val context = LocalContext.current
    val urgentPayload = remember(rawText) { parseHistoryUrgentMeshMessage(rawText) }
    val ackPayload = remember(rawText) { parseHistoryUrgentAckMeshMessage(rawText) }
    val compactLabel = remember(rawText) { compactHistoryStructuredLabel(rawText) }
    val firstLink = remember(rawText) { HistoryUrlRegex.find(rawText.trim())?.groupValues?.getOrNull(1) }
    val poll = remember(rawText) { parseHistoryPollMessage(rawText) }
    val checklist = remember(rawText) { parseHistoryChecklistMessage(rawText) }
    val beaconCoordinates = remember(rawText) {
        val trimmed = rawText.trim()
        BeaconShareLink.parseUriLenient(trimmed)?.let { formatHistoryCoordinates(it.lat, it.lon) }
            ?: BeaconShareLink.parseCoordinatesOnlyMessage(trimmed)?.let { (lat, lon) ->
                formatHistoryCoordinates(lat, lon)
            }
    }
    val resolvedPollId = remember(poll, sourceEntry.id) {
        poll?.pollId?.takeIf { it.isNotBlank() } ?: "legacy_poll_${sourceEntry.id}"
    }
    val resolvedChecklistId = remember(checklist, sourceEntry.id) {
        checklist?.listId?.takeIf { it.isNotBlank() } ?: "legacy_list_${sourceEntry.id}"
    }
    val pollSnapshot = remember(resolvedPollId, pollVoteSnapshot) { pollVoteSnapshot[resolvedPollId] }
    val checklistSnapshot = remember(resolvedChecklistId, checklistStateSnapshot) {
        checklistStateSnapshot[resolvedChecklistId].orEmpty()
    }
    val nodeShortByNum = remember(historyNodes) {
        historyNodes.associateBy(
            keySelector = { it.nodeNum and 0xFFFF_FFFFL },
            valueTransform = { node ->
                node.shortName?.trim().takeIf { !it.isNullOrBlank() && it != "?" }
                    ?: node.longName?.trim()?.takeIf { it.isNotBlank() }
                    ?: MeshWireNodeNum.formatHex((node.nodeNum and 0xFFFF_FFFFL).toUInt()).lowercase(Locale.ROOT)
            },
        )
    }

    when {
        poll != null -> {
            HistoryPollMessageContent(
                poll = poll,
                snapshot = pollSnapshot,
                textColor = textColor,
                mutedColor = textColor.copy(alpha = 0.72f),
                nodeShortByNum = nodeShortByNum,
            )
        }
        checklist != null -> {
            HistoryChecklistMessageContent(
                checklist = checklist,
                itemStates = checklistSnapshot,
                textColor = textColor,
                mutedColor = textColor.copy(alpha = 0.72f),
                ownerShortName = { owner -> owner?.let { nodeShortByNum[it] } },
            )
        }
        urgentPayload != null -> {
            Text(
                text = "Срочно",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (urgentPayload.body.isNotBlank()) {
                Text(
                    text = urgentPayload.body,
                    color = textColor,
                    fontSize = 14.sp,
                )
            }
        }
        ackPayload != null -> {
            Text(
                text = "Подтверждение",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "id: ${ackPayload.id}",
                color = textColor.copy(alpha = 0.9f),
                fontSize = 12.sp,
            )
        }
        compactLabel != null -> {
            Text(
                text = compactLabel,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        !beaconCoordinates.isNullOrBlank() -> {
            Text(
                text = beaconCoordinates,
                color = textColor,
                fontSize = 14.sp,
            )
        }
        !firstLink.isNullOrBlank() -> {
            Text(
                text = "Ссылка",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = firstLink,
                color = Color(0xFF64B5F6),
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(firstLink))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
        else -> {
            Text(
                text = rawText,
                color = textColor,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun HistoryChecklistMessageContent(
    checklist: HistoryChecklistPayload,
    itemStates: Map<Int, HistoryChecklistItemState>,
    textColor: Color,
    mutedColor: Color,
    ownerShortName: (Long?) -> String?,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = checklist.title,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(6.dp))
        checklist.items.forEachIndexed { idx, item ->
            val state = itemStates[idx]
            val checked = state?.checked == true
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (checked) "☑" else "☐",
                    color = if (checked) Color(0xFF7BD88F) else mutedColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = item,
                    color = textColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (checked) {
                    ownerShortName(state?.ownerNodeNum)?.let { who ->
                        Text(
                            text = who,
                            color = mutedColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Список", color = mutedColor, fontSize = 11.sp)
    }
}

@Composable
private fun HistoryPollMessageContent(
    poll: HistoryPollPayload,
    snapshot: HistoryPollVoteSnapshot?,
    textColor: Color,
    mutedColor: Color,
    nodeShortByNum: Map<Long, String>,
) {
    val totalVotes = snapshot?.totalVotes ?: 0
    val optionCounts = snapshot?.optionVoteCounts.orEmpty()
    val optionVoters = snapshot?.optionVoters.orEmpty()
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "📊 ${poll.question}",
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(6.dp))
        poll.options.forEachIndexed { idx, option ->
            val votesForOption = optionCounts[idx] ?: 0
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = textColor.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.14f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = option,
                        color = textColor,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = if (totalVotes > 0) "$votesForOption из $totalVotes" else "0 голосов",
                        color = mutedColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    if (!poll.anonymous && votesForOption > 0) {
                        val voters = optionVoters[idx].orEmpty()
                            .mapNotNull { nodeShortByNum[it] }
                            .distinct()
                        if (voters.isNotEmpty()) {
                            Text(
                                text = voters.joinToString(", "),
                                color = mutedColor,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (poll.anonymous) "Опрос (анонимный)" else "Опрос",
            color = mutedColor,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun VoiceRowPlayer(file: File) {
    var playing by remember(file.absolutePath) { mutableStateOf(false) }
    val player = remember(file.absolutePath) { MediaPlayer() }
    DisposableEffect(file.absolutePath) {
        onDispose {
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
    }
    IconButton(
        onClick = {
            runCatching {
                if (playing) {
                    player.pause()
                    player.seekTo(0)
                    playing = false
                } else {
                    player.reset()
                    player.setDataSource(file.absolutePath)
                    player.prepare()
                    player.setOnCompletionListener { playing = false }
                    player.start()
                    playing = true
                }
            }
        },
    ) {
        Icon(
            if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = AccentNeon,
        )
    }
}

private fun historySenderLabel(
    fromNodeNum: Long,
    historyNodes: List<MeshWireNodeSummary>,
): String {
    val nodeNumU = (fromNodeNum and 0xFFFF_FFFFL).toUInt()
    val hex = MeshWireNodeNum.formatHex(nodeNumU).lowercase(Locale.ROOT).removePrefix("!")
    val node = historyNodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == (fromNodeNum and 0xFFFF_FFFFL) }
    val longName = node?.longName?.trim().orEmpty()
    val name = when {
        longName.isNotBlank() && !longName.equals("!$hex", ignoreCase = true) && !longName.equals(hex, ignoreCase = true) ->
            longName
        else -> "Node"
    }
    return "$name ${hex.take(12)}"
}

private fun historySenderShortBadge(
    fromNodeNum: Long,
    historyNodes: List<MeshWireNodeSummary>,
): String {
    val node = historyNodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == (fromNodeNum and 0xFFFF_FFFFL) }
    val shortName = node?.shortName?.trim().orEmpty()
    if (shortName.isNotBlank() && shortName != "?") return shortName
    val longName = node?.longName?.trim().orEmpty()
    if (longName.isNotBlank()) return longName.take(24)
    val nodeNumU = (fromNodeNum and 0xFFFF_FFFFL).toUInt()
    return MeshWireNodeNum.formatHex(nodeNumU).lowercase(Locale.ROOT).removePrefix("!").take(12)
}
