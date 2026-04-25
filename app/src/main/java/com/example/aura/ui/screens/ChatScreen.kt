package com.example.aura.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aura.R
import com.example.aura.preferences.ChatConversationPreferences
import com.example.aura.preferences.ChatChannelNotificationPreferences
import com.example.aura.preferences.ChatChannelOrderPreferences
import com.example.aura.preferences.ChatPollVotePreferences
import com.example.aura.ui.vip.VipAvatarFrame
import com.example.aura.ui.vip.VipExpiredOverlay
import com.example.aura.ui.vip.VipRestrictedToast
import com.example.aura.ui.vip.rememberLocalVipActive
import com.example.aura.ui.vip.rememberVipRestricted
import com.example.aura.preferences.ChatWallpaperPreferences
import com.example.aura.chat.IncomingMessagePreviewFormatter
import com.example.aura.chat.history.ChatHistoryFileStore
import com.example.aura.ui.history.MessageHistoryScreen
import com.example.aura.ui.components.LocalProfileAvatarCircle
import com.example.aura.ui.components.MessageInputPanel
import com.example.aura.preferences.ChannelPinnedMessageStore
import com.example.aura.preferences.ChannelPinnedSnapshot
import com.example.aura.ui.components.MessageReplyDraft
import com.example.aura.ui.components.VoiceMicEvent
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.components.GattConnectionSyncProgressSection
import com.example.aura.ui.components.MeshBluetoothScanDialog
import com.example.aura.ui.components.MeshConnectionInitialTab
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as MstSett
import com.example.aura.AuraApplication
import com.example.aura.bluetooth.ChannelChatManager
import com.example.aura.bluetooth.DirectChatManager
import com.example.aura.bluetooth.MeshTextChatSession
import com.example.aura.ui.chat.DirectMessagesSection
import com.example.aura.ui.chat.mergeDmThreadSummariesWithFileLastMedia
import com.example.aura.ui.chat.displayTitleForDmPeer
import com.example.aura.ui.chat.meshtasticPeerSummaryForDm
import com.example.aura.ui.nodes.NodeQrScannerOverlay
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.mesh.repository.MeshIncomingChatRepository
import com.example.aura.ui.map.BeaconSyncEvent
import com.example.aura.ui.map.MapBeaconSyncRepository
import com.example.aura.ui.map.isLocalBeaconChannelSelection
import com.example.aura.mesh.qr.AuraQr
import com.example.aura.mesh.qr.NodeIdentityQr
import com.example.aura.sync.ProfileExportInAppResult
import com.example.aura.sync.ProfileExportQrInAppHandler
import com.example.aura.sync.SiteSyncClient
import kotlin.concurrent.thread
import com.example.aura.mesh.nodedb.MeshNodeAvatarByNodeId
import com.example.aura.mesh.nodedb.MeshNodeDbRepository
import com.example.aura.mesh.nodedb.NodesTabViewModel
import com.example.aura.progression.AuraExperience
import com.example.aura.progression.AuraProgressCounters
import com.example.aura.bluetooth.MeshNodeRemoteActions
import com.example.aura.bluetooth.MeshImageChunkCodec
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.fetchMeshWireChannels
import com.example.aura.bluetooth.fetchMeshWireDeviceConfig
import com.example.aura.bluetooth.fetchMeshWireExternalNotificationConfig
import com.example.aura.bluetooth.fetchMeshWireMqttConfig
import com.example.aura.bluetooth.fetchMeshWireLoRaConfig
import com.example.aura.bluetooth.fetchMeshWireSecurityConfig
import com.example.aura.bluetooth.fetchMeshWireTelemetryConfig
import com.example.aura.bluetooth.fetchMeshWireUserProfile
import com.example.aura.bluetooth.isMeshNodeBluetoothLinked
import com.example.aura.bluetooth.isMeshNodeSessionAppReady
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.bluetooth.MeshLocationPreferences
import com.example.aura.bluetooth.MeshPhoneLocationToMeshSender
import com.example.aura.meshwire.MeshReactionEmojiRegistry
import com.example.aura.meshwire.MeshStoredChannel
import com.example.aura.meshwire.meshChannelDisplayTitle
import com.example.aura.meshwire.MeshWireChannelsSyncResult
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.meshwire.MeshWireDevicePushState
import com.example.aura.meshwire.MeshWireExternalNotificationPushState
import com.example.aura.meshwire.MeshWireMqttPushState
import com.example.aura.meshwire.MeshWireLoRaPushState
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireModemPreset
import com.example.aura.meshwire.MeshWireReadReceiptCodec
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.MeshWireNodeUserProfile
import com.example.aura.meshwire.MeshWireSecurityPushState
import com.example.aura.meshwire.MeshWireTelemetryPushState
import com.example.aura.voice.VoiceMessageManager
import com.example.aura.voice.VoicePlayback
import com.example.aura.notifications.MeshNotificationDispatcher
import com.example.aura.chat.ChannelImageAttachment
import com.example.aura.chat.ChannelVoiceAttachment
import com.example.aura.chat.ChatMessageDeliveryStatus
import com.example.aura.data.local.ChannelChatMessageEntity
import com.example.aura.data.local.DmThreadSummaryRow
import com.example.aura.data.local.ChatMessageReactionsJson
import com.example.aura.data.local.GroupedReactionUi
import com.example.aura.preferences.MatrixRainPreferences
import com.example.aura.ui.map.BeaconShareLink
import com.example.aura.ui.map.BeaconSharePayload
import com.example.aura.ui.map.MapBeaconViewModel
import com.example.aura.ui.map.NetworkMapTabContent
import com.example.aura.ui.map.MapBeaconActiveChannelStore
import com.example.aura.ui.map.mapChannelIdForIndex
import com.example.aura.ui.matrix.MatrixRainLayer
import com.example.aura.ui.matrix.matrixPanelBackground
import com.example.aura.util.NodeIdHex
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val MtChatBar = Color(0xFF0A100A)
private val MtChatSheet = Color(0xFF2D3E33)
private val MtLockGreen = Color(0xFF4AF263)
private val MtLockYellow = Color(0xFFF2C94C)
private val MtLockRed = Color(0xFFEB5757)
/** Спец-цвет исходящих только для node id !2acbcf40 (normalized: без '!'). */
private const val PinnedBlueNodeIdHex = "2ACBCF40"
private val MtBubblePinnedBlue = Color(0xFF6C94EC)
private val MtBubbleBlueRemote = Color(0xFF1A3D78)
private val MtBubbleBrownMine = Color(0xFF7A4520)
private val ChannelBubbleCornerRadius = 12.dp

/** Исходящие: срез снизу справа; входящие: срез снизу слева (хвост как в Telegram). */
private fun channelBubbleShape(mine: Boolean): RoundedCornerShape {
    val r = ChannelBubbleCornerRadius
    return if (mine) {
        RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = 0.dp, bottomStart = r)
    } else {
        RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = r, bottomStart = 0.dp)
    }
}

private val NodeBubblePalette = listOf(
    // Зеленые
    Color(0xFF2F7D44),
    Color(0xFF3A8F55),
    // Серые
    Color(0xFF5A6473),
    Color(0xFF6A7280),
    // Синие (но не pinned-blue)
    Color(0xFF2E6FD6),
    Color(0xFF3F7FE0),
    // Дополнительные зелено-синие/серо-синие
    Color(0xFF3A7E86),
    Color(0xFF4B6B89),
)

private fun normalizeNodeIdHexForBubbleColor(raw: String?): String? =
    raw?.let { NodeIdHex.normalize(it) }?.takeIf { it.isNotBlank() }

private fun nodeIdHexFromNodeNum(nodeNum: UInt?): String? =
    nodeNum
        ?.toLong()
        ?.and(0xFFFF_FFFFL)
        ?.toString(16)
        ?.uppercase(Locale.US)
        ?.padStart(8, '0')

private fun stableNodeColorIndex(nodeIdHex: String): Int {
    var h = 2166136261L
    for (ch in nodeIdHex) {
        h = (h xor ch.code.toLong()) * 16777619L
        h = h and 0xFFFF_FFFFL
    }
    return (h % NodeBubblePalette.size.toLong()).toInt()
}

private fun bubbleBaseColorForNode(nodeIdHex: String?): Color {
    val normalized = normalizeNodeIdHexForBubbleColor(nodeIdHex) ?: return Color(0xFF5A6473)
    if (normalized == PinnedBlueNodeIdHex) return MtBubblePinnedBlue
    return NodeBubblePalette[stableNodeColorIndex(normalized)]
}

private fun bubbleBodyColorForBackground(bg: Color): Color =
    if (bg.luminance() < 0.42f) Color.White else Color(0xFF12263A)

private data class ChannelBubbleTheme(
    val bg: Color,
    val body: Color,
    val timeMuted: Color,
    val mqttTint: Color,
    val iconOnBubble: Color,
)

private fun channelBubbleTheme(senderNodeIdHex: String?): ChannelBubbleTheme {
    val bg = bubbleBaseColorForNode(senderNodeIdHex)
    val body = bubbleBodyColorForBackground(bg)
    return ChannelBubbleTheme(
        bg = bg,
        body = body,
        timeMuted = body.copy(alpha = 0.58f),
        mqttTint = body.copy(alpha = 0.74f),
        iconOnBubble = body,
    )
}

/** Полоска цитаты в пузыре (как в Telegram). */
private val TelegramReplyAccentIncomingBubble = Color(0xFF3390EC)
private val TelegramReplyAccentOutgoingBubble = Color(0xFF99C8FF)

private val MtActionGreen = Color(0xFF5CE07A)
private val MtDividerLight = Color.White.copy(alpha = 0.14f)

/** Выпадающее меню «⋯» в переписке: пузырь с мягким скруглением. */
private val ConversationOverflowMenuShape = RoundedCornerShape(
    topStart = 26.dp,
    topEnd = 14.dp,
    bottomEnd = 28.dp,
    bottomStart = 28.dp,
)
private val ConversationOverflowMenuFill = Color(0xFF3D3D3D)
private val ConversationOverflowMenuBorder = Color.White.copy(alpha = 0.14f)
private val ConversationOverflowMenuInnerSurface = Color(0xFF4A4A4A)
private val ConversationOverflowMenuAccent = Color(0xFF00E676)

/** Ползунки в меню «⋯»: ~на 20% меньше стандартного трека и бегунка 22dp. */
private val ConversationOverflowMenuSliderThumbDp = 18.dp
private val ConversationOverflowMenuSliderTrackHeightDp = 3.dp
private val ConversationOverflowMenuSliderPercentTextSp = 10.sp

/** Пузыри шапки переписки (как у поля ввода). */
private val ChatConversationHeaderBubbleBg = Color(0xFF1E1E22)
private val ChatConversationHeaderBubbleStroke = Color.White.copy(alpha = 0.1f)
private val ChatConversationHeaderTitleBubbleShape = RoundedCornerShape(24.dp)
private val ChatConversationHeaderMenuBubbleShape = RoundedCornerShape(22.dp)
private const val ProfileNodeBubbleScale = 0.8f

/** Доля заполнения кольцевой «шкалы опыта» в логотипе шапки (между двумя кольцами обода). */
/** 0% — базовый размер текста в пузырях; 100% — +100% к размеру (удвоение). */
private fun channelBubbleFontScaleMultiplier(percent: Float): Float =
    1f + (percent.coerceIn(0f, 100f) / 100f)

private fun clampChannelChatDraftUtf8(s: String): String =
    MeshWireLoRaToRadioEncoder.truncateMeshUtf8(
        s,
        MeshWireLoRaToRadioEncoder.MESH_TEXT_PAYLOAD_MAX_UTF8_BYTES,
    )

private fun channelChatDraftUtf8ByteLen(s: String): Int = s.encodeToByteArray().size

private enum class MeshChannelSecurityUi {
    Secure,
    InsecureImprecise,
    InsecurePrecise,
    InsecurePreciseMqtt,
}

/** AES — только 16/32 байта PSK; иначе «открытый» канал. */
private fun meshStoredChannelSecurityUi(ch: MeshStoredChannel): MeshChannelSecurityUi {
    val strongPsk = ch.psk.size == 16 || ch.psk.size == 32
    val precise = ch.positionPrecision > 0U
    val mqttUp = ch.uplinkEnabled
    if (strongPsk) return MeshChannelSecurityUi.Secure
    if (precise && mqttUp) return MeshChannelSecurityUi.InsecurePreciseMqtt
    if (precise) return MeshChannelSecurityUi.InsecurePrecise
    return MeshChannelSecurityUi.InsecureImprecise
}

private fun securityLockIcon(ui: MeshChannelSecurityUi) = when (ui) {
    MeshChannelSecurityUi.Secure -> Icons.Default.Lock
    else -> Icons.Outlined.LockOpen
}

private fun securityLockTint(ui: MeshChannelSecurityUi) = when (ui) {
    MeshChannelSecurityUi.Secure -> MtLockGreen
    MeshChannelSecurityUi.InsecureImprecise -> MtLockYellow
    MeshChannelSecurityUi.InsecurePrecise,
    MeshChannelSecurityUi.InsecurePreciseMqtt,
    -> MtLockRed
}

private fun channelSecurityStatusText(ui: MeshChannelSecurityUi): String = when (ui) {
    MeshChannelSecurityUi.Secure ->
        "Зелёный замок означает, что канал надёжно зашифрован либо 128-, либо 256-битным ключом AES."
    MeshChannelSecurityUi.InsecureImprecise ->
        "Жёлтый открытый замок означает, что канал небезопасно зашифрован, не используется для " +
            "точного определения местоположения и не использует ни один ключ вообще, ни один из известных байтовых ключей."
    MeshChannelSecurityUi.InsecurePrecise ->
        "Красный открытый замок означает, что канал не зашифрован, используется для точного определения " +
            "местоположения и не использует ни один ключ вообще, ни один из известных байтовых ключей."
    MeshChannelSecurityUi.InsecurePreciseMqtt ->
        "Красный открытый замок с предупреждением означает, что канал не зашифрован, используется для " +
            "получения точных данных о местоположении, которые передаются через Интернет по MQTT, " +
            "и не использует ни один ключ вообще, ни один байтовый известный ключ."
}

private sealed class PendingSettingsDestination {
    data object LoRa : PendingSettingsDestination()
    data object User : PendingSettingsDestination()
    data object Channels : PendingSettingsDestination()
    data object Security : PendingSettingsDestination()
    data object Device : PendingSettingsDestination()
    data object Mqtt : PendingSettingsDestination()
    data object ExternalNotifications : PendingSettingsDestination()
    data object Telemetry : PendingSettingsDestination()
    data object Firmware : PendingSettingsDestination()
}

private sealed class NodeSyncOverlayState {
    data class Running(val title: String, val percent: Int) : NodeSyncOverlayState()
    data class Failed(val message: String) : NodeSyncOverlayState()
}

@Composable
private fun SettingsNodeSyncOverlay(
    state: NodeSyncOverlayState,
    onDismissError: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MstSett.card),
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state) {
                    is NodeSyncOverlayState.Running -> {
                        Text(
                            state.title,
                            color = MstSett.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                        val frac = state.percent.coerceIn(0, 100) / 100f
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth(),
                            color = MstSett.accent,
                            trackColor = MstSett.dividerInCard,
                        )
                        Text(
                            "${state.percent.coerceIn(0, 100)} %",
                            color = MstSett.muted,
                            fontSize = 13.sp,
                        )
                    }
                    is NodeSyncOverlayState.Failed -> {
                        Text(
                            "Ошибка",
                            color = MstSett.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                        )
                        Text(
                            state.message,
                            color = Color(0xFFFF8A80),
                            fontSize = 14.sp,
                        )
                        TextButton(onClick = onDismissError) {
                            Text("Закрыть", color = MstSett.accent)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationOverflowMenuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    interactionSource: MutableInteractionSource,
    colors: SliderColors,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        interactionSource = interactionSource,
        colors = colors,
        track = { sliderState: SliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(ConversationOverflowMenuSliderTrackHeightDp),
                colors = colors,
            )
        },
        thumb = @Composable {
            Spacer(
                Modifier
                    .size(ConversationOverflowMenuSliderThumbDp)
                    .hoverable(interactionSource = interactionSource)
                    .background(ConversationOverflowMenuAccent, CircleShape),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    nodeId: String = "",
    /** MAC ноды для проверки реального BLE (GATT). */
    deviceAddress: String? = null,
    /** Локальный аватар профиля (тот же файл, что выбирается на экране профиля). */
    profileAvatarPath: String? = null,
    /** Обновить путь после выбора фото (SharedPreferences + файл в [ProfileLocalAvatarStore]). */
    onProfileAvatarPathChange: (String?) -> Unit = {},
    onNodeIdChange: (String) -> Unit = {},
    onDeviceAddressChange: (String?) -> Unit = {},
    /** После выбора ноды в диалоге поиска — обновить сессию и сохранённые данные. */
    onDeviceLinked: (nodeId: String, meshAddress: String) -> Unit = { _, _ -> },
    /** Переход на экран авторизации (другая нода, полный сброс сессии). */
    onNavigateToPassword: () -> Unit = {},
    /**
     * После «Отключиться» в диалоге «Соединения» ([MeshService.stopForUserBleDisconnect] уже вызван в диалоге).
     * Используйте для сброса MAC в сессии; переход на экран пароля сюда не подключается.
     */
    onBleUnbind: (() -> Unit)? = null,
    /** Экран профиля / узла */
    onOpenProfile: () -> Unit = {},
    /** Показать инструкцию первого запуска (из настроек). */
    onOpenFirstLaunchInstruction: () -> Unit = {},
    /** Увеличивается при [android.app.Activity.onNewIntent] для обработки открытия из уведомления. */
    notificationIntentKey: Int = 0,
    /** Стартовая вкладка нижней панели (0 сообщения, 1 ноды, 2 карта, 3 настройки). */
    initialBottomTabIndex: Int = 0,
    /** Увеличивается при «Прямое сообщение» с экрана профиля (MainActivity); вместе с [profileDirectDmTarget]. */
    profileDirectDmRequestId: Int = 0,
    profileDirectDmTarget: MeshWireNodeSummary? = null,
    onConsumedProfileDirectDmRequest: () -> Unit = {},
    /** [android.app.Activity.onCreate] / [onNewIntent]: открытие `aura://profile?...` внешним сканером. */
    profileExportDeepLinkKey: Int = 0,
    profileExportDeepLinkText: String? = null,
    onConsumedProfileExportDeepLink: () -> Unit = {},
) {
    val context = LocalContext.current
    val localNodeNumForRadio = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }
    var bleConnected by remember(deviceAddress) { mutableStateOf(false) }
    var wasBleConnected by remember { mutableStateOf(false) }
    LaunchedEffect(deviceAddress) {
        bleConnected = isMeshNodeBluetoothLinked(context, deviceAddress)
        while (isActive) {
            delay(1200)
            bleConnected = isMeshNodeBluetoothLinked(context, deviceAddress)
        }
    }

    LaunchedEffect(bleConnected, nodeId) {
        if (bleConnected && !wasBleConnected) {
            val id = ChatHistoryFileStore.normalizeNodeIdHex(nodeId)
            if (id.isNotEmpty()) {
                ChatHistoryFileStore.ensureArchiveLayout(context.applicationContext, id)
            }
        }
        wasBleConnected = bleConnected
    }

    var previousDeviceAddress by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deviceAddress) {
        val cur = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
        val prev = previousDeviceAddress?.trim()?.takeIf { it.isNotEmpty() }
        if (prev != null) {
            if (cur == null ||
                MeshNodeSyncMemoryStore.normalizeKey(prev) != MeshNodeSyncMemoryStore.normalizeKey(cur)
            ) {
                MeshNodeSyncMemoryStore.removeDevice(prev)
            }
        }
        previousDeviceAddress = deviceAddress
    }

    LaunchedEffect(deviceAddress) {
        val a = deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        MeshNodeSyncMemoryStore.warmFromDisk(a)
    }

    var selectedTab by remember(initialBottomTabIndex) { mutableIntStateOf(initialBottomTabIndex) }
    /** Если VIP-таймер истёк, показываем полноэкранный оверлей и блокируем доп. возможности. */
    val vipRestricted = rememberVipRestricted()
    /** Оверлей закрыт (нажата «Отмена»), но VIP всё ещё истёк: приложение в ограниченном режиме. */
    var vipOverlayDismissed by remember { mutableStateOf(false) }
    /** Оверлей активации VIP-тарифа (пункт «Активировать VIP тариф» в настройках). */
    var vipActivateOverlayOpen by remember { mutableStateOf(false) }
    LaunchedEffect(vipRestricted) {
        if (!vipRestricted) vipOverlayDismissed = false
    }
    val activityForIntent = LocalContext.current as? androidx.activity.ComponentActivity
    LaunchedEffect(notificationIntentKey, activityForIntent) {
        val act = activityForIntent ?: return@LaunchedEffect
        val n = act.intent?.getLongExtra(MeshNotificationDispatcher.EXTRA_FROM_NODE_NUM, -1L) ?: -1L
        if (n >= 0) {
            selectedTab = 0
            act.intent?.removeExtra(MeshNotificationDispatcher.EXTRA_FROM_NODE_NUM)
        }
    }
    /** Диалог «Соединения» — как на экране авторизации. */
    var showConnectionsDialog by remember { mutableStateOf(false) }
    var meshConnDialogInitialTab by remember { mutableStateOf(MeshConnectionInitialTab.Bluetooth) }
    var clearNodeListDialogOpen by remember { mutableStateOf(false) }
    var preserveFavoritesOnClear by remember { mutableStateOf(false) }
    var clearNodeListSending by remember { mutableStateOf(false) }
    var nodeListClearSignal by remember { mutableIntStateOf(0) }
    var loraSettingsOpen by remember { mutableStateOf(false) }
    var userSettingsOpen by remember { mutableStateOf(false) }
    var channelsSettingsOpen by remember { mutableStateOf(false) }
    var securitySettingsOpen by remember { mutableStateOf(false) }
    var deviceSettingsOpen by remember { mutableStateOf(false) }
    var mqttSettingsOpen by remember { mutableStateOf(false) }
    var notificationsSettingsOpen by remember { mutableStateOf(false) }
    var externalNotificationsSettingsOpen by remember { mutableStateOf(false) }
    var telemetrySettingsOpen by remember { mutableStateOf(false) }
    var firmwareSettingsOpen by remember { mutableStateOf(false) }
    /** Полноэкранная история сообщений (Room) из настроек. */
    var messageHistoryOpen by remember { mutableStateOf(false) }
    var nodeSyncOverlay by remember { mutableStateOf<NodeSyncOverlayState?>(null) }
    /** Ожидание полного GATT («Подключено») перед входом в подпункты настроек. */
    var settingsGattWaitOverlay by remember { mutableStateOf(false) }
    var pendingSettingsDestination by remember { mutableStateOf<PendingSettingsDestination?>(null) }

    var loraBootstrap by remember { mutableStateOf<MeshWireLoRaPushState?>(null) }
    /** Пресет LoRa для подписи каналов (как во вкладке «Каналы»). */
    var channelUiModemPreset by remember { mutableStateOf<MeshWireModemPreset?>(null) }
    var channelsBootstrap by remember { mutableStateOf<MeshWireChannelsSyncResult?>(null) }
    var securityBootstrap by remember { mutableStateOf<MeshWireSecurityPushState?>(null) }
    var userBootstrap by remember { mutableStateOf<MeshWireNodeUserProfile?>(null) }
    var deviceBootstrap by remember { mutableStateOf<MeshWireDevicePushState?>(null) }
    var mqttBootstrap by remember { mutableStateOf<MeshWireMqttPushState?>(null) }
    var externalNotificationsBootstrap by remember { mutableStateOf<MeshWireExternalNotificationPushState?>(null) }
    var telemetryBootstrap by remember { mutableStateOf<MeshWireTelemetryPushState?>(null) }
    /** Отправка [Position] с телефона на ноду (предоставление местоположения сети). */
    var provideLocationToMesh by remember {
        mutableStateOf(MeshLocationPreferences.isProvideLocationToMesh(context.applicationContext))
    }
    var hideCoordinatesTransmission by remember {
        mutableStateOf(MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContextForLocationPrefs = context.applicationContext
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hideCoordinatesTransmission = MeshLocationPreferences.isHideCoordinatesTransmission(appContextForLocationPrefs)
            provideLocationToMesh = MeshLocationPreferences.isProvideLocationToMesh(appContextForLocationPrefs)
        }
    }
    /**
     * Периодическая отправка позиции телефона в эфир (primary channel), интервал по умолчанию ~15 мин.
     */
    LaunchedEffect(provideLocationToMesh, hideCoordinatesTransmission, deviceAddress, nodeId) {
        if (!provideLocationToMesh || hideCoordinatesTransmission) return@LaunchedEffect
        val a = deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val num = MeshWireNodeNum.parseToUInt(nodeId) ?: return@LaunchedEffect
        val app = context.applicationContext
        while (isActive) {
            if (!MeshLocationPreferences.isProvideLocationToMesh(app)) break
            if (MeshLocationPreferences.isHideCoordinatesTransmission(app)) break
            MeshPhoneLocationToMeshSender.sendLastKnownLocationIfPossible(app, a, num) { _, _ -> }
            delay(15 * 60 * 1000L)
        }
    }
    var activeConversationChannel by remember { mutableStateOf<MeshStoredChannel?>(null) }
    /** Личная переписка с узлом; взаимоисключимо с открытым каналом [activeConversationChannel]. */
    var activeDirectMessagePeer by remember { mutableStateOf<MeshWireNodeSummary?>(null) }
    var selectedMapChannelIndex by remember { mutableStateOf<Int?>(0) }
    var pendingConversationDraft by remember { mutableStateOf<String?>(null) }
    /** Режим «Маяки» на вкладке «Карта»: не сбрасывается при переключении на чат/ноды — иначе mesh-метки не видны до ручного переключения. */
    var mapBeaconsOnlyMode by rememberSaveable { mutableStateOf(false) }
    /** Импорт метки по ссылке из чата: открыть вкладку «Карта» и диалог подтверждения. */
    var pendingMapBeaconShare by remember { mutableStateOf<BeaconSharePayload?>(null) }
    /** LongName отправителя сообщения со ссылкой (поле «Название метки» при импорте «Моя позиция» / «Моя метка»). */
    var pendingBeaconShareSenderLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        MapBeaconSyncRepository.events.collect { ev ->
            when (ev) {
                is BeaconSyncEvent.Add -> {
                    val sel = MapBeaconActiveChannelStore.selection.value
                    if (!isLocalBeaconChannelSelection(sel) && ev.beacon.channelIndex == sel.channelIndex) {
                        mapBeaconsOnlyMode = true
                    }
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(profileDirectDmRequestId, profileDirectDmTarget) {
        if (profileDirectDmRequestId == 0) return@LaunchedEffect
        val node = profileDirectDmTarget ?: return@LaunchedEffect
        selectedTab = 0
        val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
        val channels = addr?.let { MeshNodeSyncMemoryStore.getChannels(it)?.channels }.orEmpty()
        val primary = channels.firstOrNull { it.index == 0 } ?: channels.firstOrNull()
        if (primary != null) {
            activeConversationChannel = null
            pendingConversationDraft = null
            activeDirectMessagePeer = node
        } else {
            Toast.makeText(context, "Каналы ещё не загружены", Toast.LENGTH_SHORT).show()
        }
        onConsumedProfileDirectDmRequest()
    }
    var clearHistoryChannel by remember { mutableStateOf<MeshStoredChannel?>(null) }
    var historyClearBumpForConversation by remember { mutableIntStateOf(0) }
    var channelListPreviewRefresh by remember { mutableIntStateOf(0) }
    val clearHistoryScope = rememberCoroutineScope()
    val chatDaoForClear = remember {
        (context.applicationContext as AuraApplication).chatDatabase.channelChatMessageDao()
    }
    fun clearAllLocalChannelChatsAfterReorderConfirm() {
        val mac = deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val macNorm = MeshNodeSyncMemoryStore.normalizeKey(mac)
        val idHex = ChatHistoryFileStore.normalizeNodeIdHex(nodeId)
        val appCtx = context.applicationContext
        clearHistoryScope.launch(Dispatchers.IO) {
            if (idHex.isNotEmpty()) {
                ChatHistoryFileStore.ensureArchiveLayout(appCtx, idHex)
            }
            for (idx in 0 until MeshStoredChannel.MAX_CHANNELS) {
                val rows = chatDaoForClear.getAllForChannelAsc(macNorm, idx)
                ChatHistoryFileStore.syncMissingTextMessagesToArchive(
                    appCtx,
                    macNorm,
                    idx,
                    rows,
                    idHex.ifEmpty { null },
                )
                ChatHistoryFileStore.purgeAllChannelMediaForHistoryClear(
                    appCtx,
                    macNorm,
                    idx,
                    idHex.ifEmpty { null },
                )
                chatDaoForClear.deleteAllForChannel(macNorm, idx)
            }
            ChannelPinnedMessageStore.clearAllForDeviceMac(appCtx, mac)
            withContext(Dispatchers.Main) {
                channelListPreviewRefresh++
                historyClearBumpForConversation++
                MeshNotificationDispatcher.clearMessageNotifications(appCtx)
            }
        }
    }
    var bubbleMenuSession by remember { mutableStateOf<BubbleMenuSession?>(null) }
    var channelSecurityStatusFor by remember { mutableStateOf<MeshStoredChannel?>(null) }
    var channelSecurityLegendFor by remember { mutableStateOf<MeshStoredChannel?>(null) }
    var conversationMenuExpanded by remember { mutableStateOf(false) }
    var channelMessageSearchActive by remember { mutableStateOf(false) }
    var channelMessageSearchQuery by remember { mutableStateOf("") }
    var channelConversationFontScalePercent by remember {
        mutableFloatStateOf(
            ChatConversationPreferences.getFontScalePercent(context.applicationContext),
        )
    }
    val conversationSearchFocusRequester = remember { FocusRequester() }
    var selectedMeshNode by remember { mutableStateOf<MeshWireNodeSummary?>(null) }
    /** Экран «Группы» поверх списка каналов (состояние на уровне экрана — чтобы вернуться из профиля узла). */
    var showGroupsScreen by remember { mutableStateOf(false) }
    /**
     * Если профиль узла открыт из папки группы — id группы для возврата по стрелке «назад».
     * Сбрасывается при уходе с вкладки «Узлы» ([LaunchedEffect] по [selectedTab]).
     */
    var nodesProfileReturnGroupId by remember { mutableStateOf<Long?>(null) }
    /**
     * Индекс вкладки (0 чаты, 2 карта, 3 настройки), куда вернуться при «назад» из карточки узла,
     * если профиль открыли не со списка «Узлы». `null` — открыли, уже находясь на вкладке узлов.
     */
    var nodesProfileReturnTabIndex by remember { mutableStateOf<Int?>(null) }
    val groupsNavVm: GroupsViewModel = viewModel()
    var showNodeQrScanner by remember { mutableStateOf(false) }
    val nodesTabVm: NodesTabViewModel = viewModel()
    val nodesTabUi by nodesTabVm.uiState.collectAsState()
    LaunchedEffect(profileExportDeepLinkKey, profileExportDeepLinkText) {
        if (profileExportDeepLinkKey <= 0) return@LaunchedEffect
        val raw = profileExportDeepLinkText?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val list = nodesTabUi.nodes
        onConsumedProfileExportDeepLink()
        when (val p = AuraQr.parse(raw)) {
            is AuraQr.ProfileExport -> {
                when (
                    val r = ProfileExportQrInAppHandler.handle(
                        context = context,
                        export = p,
                        nodes = list,
                    )
                ) {
                    is ProfileExportInAppResult.RestoredVip -> {
                        Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                    }
                    is ProfileExportInAppResult.OpenPeerProfile -> {
                        selectedTab = 1
                        selectedMeshNode = r.node
                        Toast.makeText(
                            context,
                            "Профиль: ${r.node.displayLongName()}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    is ProfileExportInAppResult.Error -> {
                        Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            else -> {
                Toast.makeText(
                    context,
                    "Ссылка не распознана как QR профиля AuRA",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    var chatWallpaperIndex by remember {
        mutableIntStateOf(ChatWallpaperPreferences.getWallpaperIndex(context.applicationContext))
    }
    var chatWallpaperDimPercent by remember {
        mutableFloatStateOf(
            ChatWallpaperPreferences.getWallpaperDimPercent(context.applicationContext).toFloat(),
        )
    }
    var showChatWallpaperPicker by remember { mutableStateOf(false) }

    val appCtxMatrix = context.applicationContext
    var matrixSettingsOpen by remember { mutableStateOf(false) }
    var matrixDensity by remember { mutableFloatStateOf(MatrixRainPreferences.densityMultiplier(appCtxMatrix)) }
    var matrixSpeed by remember { mutableFloatStateOf(MatrixRainPreferences.speedMultiplier(appCtxMatrix)) }
    var matrixDim by remember { mutableFloatStateOf(MatrixRainPreferences.dimOverlayAlpha(appCtxMatrix)) }
    val vipTimerActiveForMatrix = rememberLocalVipActive()
    /** Matrix (анимация и настройки) только пока действует VIP по таймеру. */
    val matrixFeatureUnlocked = vipTimerActiveForMatrix
    val matrixRainAnimationEnabled = matrixFeatureUnlocked
    LaunchedEffect(matrixFeatureUnlocked) {
        if (!matrixFeatureUnlocked) matrixSettingsOpen = false
    }
    val settingsRootListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val settingsSubscreenOpen =
        selectedTab == 3 &&
            (loraSettingsOpen || userSettingsOpen || channelsSettingsOpen || securitySettingsOpen ||
                deviceSettingsOpen || mqttSettingsOpen || notificationsSettingsOpen ||
                externalNotificationsSettingsOpen || telemetrySettingsOpen || firmwareSettingsOpen ||
                matrixSettingsOpen)

    val snackbarHostState = remember { SnackbarHostState() }
    val activeConversationUpdated by rememberUpdatedState(activeConversationChannel)
    val activeDirectDmPeerUpdated by rememberUpdatedState(activeDirectMessagePeer)
    val selectedTabUpdated by rememberUpdatedState(selectedTab)
    val channelUiModemPresetUpdated by rememberUpdatedState(channelUiModemPreset)
    val deviceAddressTrimmedUpdated by rememberUpdatedState(
        deviceAddress?.trim()?.takeIf { it.isNotEmpty() },
    )
    val snackbarAboveInput = selectedTab == 0 &&
        (activeConversationChannel != null || activeDirectMessagePeer != null)
    val snackbarBeaconPreviewLabel = stringResource(R.string.channel_preview_beacon)
    val snackbarPollVotePreviewLabel = stringResource(R.string.preview_poll_vote_recorded)
    val snackbarChecklistTogglePreviewLabel = stringResource(R.string.preview_checklist_choice_made)

    LaunchedEffect(Unit) {
        MeshIncomingChatRepository.incomingMessages.collect { evt ->
            val ent = evt.entity
            if (ent.isOutgoing) return@collect
            val rawAddr = deviceAddressTrimmedUpdated ?: return@collect
            val norm = MeshNodeSyncMemoryStore.normalizeKey(rawAddr)
            if (ent.deviceMac != norm) return@collect
            val dmPeer = ent.dmPeerNodeNum
            // Если пользователь уже на главном экране чатов (список каналов/личек), дублирующий snackbar не показываем.
            if (selectedTabUpdated == 0 && activeConversationUpdated == null && activeDirectDmPeerUpdated == null) {
                return@collect
            }
            if (dmPeer != null) {
                val peerLong = dmPeer and 0xFFFF_FFFFL
                if (ChatChannelNotificationPreferences.isDirectThreadMuted(context, rawAddr, peerLong)) return@collect
                val openPeer = activeDirectDmPeerUpdated?.nodeNum?.and(0xFFFF_FFFFL)
                if (selectedTabUpdated == 0 && openPeer != null && openPeer == peerLong) return@collect
            } else {
                if (ChatChannelNotificationPreferences.isChannelMuted(context, ent.deviceMac, ent.channelIndex)) {
                    return@collect
                }
                if (selectedTabUpdated == 0 && activeConversationUpdated?.index == ent.channelIndex) {
                    return@collect
                }
            }
            val normalizedPreview = normalizeChatPreviewText(
                ent.text,
                snackbarBeaconPreviewLabel,
                snackbarPollVotePreviewLabel,
                snackbarChecklistTogglePreviewLabel,
            )
                .replace('\n', ' ')
                .trim()
                .take(96)
            val preview = normalizedPreview
                .ifEmpty { context.getString(R.string.snackbar_new_message_fallback) }
            val label = if (dmPeer != null) {
                context.getString(R.string.snackbar_new_message_direct, preview)
            } else {
                val chTitle = MeshNodeSyncMemoryStore.getChannels(rawAddr)?.channels
                    ?.firstOrNull { it.index == ent.channelIndex }
                    ?.let { conversationChannelTitle(it, channelUiModemPresetUpdated) }
                    ?.takeIf { it.isNotBlank() }
                    ?: "канал ${ent.channelIndex}"
                context.getString(R.string.snackbar_new_message_channel, chTitle, preview)
            }
            when (
                snackbarHostState.showSnackbar(
                    message = label,
                    actionLabel = context.getString(R.string.snackbar_reply),
                    duration = SnackbarDuration.Long,
                    withDismissAction = true,
                )
            ) {
                SnackbarResult.ActionPerformed -> {
                    selectedTab = 0
                    if (dmPeer != null) {
                        val peerLong = dmPeer and 0xFFFF_FFFFL
                        val nodes = MeshNodeListDiskCache.load(context.applicationContext, rawAddr).orEmpty()
                        activeConversationChannel = null
                        pendingConversationDraft = null
                        activeDirectMessagePeer = meshtasticPeerSummaryForDm(peerLong, nodes)
                    } else {
                        val ch = MeshNodeSyncMemoryStore.getChannels(rawAddr)?.channels
                            ?.firstOrNull { it.index == ent.channelIndex }
                        if (ch != null) {
                            activeConversationChannel = ch.copyForEdit()
                        }
                    }
                }
                SnackbarResult.Dismissed -> Unit
            }
        }
    }

    LaunchedEffect(activeConversationChannel?.rowKey) {
        conversationMenuExpanded = false
        channelMessageSearchActive = false
        channelMessageSearchQuery = ""
        if (selectedTab == 0) {
            MeshNotificationDispatcher.clearMessageNotifications(context.applicationContext)
        }
    }

    LaunchedEffect(channelMessageSearchActive) {
        if (channelMessageSearchActive) {
            delay(100)
            conversationSearchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(activeConversationChannel, activeDirectMessagePeer) {
        if (activeConversationChannel == null && activeDirectMessagePeer == null) {
            bubbleMenuSession = null
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            MeshNotificationDispatcher.clearMessageNotifications(context.applicationContext)
        }
    }

    /** Закрыть все подстраницы настроек перед открытием новой. */
    fun closeAllSettingSubscreens() {
        firmwareSettingsOpen = false
        loraSettingsOpen = false
        userSettingsOpen = false
        channelsSettingsOpen = false
        securitySettingsOpen = false
        deviceSettingsOpen = false
        mqttSettingsOpen = false
        notificationsSettingsOpen = false
        externalNotificationsSettingsOpen = false
        telemetrySettingsOpen = false
        matrixSettingsOpen = false
        messageHistoryOpen = false
    }

    /** После записи конфига на ноду: вкладка «Настройки» и блок входа в подэкраны до стабильного BLE. */
    var settingsDetailLockedAfterNodeWrite by remember { mutableStateOf(false) }

    LaunchedEffect(bleConnected, settingsDetailLockedAfterNodeWrite) {
        if (!settingsDetailLockedAfterNodeWrite) return@LaunchedEffect
        if (!bleConnected) return@LaunchedEffect
        delay(700)
        if (bleConnected) {
            settingsDetailLockedAfterNodeWrite = false
        }
    }

    fun markNodeConfigWriteSuccess() {
        selectedTab = 3
        closeAllSettingSubscreens()
            nodeSyncOverlay = null
        settingsDetailLockedAfterNodeWrite = true
    }

    fun blockSettingsSubscreenOpen(): Boolean {
        if (!settingsDetailLockedAfterNodeWrite) return false
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.settings_detail_locked_until_node),
            Toast.LENGTH_SHORT,
        ).show()
        return true
    }

    fun deviceAddrOrFail(): String? {
        val a = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
        if (a == null) {
            nodeSyncOverlay = NodeSyncOverlayState.Failed("Привяжите устройство по Bluetooth.")
        }
        return a
    }

    LaunchedEffect(selectedTab) {
        // Вкладка «Карта»: не сбрасываем канал — после импорта метки по ссылке «Нет» возвращаем в тот же чат.
        if (selectedTab != 0 && selectedTab != 2) {
            activeConversationChannel = null
        }
        if (selectedTab != 1) {
            selectedMeshNode = null
            nodesProfileReturnGroupId = null
            nodesProfileReturnTabIndex = null
        }
        if (selectedTab != 3) {
            loraSettingsOpen = false
            userSettingsOpen = false
            channelsSettingsOpen = false
            securitySettingsOpen = false
            deviceSettingsOpen = false
            mqttSettingsOpen = false
            notificationsSettingsOpen = false
            externalNotificationsSettingsOpen = false
            telemetrySettingsOpen = false
            firmwareSettingsOpen = false
            matrixSettingsOpen = false
            messageHistoryOpen = false
        }
    }

    LaunchedEffect(deviceAddress) {
        if (deviceAddress.isNullOrBlank()) {
            activeConversationChannel = null
            selectedMeshNode = null
            settingsDetailLockedAfterNodeWrite = false
        }
    }

    /** Стрелка «назад» в шапке профиля узла и системный «назад» — возврат на предыдущую вкладку. */
    fun dismissNodeProfileFromNav() {
        val gid = nodesProfileReturnGroupId
        val retTab = nodesProfileReturnTabIndex
        nodesProfileReturnGroupId = null
        nodesProfileReturnTabIndex = null
        selectedMeshNode = null
        when {
            gid != null -> {
                selectedTab = 0
                showGroupsScreen = true
                groupsNavVm.selectGroup(gid)
            }
            retTab != null -> {
                selectedTab = retTab
            }
        }
    }

    LaunchedEffect(bleConnected) {
        if (!bleConnected) {
            firmwareSettingsOpen = false
        }
    }

    BackHandler(enabled = bubbleMenuSession != null) {
        bubbleMenuSession = null
    }

    BackHandler(
        enabled = selectedTab == 0 &&
            (activeConversationChannel != null || activeDirectMessagePeer != null) &&
            bubbleMenuSession == null,
    ) {
        activeConversationChannel = null
        activeDirectMessagePeer = null
    }

    BackHandler(enabled = selectedTab == 1 && selectedMeshNode != null) {
        dismissNodeProfileFromNav()
    }

    BackHandler(enabled = settingsSubscreenOpen) {
        when {
            loraSettingsOpen -> loraSettingsOpen = false
            userSettingsOpen -> userSettingsOpen = false
            channelsSettingsOpen -> channelsSettingsOpen = false
            securitySettingsOpen -> securitySettingsOpen = false
            deviceSettingsOpen -> deviceSettingsOpen = false
            mqttSettingsOpen -> mqttSettingsOpen = false
            externalNotificationsSettingsOpen -> externalNotificationsSettingsOpen = false
            notificationsSettingsOpen -> notificationsSettingsOpen = false
            telemetrySettingsOpen -> telemetrySettingsOpen = false
            firmwareSettingsOpen -> firmwareSettingsOpen = false
            matrixSettingsOpen -> matrixSettingsOpen = false
        }
    }

    // Вкладки «Узлы» (без карточки узла), «Карта», «Настройки» (корень): жест «назад» слева направо не закрывает экран чата.
    BackHandler(
        enabled = selectedTab == 2 ||
            (selectedTab == 1 && selectedMeshNode == null) ||
            (selectedTab == 3 && !settingsSubscreenOpen),
    ) {
        // намеренно пусто
    }

    // Вкладка «Сообщения», список каналов: жест «назад» с краёв (слева направо / справа налево в RTL) не уводит приложение в фон.
    BackHandler(
        enabled = selectedTab == 0 &&
            activeConversationChannel == null &&
            activeDirectMessagePeer == null &&
            bubbleMenuSession == null,
    ) {
        // намеренно пусто
    }

    BackHandler(enabled = messageHistoryOpen) {
        messageHistoryOpen = false
    }

    val btAnim = rememberInfiniteTransition(label = "btBar")
    val btBarScale by btAnim.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "btBarScale"
    )

    val gattConnState by NodeGattConnection.connectionState.collectAsStateWithLifecycle()
    val gattSyncStep by NodeGattConnection.syncStep.collectAsStateWithLifecycle()
    val gattTargetAddr = NodeGattConnection.targetDevice?.address
    val nodeAppReady = remember(deviceAddress, gattConnState, gattSyncStep, gattTargetAddr) {
        isMeshNodeSessionAppReady(deviceAddress, gattConnState, gattSyncStep, gattTargetAddr)
    }

    LaunchedEffect(Unit) {
        AuraExperience.reconcile(context.applicationContext)
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) {
            AuraProgressCounters.markMapVisited(context.applicationContext)
        }
    }

    LaunchedEffect(deviceAddress, gattSyncStep, loraBootstrap) {
        val a = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
        channelUiModemPreset = if (a != null) {
            MeshNodeSyncMemoryStore.getLora(a)?.modemPreset ?: loraBootstrap?.modemPreset
        } else {
            null
        }
    }

    fun clearSettingsGattWait() {
        settingsGattWaitOverlay = false
        pendingSettingsDestination = null
    }

    fun openLoRaSettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        val a = deviceAddrOrFail() ?: return
        closeAllSettingSubscreens()
        MeshNodeSyncMemoryStore.getLora(a)?.let { cached ->
            loraBootstrap = cached
            loraSettingsOpen = true
            return
        }
        val title = context.getString(R.string.settings_lora)
        nodeSyncOverlay = NodeSyncOverlayState.Running(title, 0)
        fetchMeshWireLoRaConfig(
            context.applicationContext, a,
            onSyncProgress = { p ->
                nodeSyncOverlay = NodeSyncOverlayState.Running(title, p)
            },
            localNodeNum = localNodeNumForRadio,
        ) { state, err ->
            if (state != null) {
                MeshNodeSyncMemoryStore.putLora(a, state)
                loraBootstrap = state
                nodeSyncOverlay = null
                loraSettingsOpen = true
            } else {
                nodeSyncOverlay = NodeSyncOverlayState.Failed(
                    err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить LoRa.",
                )
            }
        }
    }

    fun openUserSettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        val a = deviceAddrOrFail() ?: return
        closeAllSettingSubscreens()
        MeshNodeSyncMemoryStore.getUser(a)
            ?.takeIf { it.longName.isNotBlank() || it.shortName.isNotBlank() }
            ?.let { cached ->
                userBootstrap = cached
                userSettingsOpen = true
                return
            }
        val title = context.getString(R.string.settings_user)
        val numHint = MeshWireNodeNum.parseToUInt(nodeId)
        nodeSyncOverlay = NodeSyncOverlayState.Running(title, 0)
        fetchMeshWireUserProfile(
            context.applicationContext, a,
            expectedNodeNum = numHint,
            onSyncProgress = { p ->
                nodeSyncOverlay = NodeSyncOverlayState.Running(title, p)
            },
            localNodeNum = localNodeNumForRadio,
        ) { profile, err ->
            if (profile != null) {
                MeshNodeSyncMemoryStore.putUser(a, profile)
                userBootstrap = profile
                nodeSyncOverlay = null
                userSettingsOpen = true
            } else {
                nodeSyncOverlay = NodeSyncOverlayState.Failed(
                    err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить профиль.",
                )
            }
        }
    }

    fun openChannelsSettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        val a = deviceAddrOrFail() ?: return
        closeAllSettingSubscreens()
        MeshNodeSyncMemoryStore.getChannels(a)
            ?.takeIf { it.channels.isNotEmpty() }
            ?.let { cached ->
                channelsBootstrap = cached
                channelsSettingsOpen = true
                return
            }
        val title = context.getString(R.string.settings_channels)
        nodeSyncOverlay = NodeSyncOverlayState.Running(title, 0)
        fetchMeshWireChannels(
            context.applicationContext, a,
            onSyncProgress = { p ->
                nodeSyncOverlay = NodeSyncOverlayState.Running(title, p)
            },
            localNodeNum = localNodeNumForRadio,
        ) { result, err ->
            if (result != null) {
                val stored = MeshNodeSyncMemoryStore.putChannels(a, result)
                channelsBootstrap = stored
                nodeSyncOverlay = null
                channelsSettingsOpen = true
            } else {
                nodeSyncOverlay = NodeSyncOverlayState.Failed(
                    err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить каналы.",
                )
            }
        }
    }

    fun openSecuritySettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        val a = deviceAddrOrFail() ?: return
        closeAllSettingSubscreens()
        MeshNodeSyncMemoryStore.getSecurity(a)?.let { cached ->
            securityBootstrap = cached
            securitySettingsOpen = true
            return
        }
        val title = context.getString(R.string.settings_security)
        nodeSyncOverlay = NodeSyncOverlayState.Running(title, 0)
        fetchMeshWireSecurityConfig(
            context.applicationContext, a,
            onSyncProgress = { p ->
                nodeSyncOverlay = NodeSyncOverlayState.Running(title, p)
            },
            localNodeNum = localNodeNumForRadio,
        ) { state, err ->
            if (state != null) {
                MeshNodeSyncMemoryStore.putSecurity(a, state)
                securityBootstrap = state
                nodeSyncOverlay = null
                securitySettingsOpen = true
            } else {
                nodeSyncOverlay = NodeSyncOverlayState.Failed(
                    err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить безопасность.",
                )
            }
        }
    }

    fun openDeviceSettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        val a = deviceAddrOrFail() ?: return
        closeAllSettingSubscreens()
        MeshNodeSyncMemoryStore.getDevice(a)?.let { cached ->
            deviceBootstrap = cached
            deviceSettingsOpen = true
            return
        }
        val title = context.getString(R.string.settings_device)
        nodeSyncOverlay = NodeSyncOverlayState.Running(title, 0)
        fetchMeshWireDeviceConfig(
            context.applicationContext, a,
            onSyncProgress = { p ->
                nodeSyncOverlay = NodeSyncOverlayState.Running(title, p)
            },
            localNodeNum = localNodeNumForRadio,
        ) { state, err ->
            if (state != null) {
                MeshNodeSyncMemoryStore.putDevice(a, state)
                deviceBootstrap = state
                nodeSyncOverlay = null
                deviceSettingsOpen = true
            } else {
                nodeSyncOverlay = NodeSyncOverlayState.Failed(
                    err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить настройки устройства.",
                )
            }
        }
    }

    fun openMqttSettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        val a = deviceAddrOrFail() ?: return
        closeAllSettingSubscreens()
        MeshNodeSyncMemoryStore.getMqtt(a)?.let { cached ->
            mqttBootstrap = cached
            mqttSettingsOpen = true
            return
        }
        val title = context.getString(R.string.settings_mqtt)
        nodeSyncOverlay = NodeSyncOverlayState.Running(title, 0)
        fetchMeshWireMqttConfig(
            context.applicationContext, a,
            onSyncProgress = { p ->
                nodeSyncOverlay = NodeSyncOverlayState.Running(title, p)
            },
            localNodeNum = localNodeNumForRadio,
        ) { state, err ->
            if (state != null) {
                MeshNodeSyncMemoryStore.putMqtt(a, state)
                mqttBootstrap = state
                nodeSyncOverlay = null
                mqttSettingsOpen = true
                            } else {
                nodeSyncOverlay = NodeSyncOverlayState.Failed(
                    err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить MQTT.",
                )
            }
        }
    }

    fun openNotificationsSettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        closeAllSettingSubscreens()
        notificationsSettingsOpen = true
    }

    fun openMatrixSettingsScreen() {
        if (!matrixFeatureUnlocked) return
        closeAllSettingSubscreens()
        matrixSettingsOpen = true
    }

    fun openExternalNotificationsSettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        val a = deviceAddrOrFail() ?: return
        closeAllSettingSubscreens()
        MeshNodeSyncMemoryStore.getExternalNotification(a)?.let { cached ->
            externalNotificationsBootstrap = cached
            externalNotificationsSettingsOpen = true
            return
        }
        val title = context.getString(R.string.settings_external_notifications)
        nodeSyncOverlay = NodeSyncOverlayState.Running(title, 0)
        fetchMeshWireExternalNotificationConfig(
            context.applicationContext, a,
            onSyncProgress = { p ->
                nodeSyncOverlay = NodeSyncOverlayState.Running(title, p)
            },
            localNodeNum = localNodeNumForRadio,
        ) { state, err ->
            if (state != null) {
                MeshNodeSyncMemoryStore.putExternalNotification(a, state)
                externalNotificationsBootstrap = state
                nodeSyncOverlay = null
                externalNotificationsSettingsOpen = true
            } else {
                nodeSyncOverlay = NodeSyncOverlayState.Failed(
                    err?.takeIf { it.isNotBlank() }
                        ?: "Не удалось загрузить внешние уведомления.",
                )
            }
        }
    }

    fun openTelemetrySettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        val a = deviceAddrOrFail() ?: return
        closeAllSettingSubscreens()
        MeshNodeSyncMemoryStore.getTelemetry(a)?.let { cached ->
            telemetryBootstrap = cached
            telemetrySettingsOpen = true
            return
        }
        val title = context.getString(R.string.settings_telemetry)
        nodeSyncOverlay = NodeSyncOverlayState.Running(title, 0)
        fetchMeshWireTelemetryConfig(
            context.applicationContext, a,
            onSyncProgress = { p ->
                nodeSyncOverlay = NodeSyncOverlayState.Running(title, p)
            },
            localNodeNum = localNodeNumForRadio,
        ) { state, err ->
            if (state != null) {
                MeshNodeSyncMemoryStore.putTelemetry(a, state)
                telemetryBootstrap = state
                nodeSyncOverlay = null
                telemetrySettingsOpen = true
            } else {
                nodeSyncOverlay = NodeSyncOverlayState.Failed(
                    err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить телеметрию.",
                )
            }
        }
    }

    fun openFirmwareSettingsAfterReady() {
        if (blockSettingsSubscreenOpen()) return
        closeAllSettingSubscreens()
        firmwareSettingsOpen = true
    }

    LaunchedEffect(nodeAppReady, settingsGattWaitOverlay, pendingSettingsDestination) {
        if (!settingsGattWaitOverlay || pendingSettingsDestination == null || !nodeAppReady) return@LaunchedEffect
        val dest = pendingSettingsDestination!!
        pendingSettingsDestination = null
        settingsGattWaitOverlay = false
        when (dest) {
            PendingSettingsDestination.LoRa -> openLoRaSettingsAfterReady()
            PendingSettingsDestination.User -> openUserSettingsAfterReady()
            PendingSettingsDestination.Channels -> openChannelsSettingsAfterReady()
            PendingSettingsDestination.Security -> openSecuritySettingsAfterReady()
            PendingSettingsDestination.Device -> openDeviceSettingsAfterReady()
            PendingSettingsDestination.Mqtt -> openMqttSettingsAfterReady()
            PendingSettingsDestination.ExternalNotifications -> openExternalNotificationsSettingsAfterReady()
            PendingSettingsDestination.Telemetry -> openTelemetrySettingsAfterReady()
            PendingSettingsDestination.Firmware -> openFirmwareSettingsAfterReady()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 3) clearSettingsGattWait()
    }

    MeshBluetoothScanDialog(
        visible = showConnectionsDialog,
        onDismissRequest = {
            showConnectionsDialog = false
            meshConnDialogInitialTab = MeshConnectionInitialTab.Bluetooth
        },
        sessionNodeId = nodeId,
        sessionMeshAddress = deviceAddress,
        onSessionNodeIdChange = onNodeIdChange,
        onSessionMeshAddressChange = onDeviceAddressChange,
        onDeviceLinked = { nid, addr -> onDeviceLinked(nid, addr) },
        onDeviceUnbound = onBleUnbind,
        onNavigateToPassword = onNavigateToPassword,
        bondedUnbindHeaderEnabled = true,
        initialConnectionTab = meshConnDialogInitialTab,
    )

    if (clearNodeListDialogOpen) {
        // Палитра как на иммерсивной вкладке «Узлы» (matrix / пузыри).
        val nodeDialogBg = Color(0xFF0A2036)
        val nodeDialogBorder = Color(0xFF42E6FF).copy(alpha = 0.35f)
        val nodeDialogAccent = Color(0xFF63FFD7)
        val nodeDialogAccentDim = Color(0xFF6DEBFF)
        val nodeDialogText = Color(0xFFE7FCFF)
        val nodeDialogMuted = Color(0xFF8CB0BF)
        val nodeDialogTrackOff = Color(0xFF2A3A4A)
        val nodeDialogShape = RoundedCornerShape(22.dp)
        AlertDialog(
            onDismissRequest = {
                if (!clearNodeListSending) clearNodeListDialogOpen = false
            },
            modifier = Modifier.border(1.dp, nodeDialogBorder, nodeDialogShape),
            containerColor = nodeDialogBg,
            shape = nodeDialogShape,
            titleContentColor = nodeDialogText,
            textContentColor = nodeDialogMuted,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = nodeDialogAccentDim,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.clear_node_list_dialog_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.clear_node_list_save_favorites),
                        color = nodeDialogText,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Switch(
                        checked = preserveFavoritesOnClear,
                        onCheckedChange = { preserveFavoritesOnClear = it },
                        enabled = !clearNodeListSending,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = nodeDialogAccent,
                            checkedTrackColor = nodeDialogAccent.copy(alpha = 0.45f),
                            uncheckedThumbColor = nodeDialogMuted,
                            uncheckedTrackColor = nodeDialogTrackOff,
                        ),
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { if (!clearNodeListSending) clearNodeListDialogOpen = false },
                        enabled = !clearNodeListSending,
                    ) {
                        Text(
                            stringResource(R.string.action_cancel),
                            color = nodeDialogMuted,
                        )
                    }
                    Button(
                        onClick = {
                            val a = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
                            val num = localNodeNumForRadio
                            when {
                                a == null -> Toast.makeText(
                                    context,
                                    context.getString(R.string.clear_node_list_toast_need_device),
                                    Toast.LENGTH_SHORT,
                                ).show()

                                num == null -> Toast.makeText(
                                    context,
                                    context.getString(R.string.clear_node_list_toast_need_node_id),
                                    Toast.LENGTH_SHORT,
                                ).show()

                                else -> {
                                    clearNodeListSending = true
                                    MeshNodeRemoteActions.sendAdminNodedbReset(
                                        context.applicationContext,
                                        a,
                                        num,
                                        preserveFavoritesOnClear,
                                    ) { ok, err, _ ->
                                        clearNodeListSending = false
                                        if (ok) {
                                            MeshNodeListDiskCache.clear(context.applicationContext, a)
                                            MeshNodeDbRepository.clearForDevice(a)
                                            nodeListClearSignal++
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.clear_node_list_toast_ok),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            clearNodeListDialogOpen = false
                                            markNodeConfigWriteSuccess()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.clear_node_list_toast_err,
                                                    err?.takeIf { it.isNotBlank() } ?: "BLE",
                                                ),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !clearNodeListSending,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = nodeDialogAccent,
                            contentColor = Color(0xFF071425),
                            disabledContainerColor = nodeDialogTrackOff,
                            disabledContentColor = nodeDialogMuted.copy(alpha = 0.6f),
                        ),
                    ) {
                        Text(
                            if (clearNodeListSending) "…" else stringResource(R.string.clear_node_list_send),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
        )
    }

    clearHistoryChannel?.let { ch ->
        val deviceAddrForClear = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
        AlertDialog(
            onDismissRequest = { clearHistoryChannel = null },
            containerColor = Color(0xFF1A2330),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD0D8E0),
            title = { Text("Очистить историю чата?") },
            text = {
                Text(
                    "Сообщения канала «${conversationChannelTitle(ch, channelUiModemPreset)}» (индекс ${ch.index}) будут убраны из списка на устройстве. " +
                        "Копии остаются во внутреннем хранилище (файлы канала и запись в базе «История сообщений», если включена). " +
                        "Эфир и другие устройства не затрагиваются.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val snapshot = ch
                        clearHistoryChannel = null
                        val mac = deviceAddrForClear
                        if (mac != null) {
                            val macNorm = MeshNodeSyncMemoryStore.normalizeKey(mac)
                            val idHex = ChatHistoryFileStore.normalizeNodeIdHex(nodeId)
                            clearHistoryScope.launch(Dispatchers.IO) {
                                val rows = chatDaoForClear.getAllForChannelAsc(macNorm, snapshot.index)
                                if (idHex.isNotEmpty()) {
                                    ChatHistoryFileStore.ensureArchiveLayout(context.applicationContext, idHex)
                                }
                                ChatHistoryFileStore.syncMissingTextMessagesToArchive(
                                    context.applicationContext,
                                    macNorm,
                                    snapshot.index,
                                    rows,
                                    idHex.ifEmpty { null },
                                )
                                ChatHistoryFileStore.purgeAllChannelMediaForHistoryClear(
                                    context.applicationContext,
                                    macNorm,
                                    snapshot.index,
                                    idHex.ifEmpty { null },
                                )
                                chatDaoForClear.deleteAllForChannel(macNorm, snapshot.index)
                                withContext(Dispatchers.Main) {
                                    channelListPreviewRefresh++
                                    if (activeConversationChannel?.rowKey == snapshot.rowKey &&
                                        activeConversationChannel?.index == snapshot.index
                                    ) {
                                        historyClearBumpForConversation++
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Text("Очистить", color = Color(0xFFFF8A80))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearHistoryChannel = null }) {
                    Text("Отмена", color = MtActionGreen)
                }
            },
        )
    }

    CompositionLocalProvider(
        LocalNotifyNodeConfigWrite provides {
            markNodeConfigWriteSuccess()
        },
    ) {
    val wallpaperResRoot = chatWallpaperDrawableRes(chatWallpaperIndex)
    val forceMatrixOnMessagesTab = selectedTab == 0
    val showFullScreenChatWallpaper =
        !forceMatrixOnMessagesTab &&
            selectedTab == 0 &&
            (activeConversationChannel != null || activeDirectMessagePeer != null) &&
            wallpaperResRoot != null
    val matrixBackdropVisible =
        forceMatrixOnMessagesTab || !showFullScreenChatWallpaper
    Box(Modifier.fillMaxSize()) {
        // Всегда в композиции — анимация не сбрасывается при смене вкладки; скрываем через alpha.
        MatrixRainLayer(
            densityMultiplier = matrixDensity,
            speedMultiplier = matrixSpeed,
            dimOverlayAlpha = matrixDim,
            rainAnimationEnabled = matrixRainAnimationEnabled && matrixBackdropVisible,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (matrixBackdropVisible) 1f else 0f),
        )
        if (showFullScreenChatWallpaper) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(wallpaperResRoot!!),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (chatWallpaperDimPercent > 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = chatWallpaperDimPercent / 100f)),
                    )
                }
            }
        }
    Scaffold(
        containerColor = when {
            showFullScreenChatWallpaper -> Color.Transparent
            matrixBackdropVisible -> Color.Transparent
            else -> MstSett.mainTabBackground
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (snackbarAboveInput) 74.dp else 0.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = Color(0xFF7A8088),
                        shape = RoundedCornerShape(14.dp),
                    ),
                    containerColor = MstSett.card,
                    contentColor = MstSett.text,
                    actionColor = MstSett.accent,
                    dismissActionContentColor = MstSett.muted,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        topBar = {
            when {
                selectedTab == 0 && (activeConversationChannel != null || activeDirectMessagePeer != null) -> {
                    val convCh = activeConversationChannel
                    val dmPeer = activeDirectMessagePeer
                    val titleText = when {
                        dmPeer != null -> dmPeer.displayLongName()
                        convCh != null -> conversationChannelTitle(convCh, channelUiModemPreset)
                        else -> ""
                    }
                    Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                    Surface(
                                        shape = ChatConversationHeaderMenuBubbleShape,
                                        color = ChatConversationHeaderBubbleBg,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 6.dp,
                                        border = BorderStroke(1.dp, ChatConversationHeaderBubbleStroke),
                                    ) {
                            IconButton(onClick = {
                                activeDirectMessagePeer = null
                                activeConversationChannel = null
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = ChatConversationHeaderTitleBubbleShape,
                                        color = ChatConversationHeaderBubbleBg,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 6.dp,
                                        border = BorderStroke(1.dp, ChatConversationHeaderBubbleStroke),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = titleText,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                                modifier = Modifier
                                                    .widthIn(max = 220.dp)
                                                    .combinedClickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                        onClick = {},
                                                        onLongClick = convCh?.let { ch ->
                                                            { clearHistoryChannel = ch }
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                    if (convCh != null && dmPeer == null) {
                                        val secUi = meshStoredChannelSecurityUi(convCh)
                                        Surface(
                                            shape = ChatConversationHeaderMenuBubbleShape,
                                            color = ChatConversationHeaderBubbleBg,
                                            tonalElevation = 0.dp,
                                            shadowElevation = 6.dp,
                                            border = BorderStroke(1.dp, ChatConversationHeaderBubbleStroke),
                                        ) {
                                            IconButton(
                                                onClick = { channelSecurityStatusFor = convCh },
                                            ) {
                                                Icon(
                                                    imageVector = securityLockIcon(secUi),
                                                    contentDescription = "Безопасность канала",
                                                    tint = securityLockTint(secUi),
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                    }
                                    Box {
                                        val fontSliderInteraction = remember { MutableInteractionSource() }
                                        val fontSliderColors = SliderDefaults.colors(
                                            thumbColor = ConversationOverflowMenuAccent,
                                            activeTrackColor = ConversationOverflowMenuAccent,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                                        )
                                        Surface(
                                            shape = ChatConversationHeaderMenuBubbleShape,
                                            color = ChatConversationHeaderBubbleBg,
                                            tonalElevation = 0.dp,
                                            shadowElevation = 6.dp,
                                            border = BorderStroke(1.dp, ChatConversationHeaderBubbleStroke),
                                        ) {
                                            IconButton(
                                                onClick = { conversationMenuExpanded = true },
                                            ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Меню",
                                        tint = Color.White,
                                    )
                                            }
                                }
                                DropdownMenu(
                                    expanded = conversationMenuExpanded,
                                    onDismissRequest = { conversationMenuExpanded = false },
                                            modifier = Modifier.widthIn(min = 237.dp),
                                            shape = ConversationOverflowMenuShape,
                                            containerColor = ConversationOverflowMenuFill,
                                            tonalElevation = 0.dp,
                                            shadowElevation = 20.dp,
                                            border = BorderStroke(1.dp, ConversationOverflowMenuBorder),
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.chat_conversation_search_messages),
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                },
                                                onClick = {
                                                    conversationMenuExpanded = false
                                                    channelMessageSearchActive = true
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Search,
                                                        contentDescription = null,
                                                        tint = ConversationOverflowMenuAccent,
                                                        modifier = Modifier.size(22.dp),
                                                    )
                                                },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = Color.White,
                                                    leadingIconColor = ConversationOverflowMenuAccent,
                                                ),
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.chat_wallpaper_menu),
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                },
                                                onClick = {
                                                    conversationMenuExpanded = false
                                                    showChatWallpaperPicker = true
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Filled.Image,
                                                        contentDescription = null,
                                                        tint = ConversationOverflowMenuAccent,
                                                        modifier = Modifier.size(22.dp),
                                                    )
                                                },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = Color.White,
                                                    leadingIconColor = ConversationOverflowMenuAccent,
                                                ),
                                            )
                                            val wallpaperDimSliderInteraction =
                                                remember { MutableInteractionSource() }
                                            val wallpaperDimSliderColors = SliderDefaults.colors(
                                                thumbColor = ConversationOverflowMenuAccent,
                                                activeTrackColor = ConversationOverflowMenuAccent,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                                            )
                                            Surface(
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .fillMaxWidth(),
                                                shape = RoundedCornerShape(18.dp),
                                                color = ConversationOverflowMenuInnerSurface,
                                                shadowElevation = 0.dp,
                                                tonalElevation = 0.dp,
                                                border = BorderStroke(
                                                    1.dp,
                                                    Color.White.copy(alpha = 0.08f),
                                                ),
                                            ) {
                                                Column(
                                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.DarkMode,
                                                            contentDescription = null,
                                                            tint = ConversationOverflowMenuAccent.copy(alpha = 0.95f),
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            stringResource(R.string.chat_wallpaper_dim),
                                                            color = Color.White.copy(alpha = 0.95f),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium,
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 6.dp),
                                                    ) {
                                                        ConversationOverflowMenuSlider(
                                                            value = chatWallpaperDimPercent,
                                                            onValueChange = { v ->
                                                                chatWallpaperDimPercent = v
                                                                ChatWallpaperPreferences.setWallpaperDimPercent(
                                                                    context.applicationContext,
                                                                    v.toInt().coerceIn(0, 100),
                                                                )
                                                            },
                                                            valueRange = 0f..100f,
                                                            interactionSource = wallpaperDimSliderInteraction,
                                                            colors = wallpaperDimSliderColors,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        Text(
                                                            "${chatWallpaperDimPercent.toInt()}%",
                                                            color = Color.White.copy(alpha = 0.78f),
                                                            fontSize = ConversationOverflowMenuSliderPercentTextSp,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(start = 8.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .fillMaxWidth(),
                                                shape = RoundedCornerShape(18.dp),
                                                color = ConversationOverflowMenuInnerSurface,
                                                shadowElevation = 0.dp,
                                                tonalElevation = 0.dp,
                                                border = BorderStroke(
                                                    1.dp,
                                                    Color.White.copy(alpha = 0.08f),
                                                ),
                                            ) {
                                                Column(
                                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.FormatSize,
                                                            contentDescription = null,
                                                            tint = ConversationOverflowMenuAccent.copy(alpha = 0.95f),
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            stringResource(R.string.chat_conversation_font_scale),
                                                            color = Color.White.copy(alpha = 0.95f),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium,
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 6.dp),
                                                    ) {
                                                        ConversationOverflowMenuSlider(
                                                            value = channelConversationFontScalePercent,
                                                            onValueChange = {
                                                                channelConversationFontScalePercent = it
                                                                ChatConversationPreferences.setFontScalePercent(
                                                                    context.applicationContext,
                                                                    it,
                                                                )
                                                            },
                                                            valueRange = 0f..100f,
                                                            interactionSource = fontSliderInteraction,
                                                            colors = fontSliderColors,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        Text(
                                                            "${channelConversationFontScalePercent.toInt()}%",
                                                            color = Color.White.copy(alpha = 0.78f),
                                                            fontSize = ConversationOverflowMenuSliderPercentTextSp,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(start = 8.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                color = Color.White.copy(alpha = 0.08f),
                                            )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Значения безопасности канала",
                                                color = Color.White,
                                                        fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        onClick = {
                                            conversationMenuExpanded = false
                                            channelSecurityLegendFor = convCh
                                        },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Filled.Lock,
                                                        contentDescription = null,
                                                        tint = ConversationOverflowMenuAccent,
                                                        modifier = Modifier.size(22.dp),
                                                    )
                                                },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = Color.White,
                                                    leadingIconColor = ConversationOverflowMenuAccent,
                                                ),
                                            )
                                        }
                                    }
                                }
                        if (channelMessageSearchActive) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = ChatConversationHeaderTitleBubbleShape,
                                color = ChatConversationHeaderBubbleBg,
                                tonalElevation = 0.dp,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, ChatConversationHeaderBubbleStroke),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = {
                                        channelMessageSearchActive = false
                                        channelMessageSearchQuery = ""
                                    }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.action_cancel),
                                            tint = Color.White,
                                        )
                                    }
                                    TextField(
                                        value = channelMessageSearchQuery,
                                        onValueChange = { channelMessageSearchQuery = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(conversationSearchFocusRequester),
                                        placeholder = {
                                            Text(
                                                stringResource(R.string.chat_conversation_search_messages),
                                                color = Color.White.copy(alpha = 0.45f),
                                            )
                                        },
                                        trailingIcon = {
                                            if (channelMessageSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { channelMessageSearchQuery = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = stringResource(R.string.action_cancel),
                                                        tint = Color.White.copy(alpha = 0.7f),
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = Color(0xFF00E676),
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                            focusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
                                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                selectedTab == 1 && selectedMeshNode != null -> {
                    val n = selectedMeshNode!!
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = { dismissNodeProfileFromNav() },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = n.longName,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                maxLines = 1,
                            )
                        },
                        actions = { Spacer(Modifier.size(48.dp)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0D1525).copy(alpha = 0.9f),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                }
                selectedTab == 3 && loraSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { loraSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "LoRa",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF121212)
                        )
                    )
                }
                selectedTab == 3 && securitySettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { securitySettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Безопасность",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF121212)
                        )
                    )
                }
                selectedTab == 3 && userSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { userSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Пользователь",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0D1525)
                        )
                    )
                }
                selectedTab == 3 && channelsSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { channelsSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Каналы",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF121212),
                        ),
                    )
                }
                selectedTab == 3 && deviceSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { deviceSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Устройство",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0C140E),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                }
                selectedTab == 3 && externalNotificationsSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { externalNotificationsSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.nav_back),
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.settings_external_notifications),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF121212),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                }
                selectedTab == 3 && notificationsSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { notificationsSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.settings_phone_notifications),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF121212),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                }
                selectedTab == 3 && matrixSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { matrixSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.nav_back),
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.settings_matrix),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF121212),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                }
                selectedTab == 3 && mqttSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { mqttSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "MQTT",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF121212),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                }
                selectedTab == 3 && telemetrySettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { telemetrySettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Телеметрия",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        },
                        actions = {
                            Spacer(Modifier.size(48.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF121212),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                }
                selectedTab == 3 && firmwareSettingsOpen -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { firmwareSettingsOpen = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.nav_back),
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.settings_firmware_update),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        },
                        actions = { Spacer(Modifier.size(48.dp)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0D1812),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-45).dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                color = ChatConversationHeaderBubbleBg.copy(alpha = 0.9f),
                                tonalElevation = 0.dp,
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, ChatConversationHeaderBubbleStroke),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(24.dp))
                                            .drawBehind {
                                                drawRect(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            Color(0xFF08233C),
                                                            Color(0xFF041628),
                                                        ),
                                                    ),
                                                )
                                                val dotSpacing = 11.dp.toPx()
                                                val dotRadius = 1.4.dp.toPx()
                                                var y = dotSpacing * 0.55f
                                                while (y < size.height - dotSpacing * 0.35f) {
                                                    var x = dotSpacing * 0.55f
                                                    while (x < size.width - dotSpacing * 0.35f) {
                                                        drawCircle(
                                                            color = Color(0xFF42A8C9).copy(alpha = 0.14f),
                                                            radius = dotRadius,
                                                            center = Offset(x, y),
                                                        )
                                                        x += dotSpacing
                                                    }
                                                    y += dotSpacing
                                                }
                                            },
                                    )
                                    IconButton(
                                        onClick = onOpenProfile,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .size(40.dp),
                                    ) {
                                        val avatarDp = (38f * 1.15f * 2f * ProfileNodeBubbleScale).dp
                                        val profilePhoto = profileAvatarPath?.trim()?.takeIf { it.isNotEmpty() }
                                        VipAvatarFrame(
                                            active = rememberLocalVipActive(),
                                            avatarSize = avatarDp,
                                            onAvatarClick = onOpenProfile,
                                            nodeIdHex = nodeId,
                                        ) {
                                            if (profilePhoto != null) {
                                                LocalProfileAvatarCircle(
                                                    filePath = profilePhoto,
                                                    size = avatarDp,
                                                    placeholderBackground = Color(0xFF0D1A2E),
                                                    placeholderIconTint = Color(0xFF00D4FF),
                                                    onClick = null,
                                                    contentDescription = stringResource(R.string.settings_user),
                                                )
                                            } else {
                                                MeshNodeAvatarByNodeId(
                                                    nodeIdHex = nodeId,
                                                    contentDescription = stringResource(R.string.settings_user),
                                                    modifier = Modifier.size(avatarDp),
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        val btBarColor = if (nodeAppReady) Color(0xFF4CAF50) else Color(0xFFFF5252)
                                        val btBarGlowAlphaMul = if (nodeAppReady) 0.15f else 0.35f
                                        IconButton(
                                            onClick = {
                                                meshConnDialogInitialTab = MeshConnectionInitialTab.Bluetooth
                                                showConnectionsDialog = true
                                            },
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .size(40.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .drawBehind {
                                                        if (!showConnectionsDialog) {
                                                            drawCircle(
                                                                color = btBarColor.copy(
                                                                    alpha = (btBarScale - 0.88f) / 0.2f * btBarGlowAlphaMul,
                                                                ),
                                                                radius = 18.dp.toPx() * btBarScale,
                                                            )
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Router,
                                                    contentDescription = "Состояние ноды",
                                                    tint = btBarColor,
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .graphicsLayer {
                                                            scaleX = btBarScale
                                                            scaleY = btBarScale
                                                        },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(156.dp)
                                    .graphicsLayer {
                                        scaleX = 0.648f
                                        scaleY = 0.648f
                                    }
                                    .drawBehind {
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val ringRadius = minOf(size.width, size.height) * 0.42f
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0x6639ECFF),
                                                    Color.Transparent,
                                                ),
                                                center = center,
                                                radius = ringRadius * 1.35f,
                                            ),
                                            radius = ringRadius * 1.35f,
                                            center = center,
                                        )
                                        drawCircle(
                                            color = Color(0xFF07182B),
                                            radius = ringRadius * 0.74f,
                                            center = center,
                                        )
                                        drawCircle(
                                            color = Color(0xFF4AF2FF).copy(alpha = 0.8f),
                                            radius = ringRadius,
                                            center = center,
                                            style = Stroke(width = 3.dp.toPx()),
                                        )
                                        drawCircle(
                                            color = Color(0xFF22C9E8).copy(alpha = 0.45f),
                                            radius = ringRadius * 0.83f,
                                            center = center,
                                            style = Stroke(width = 2.dp.toPx()),
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = "Aura",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                        letterSpacing = 0.4.sp,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Spacer(
                                            Modifier
                                                .width(26.dp)
                                                .height(1.5.dp)
                                                .background(Color(0xFF2FAAC5).copy(alpha = 0.55f)),
                                        )
                                        Text(
                                            text = "MESH",
                                            color = Color(0xFF2A9DB9).copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            letterSpacing = 2.2.sp,
                                        )
                                        Spacer(
                                            Modifier
                                                .width(26.dp)
                                                .height(1.5.dp)
                                                .background(Color(0xFF2FAAC5).copy(alpha = 0.55f)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!(selectedTab == 0 && (activeConversationChannel != null || activeDirectMessagePeer != null))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
            Surface(
                        shape = RoundedCornerShape(36.dp),
                        color = ChatConversationHeaderBubbleBg.copy(alpha = 0.9f),
                        tonalElevation = 0.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.dp, ChatConversationHeaderBubbleStroke),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                                .widthIn(min = 200.dp, max = 420.dp)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val bottomTintActive = Color(0xFF00D4FF)
                    val bottomTintIdle = Color(0xFF4A5A6A)
                    IconButton(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f, fill = true),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = stringResource(R.string.tab_messages),
                            tint = if (selectedTab == 0) bottomTintActive else bottomTintIdle,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    IconButton(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f, fill = true),
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = stringResource(R.string.tab_nodes),
                            tint = if (selectedTab == 1) bottomTintActive else bottomTintIdle,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (vipRestricted) {
                                VipRestrictedToast.show(context)
                            } else {
                                selectedTab = 2
                            }
                        },
                        modifier = Modifier.weight(1f, fill = true),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = stringResource(R.string.tab_map),
                            tint = when {
                                vipRestricted -> bottomTintIdle.copy(alpha = 0.5f)
                                selectedTab == 2 -> bottomTintActive
                                else -> bottomTintIdle
                            },
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    IconButton(
                        onClick = { selectedTab = 3 },
                        modifier = Modifier.weight(1f, fill = true),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.tab_settings),
                            tint = if (selectedTab == 3) bottomTintActive else bottomTintIdle,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> {
                val dmPeerTab = activeDirectMessagePeer
                val addrDm = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
                val channelsDm = addrDm?.let { MeshNodeSyncMemoryStore.getChannels(it)?.channels }.orEmpty()
                val primaryDm = channelsDm.firstOrNull { it.index == 0 } ?: channelsDm.firstOrNull()
                if (dmPeerTab != null && primaryDm != null) {
                    ChannelConversationScreen(
                        padding = padding,
                        channel = primaryDm.copyForEdit(),
                        directMessagePeer = dmPeerTab,
                        deviceAddress = deviceAddress,
                        bleConnected = bleConnected,
                        localNodeNum = localNodeNumForRadio,
                        nodeId = nodeId,
                        hideCoordinatesTransmission = hideCoordinatesTransmission,
                        historyClearBumpForConversation = historyClearBumpForConversation,
                        onBubbleMenuSessionChange = { bubbleMenuSession = it },
                        messageFontScalePercent = channelConversationFontScalePercent,
                        messageSearchQuery = channelMessageSearchQuery,
                        messageSearchActive = channelMessageSearchActive,
                        chatWallpaperResId = chatWallpaperDrawableRes(chatWallpaperIndex),
                        matrixBackdropActive = matrixBackdropVisible,
                        initialDraftText = null,
                        onInitialDraftConsumed = { },
                        onBeaconShareLinkClick = { payload, senderLongName ->
                            if (vipRestricted) {
                                VipRestrictedToast.show(context)
                            } else {
                                pendingMapBeaconShare = payload
                                pendingBeaconShareSenderLabel = senderLongName.trim().takeIf { it.isNotEmpty() }
                                selectedTab = 2
                            }
                        },
                        onOpenUserProfile = { node ->
                            nodesProfileReturnTabIndex = 0
                            nodesProfileReturnGroupId = null
                            selectedTab = 1
                            selectedMeshNode = node
                        },
                    )
                } else {
                    val conv = activeConversationChannel
                    if (conv != null) {
                        ChannelConversationScreen(
                            padding = padding,
                            channel = conv,
                            directMessagePeer = null,
                            deviceAddress = deviceAddress,
                            bleConnected = bleConnected,
                            localNodeNum = localNodeNumForRadio,
                            nodeId = nodeId,
                            hideCoordinatesTransmission = hideCoordinatesTransmission,
                            historyClearBumpForConversation = historyClearBumpForConversation,
                            onBubbleMenuSessionChange = { bubbleMenuSession = it },
                            messageFontScalePercent = channelConversationFontScalePercent,
                            messageSearchQuery = channelMessageSearchQuery,
                            messageSearchActive = channelMessageSearchActive,
                            chatWallpaperResId = chatWallpaperDrawableRes(chatWallpaperIndex),
                            matrixBackdropActive = matrixBackdropVisible,
                            initialDraftText = pendingConversationDraft,
                            onInitialDraftConsumed = { pendingConversationDraft = null },
                            onBeaconShareLinkClick = { payload, senderLongName ->
                                if (vipRestricted) {
                                    VipRestrictedToast.show(context)
                                } else {
                                    pendingMapBeaconShare = payload
                                    pendingBeaconShareSenderLabel = senderLongName.trim().takeIf { it.isNotEmpty() }
                                    selectedTab = 2
                                }
                            },
                            onOpenUserProfile = { node ->
                                nodesProfileReturnTabIndex = 0
                                nodesProfileReturnGroupId = null
                                selectedTab = 1
                                selectedMeshNode = node
                            },
                        )
                    } else {
                    ChatTabContent(
                        padding = padding,
                        deviceAddress = deviceAddress,
                        bleConnected = bleConnected,
                        nodeId = nodeId,
                        channelPreviewRefreshKey = channelListPreviewRefresh,
                        modemPreset = channelUiModemPreset,
                        matrixBackdropActive = matrixBackdropVisible,
                        selectedMapChannelIndex = selectedMapChannelIndex,
                        showGroupsScreen = showGroupsScreen,
                        onShowGroupsScreenChange = { showGroupsScreen = it },
                        groupsVm = groupsNavVm,
                        onOpenDirectThread = { peer ->
                            activeConversationChannel = null
                            activeDirectMessagePeer = peer
                        },
                        onChannelClick = { ch ->
                            activeDirectMessagePeer = null
                            activeConversationChannel = ch.copyForEdit()
                        },
                        onChannelSelectForMap = { ch ->
                            selectedMapChannelIndex = ch.index
                            MapBeaconActiveChannelStore.setChannel(
                                index = ch.index,
                                title = conversationChannelTitle(ch, channelUiModemPreset),
                            )
                        },
                        onChannelLongPressClear = { ch ->
                            clearHistoryChannel = ch
                        },
                        onDirectThreadHistoryCleared = { peerNumCleared ->
                            historyClearBumpForConversation++
                            val open = activeDirectMessagePeer
                            if (open != null &&
                                (open.nodeNum and 0xFFFF_FFFFL) == (peerNumCleared and 0xFFFF_FFFFL)
                            ) {
                                activeDirectMessagePeer = null
                                pendingConversationDraft = null
                            }
                        },
                        onRequestNodesTabForNode = { node, openedFromGroupFolderId ->
                            selectedTab = 1
                            selectedMeshNode = node
                            nodesProfileReturnGroupId = openedFromGroupFolderId
                        },
                    )
                    }
                }
            }
            1 -> {
                NodesTabScreen(
                    padding = padding,
                    deviceAddress = deviceAddress,
                    bleConnected = bleConnected,
                    localNodeNum = localNodeNumForRadio,
                    profileAvatarPath = profileAvatarPath,
                    onProfileAvatarPathChange = onProfileAvatarPathChange,
                    selected = selectedMeshNode,
                    onSelect = {
                        nodesProfileReturnTabIndex = null
                        selectedMeshNode = it
                    },
                    nodeListClearSignal = nodeListClearSignal,
                    onClearNodeListClick = { clearNodeListDialogOpen = true },
                    matrixBackdropActive = matrixBackdropVisible,
                    matrixRainAnimationEnabled = matrixRainAnimationEnabled,
                    matrixDensity = matrixDensity,
                    matrixSpeed = matrixSpeed,
                    matrixDim = matrixDim,
                    onOpenNodeQrScanner = { showNodeQrScanner = true },
                    onNodeForgottenFromCache = { forgotten ->
                        if (selectedMeshNode?.nodeNum == forgotten.nodeNum) {
                            selectedMeshNode = null
                            nodesProfileReturnTabIndex = null
                            nodesProfileReturnGroupId = null
                        }
                    },
                    onOpenDirectMessage = { node ->
                        selectedTab = 0
                        val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
                        val channels = addr?.let { MeshNodeSyncMemoryStore.getChannels(it)?.channels }.orEmpty()
                        val primary = channels.firstOrNull { it.index == 0 } ?: channels.firstOrNull()
                        if (primary != null) {
                            activeConversationChannel = null
                            pendingConversationDraft = null
                            activeDirectMessagePeer = node
                        } else {
                            Toast.makeText(context, "Каналы ещё не загружены", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                if (showNodeQrScanner) {
                    NodeQrScannerOverlay(
                        onDismiss = { showNodeQrScanner = false },
                        onDecodedText = { raw ->
                            when (val p = AuraQr.parse(raw)) {
                                is AuraQr.NodeIdentity -> {
                                    showNodeQrScanner = false
                                    selectedMeshNode = meshtasticPeerSummaryForDm(
                                        p.nodeNum.toLong() and 0xFFFF_FFFFL,
                                        nodesTabUi.nodes,
                                    )
                                }
                                is AuraQr.PairSync -> {
                                    val base = p.baseUrl
                                    if (base.isNullOrBlank()) {
                                        Toast.makeText(context, "QR синхронизации без адреса сайта. Откройте сайт и обновите QR.", Toast.LENGTH_LONG).show()
                                        showNodeQrScanner = false
                                        return@NodeQrScannerOverlay
                                    }
                                    Toast.makeText(context, "Синхронизация… отправляю профиль на сайт", Toast.LENGTH_SHORT).show()
                                    thread {
                                        runCatching {
                                            SiteSyncClient.pushProfileToSiteBlocking(
                                                context = context,
                                                baseUrl = base,
                                                pairId = p.pairId,
                                                secret = p.secret,
                                            )
                                        }.onSuccess {
                                            Toast.makeText(context, "Профиль перенесён на сайт", Toast.LENGTH_SHORT).show()
                                        }.onFailure { e ->
                                            Toast.makeText(context, "Ошибка синхронизации: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    showNodeQrScanner = false
                                }
                                is AuraQr.ProfileExport -> {
                                    showNodeQrScanner = false
                                    when (
                                        val r = ProfileExportQrInAppHandler.handle(
                                            context,
                                            p,
                                            nodesTabUi.nodes,
                                        )
                                    ) {
                                        is ProfileExportInAppResult.RestoredVip -> {
                                            Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                                        }
                                        is ProfileExportInAppResult.OpenPeerProfile -> {
                                            selectedTab = 1
                                            selectedMeshNode = r.node
                                            Toast.makeText(
                                                context,
                                                "Профиль: ${r.node.displayLongName()}",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        is ProfileExportInAppResult.Error -> {
                                            Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                is AuraQr.Unknown -> {
                                    Toast.makeText(context, "Не удалось распознать QR", Toast.LENGTH_SHORT).show()
                                    showNodeQrScanner = false
                                }
                            }
                        },
                    )
                }
            }
            2 -> NetworkMapTabContent(
                padding = padding,
                deviceAddress = deviceAddress,
                bleConnected = bleConnected,
                localNodeNum = localNodeNumForRadio,
                beaconsOnlyMode = mapBeaconsOnlyMode,
                onBeaconsOnlyModeChange = { mapBeaconsOnlyMode = it },
                pendingBeaconShareImport = pendingMapBeaconShare,
                pendingBeaconShareSenderLabel = pendingBeaconShareSenderLabel,
                onPendingBeaconShareConsumed = {
                    pendingMapBeaconShare = null
                    pendingBeaconShareSenderLabel = null
                },
                onBeaconShareImportCancelled = { selectedTab = 0 },
                onWriteMessageToNode = { node ->
                    selectedTab = 0
                    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
                    val channels = addr?.let { MeshNodeSyncMemoryStore.getChannels(it)?.channels }.orEmpty()
                    val primary = channels.firstOrNull { it.index == 0 } ?: channels.firstOrNull()
                    if (primary != null) {
                        activeConversationChannel = null
                        pendingConversationDraft = null
                        activeDirectMessagePeer = node
                    }
                },
            )
            3 -> {
                when {
                securitySettingsOpen -> SecuritySettingsContent(
                    padding = padding,
                    deviceAddress = deviceAddress,
                    nodeId = nodeId,
                    bootstrap = securityBootstrap,
                    onBootstrapConsumed = { securityBootstrap = null },
                )
                loraSettingsOpen -> LoRaSettingsContent(
                    padding = padding,
                    deviceAddress = deviceAddress,
                    nodeId = nodeId,
                    bootstrap = loraBootstrap,
                    onBootstrapConsumed = { loraBootstrap = null },
                )
                userSettingsOpen -> UserSettingsContent(
                    padding = padding,
                    nodeId = nodeId,
                    deviceAddress = deviceAddress,
                    bootstrap = userBootstrap,
                    onBootstrapConsumed = { userBootstrap = null },
                )
                channelsSettingsOpen -> ChannelsSettingsContent(
                    padding = padding,
                    nodeId = nodeId,
                    deviceAddress = deviceAddress,
                    bootstrap = channelsBootstrap,
                    onBootstrapConsumed = { channelsBootstrap = null },
                    onReorderPushConfirmed = { clearAllLocalChannelChatsAfterReorderConfirm() },
                    onChannelsAppliedToNode = { channelListPreviewRefresh++ },
                )
                deviceSettingsOpen -> DeviceSettingsContent(
                    padding = padding,
                    deviceAddress = deviceAddress,
                    nodeId = nodeId,
                    bootstrap = deviceBootstrap,
                    onBootstrapConsumed = { deviceBootstrap = null },
                )
                externalNotificationsSettingsOpen -> ExternalNotificationsSettingsContent(
                    padding = padding,
                    deviceAddress = deviceAddress,
                    nodeId = nodeId,
                    bootstrap = externalNotificationsBootstrap,
                    onBootstrapConsumed = { externalNotificationsBootstrap = null },
                )
                notificationsSettingsOpen -> NotificationsSettingsContent(
                    padding = padding,
                )
                matrixSettingsOpen -> MatrixSettingsContent(
                    padding = padding,
                    density = matrixDensity,
                    speed = matrixSpeed,
                    dim = matrixDim,
                    onValuesChange = { d, s, m ->
                        MatrixRainPreferences.save(appCtxMatrix, d, s, m)
                        matrixDensity = d
                        matrixSpeed = s
                        matrixDim = m
                    },
                )
                mqttSettingsOpen -> MqttSettingsContent(
                    padding = padding,
                    deviceAddress = deviceAddress,
                    nodeId = nodeId,
                    bootstrap = mqttBootstrap,
                    onBootstrapConsumed = { mqttBootstrap = null },
                )
                telemetrySettingsOpen -> TelemetrySettingsContent(
                    padding = padding,
                    deviceAddress = deviceAddress,
                    nodeId = nodeId,
                    bootstrap = telemetryBootstrap,
                    onBootstrapConsumed = { telemetryBootstrap = null },
                )
                firmwareSettingsOpen -> FirmwareUpdateContent(
                    padding = padding,
                    deviceAddress = deviceAddress,
                    nodeId = nodeId,
                )
                else -> ChatSettingsTabContent(
                    padding = padding,
                    settingsListState = settingsRootListState,
                    deviceAddress = deviceAddress,
                    localNodeNum = localNodeNumForRadio,
                    matrixBackdropActive = matrixBackdropVisible,
                    matrixUnlocked = matrixFeatureUnlocked,
                    onMatrixLockedClick = { },
                    provideLocationToMesh = provideLocationToMesh,
                    hideCoordinatesTransmission = hideCoordinatesTransmission,
                    onProvideLocationToMeshChange = { new ->
                        MeshLocationPreferences.setProvideLocationToMesh(context.applicationContext, new)
                        provideLocationToMesh = new
                    },
                    onOpenLoRa = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        val a = deviceAddrOrFail() ?: return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.LoRa
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openLoRaSettingsAfterReady()
                    },
                    onOpenUserSettings = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        val a = deviceAddrOrFail() ?: return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.User
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openUserSettingsAfterReady()
                    },
                    onOpenChannels = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        val a = deviceAddrOrFail() ?: return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.Channels
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openChannelsSettingsAfterReady()
                    },
                    onOpenSecurity = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        val a = deviceAddrOrFail() ?: return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.Security
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openSecuritySettingsAfterReady()
                    },
                    onOpenDeviceSettings = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        val a = deviceAddrOrFail() ?: return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.Device
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openDeviceSettingsAfterReady()
                    },
                    onOpenMqtt = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        val a = deviceAddrOrFail() ?: return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.Mqtt
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openMqttSettingsAfterReady()
                    },
                    onOpenNotifications = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        openNotificationsSettingsAfterReady()
                    },
                    onOpenExternalNotifications = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        val a = deviceAddrOrFail() ?: return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.ExternalNotifications
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openExternalNotificationsSettingsAfterReady()
                    },
                    onOpenTelemetry = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        val a = deviceAddrOrFail() ?: return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.Telemetry
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openTelemetrySettingsAfterReady()
                    },
                    nodeConnected = bleConnected,
                    onOpenFirmwareUpdate = {
                        if (blockSettingsSubscreenOpen()) return@ChatSettingsTabContent
                        if (!nodeAppReady) {
                            pendingSettingsDestination = PendingSettingsDestination.Firmware
                            settingsGattWaitOverlay = true
                            return@ChatSettingsTabContent
                        }
                        openFirmwareSettingsAfterReady()
                    },
                    onOpenSerialConnection = {
                        meshConnDialogInitialTab = MeshConnectionInitialTab.Usb
                        showConnectionsDialog = true
                    },
                    onNodeConfigWriteSuccess = { markNodeConfigWriteSuccess() },
                    onOpenMessageHistory = { messageHistoryOpen = true },
                    onOpenMatrixSettings = { openMatrixSettingsScreen() },
                    onOpenAppCapabilitiesTutorial = onOpenFirstLaunchInstruction,
                    onOpenVipActivate = {
                        vipActivateOverlayOpen = true
                    },
                    showVipActivate = vipRestricted,
                )
            }
            }
        }
    }
    nodeSyncOverlay?.let { st ->
        SettingsNodeSyncOverlay(
            state = st,
            onDismissError = { nodeSyncOverlay = null },
        )
    }
    if (settingsGattWaitOverlay) {
        Dialog(
            onDismissRequest = { clearSettingsGattWait() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1A2E)),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Подключение ноды",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                            TextButton(onClick = { clearSettingsGattWait() }) {
                                Text("Отмена", color = MstSett.accent)
                            }
                        }
                        Text(
                            "Дождитесь статуса «Подключено» — затем откроется выбранный раздел.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                        )
                        GattConnectionSyncProgressSection(
                            syncStep = gattSyncStep,
                            connState = gattConnState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF071320))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            }
        }
    }
    }
    channelSecurityStatusFor?.let { ch ->
        ChannelSecurityStatusAlert(
            channel = ch,
            onDismiss = { channelSecurityStatusFor = null },
            onShowAllValues = {
                channelSecurityStatusFor = null
                channelSecurityLegendFor = ch
            },
        )
    }
    channelSecurityLegendFor?.let { ch ->
        ChannelSecurityLegendSheet(
            onDismiss = { channelSecurityLegendFor = null },
            onShowCurrentStatus = {
                channelSecurityLegendFor = null
                channelSecurityStatusFor = ch
            },
        )
    }
    if (messageHistoryOpen) {
        MessageHistoryScreen(
            meshNodeId = nodeId,
            onBack = { messageHistoryOpen = false },
        )
    }
    if (showChatWallpaperPicker) {
        ChatWallpaperPickerDialog(
            currentIndex = chatWallpaperIndex,
            onDismiss = { showChatWallpaperPicker = false },
            onSelect = { idx ->
                ChatWallpaperPreferences.setWallpaperIndex(context.applicationContext, idx)
                chatWallpaperIndex = idx
                showChatWallpaperPicker = false
            },
        )
    }
    bubbleMenuSession?.let { session ->
        MessageBubbleContextOverlay(
            state = session.state,
            onDismiss = { bubbleMenuSession = null },
            onReply = session.onReply,
            onCopyText = session.onCopyText,
            onReaction = session.onReaction,
            onPin = session.onPin,
            onDelete = session.onDelete,
        )
    }
    if ((vipRestricted && !vipOverlayDismissed) || vipActivateOverlayOpen) {
        VipExpiredOverlay(
            onPasswordAccepted = {
                vipOverlayDismissed = false
                vipActivateOverlayOpen = false
            },
            onDismiss = {
                vipOverlayDismissed = true
                vipActivateOverlayOpen = false
            },
        )
    }
        }
    }
}

// ── Вкладки ───────────────────────────────────────────────────────────────────

private fun conversationChannelTitle(ch: MeshStoredChannel, modemPreset: MeshWireModemPreset?): String =
    meshChannelDisplayTitle(ch, modemPreset)

@Composable
private fun ChannelSecurityStatusAlert(
    channel: MeshStoredChannel,
    onDismiss: () -> Unit,
    onShowAllValues: () -> Unit,
) {
    val ui = meshStoredChannelSecurityUi(channel)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text(
                "Безопасность канала",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    securityLockIcon(ui),
                    contentDescription = null,
                    tint = securityLockTint(ui),
                    modifier = Modifier.size(52.dp),
                )
                Text(
                    channelSecurityStatusText(ui),
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onShowAllValues) {
                Text("Показать все значения", color = MtActionGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отменить", color = MtActionGreen)
            }
        },
    )
}

@Composable
private fun SecurityLegendRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    body: String,
    extraLeading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            extraLeading?.invoke()
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSecurityLegendSheet(
    onDismiss: () -> Unit,
    onShowCurrentStatus: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MtChatSheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                "Значения безопасности канала",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            SecurityLegendRow(
                icon = Icons.Default.Lock,
                iconTint = MtLockGreen,
                title = "Безопасный",
                body = "Зелёный замок означает, что канал надёжно зашифрован либо 128-, либо 256-битным ключом AES.",
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MtDividerLight)
            SecurityLegendRow(
                icon = Icons.Outlined.LockOpen,
                iconTint = MtLockYellow,
                title = "Небезопасный канал, не точный",
                body = "Жёлтый открытый замок означает, что канал небезопасно зашифрован, не используется для " +
                    "точного определения местоположения и не использует ни один ключ вообще, ни один из известных байтовых ключей.",
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MtDividerLight)
            SecurityLegendRow(
                icon = Icons.Outlined.LockOpen,
                iconTint = MtLockRed,
                title = "Небезопасный канал, точное местоположение",
                body = "Красный открытый замок означает, что канал не зашифрован, используется для точного " +
                    "определения местоположения и не использует ни один ключ вообще, ни один байтовый известный ключ.",
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MtDividerLight)
            SecurityLegendRow(
                icon = Icons.Outlined.LockOpen,
                iconTint = MtLockRed,
                title = "Предупреждение: Небезопасно, точное местоположение; Uplink MQTT",
                body = "Красный открытый замок с предупреждением означает, что канал не зашифрован, используется " +
                    "для получения точных данных о местоположении, которые передаются через Интернет по MQTT, " +
                    "и не использует ни один ключ вообще, ни один байтовый известный ключ.",
                extraLeading = {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MtLockYellow,
                        modifier = Modifier.size(28.dp),
                    )
                },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onShowCurrentStatus) {
                Text("Показать текущий статус", color = MtActionGreen, fontSize = 14.sp)
            }
            TextButton(onClick = onDismiss) {
                Text("Отменить", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ChatTabContent(
    padding: PaddingValues,
    deviceAddress: String?,
    bleConnected: Boolean,
    nodeId: String,
    channelPreviewRefreshKey: Int,
    modemPreset: MeshWireModemPreset?,
    matrixBackdropActive: Boolean,
    selectedMapChannelIndex: Int?,
    /** Открыть личный чат с узлом (список «Личные сообщения»). */
    onOpenDirectThread: (MeshWireNodeSummary) -> Unit,
    onChannelClick: (MeshStoredChannel) -> Unit,
    onChannelSelectForMap: (MeshStoredChannel) -> Unit,
    onChannelLongPressClear: (MeshStoredChannel) -> Unit,
    /**
     * После очистки истории личного чата: [nodeNumCleared] — pир, чей тред сброшен; родитель скрывает беседу, если она открыта.
     */
    onDirectThreadHistoryCleared: (nodeNumCleared: Long) -> Unit = {},
    /** Из группы: закрыть группы и открыть карточку узла на вкладке «Узлы»; [openedFromGroupFolderId] — для «назад» в папку. */
    onRequestNodesTabForNode: (MeshWireNodeSummary, openedFromGroupFolderId: Long?) -> Unit = { _, _ -> },
    showGroupsScreen: Boolean,
    onShowGroupsScreenChange: (Boolean) -> Unit,
    groupsVm: GroupsViewModel,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AuraApplication
    val chatDao = remember { app.chatDatabase.channelChatMessageDao() }
    val latestAddr by rememberUpdatedState(deviceAddress?.trim()?.takeIf { it.isNotEmpty() })
    var channels by remember { mutableStateOf<List<MeshStoredChannel>>(emptyList()) }
    /** Порядок строк на главной (без записи на ноду); у каждого элемента прежний [MeshStoredChannel.index]. */
    var orderedChannelsMain by remember { mutableStateOf<List<MeshStoredChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val nodesVm: NodesTabViewModel = viewModel()
    val nodesUiState by nodesVm.uiState.collectAsState()

    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val macNormForDm = remember(addr) { addr?.let { MeshNodeSyncMemoryStore.normalizeKey(it) } }
    val dmThreadsFlow = remember(macNormForDm, chatDao) {
        if (macNormForDm == null) flowOf(emptyList())
        else chatDao.observeDmThreadSummaries(macNormForDm)
    }
    val dmThreads by dmThreadsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val historyNodeIdHex = remember(nodeId) {
        ChatHistoryFileStore.normalizeNodeIdHex(nodeId).ifEmpty { null }
    }
    var dmFilePreviewBump by remember { mutableIntStateOf(0) }
    var dmThreadsMerged by remember { mutableStateOf<List<DmThreadSummaryRow>>(emptyList()) }
    var pendingDirectThreadMergeExcludePeer by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(
        dmThreads,
        macNormForDm,
        historyNodeIdHex,
        channelPreviewRefreshKey,
        dmFilePreviewBump,
    ) {
        if (macNormForDm == null) {
            dmThreadsMerged = emptyList()
            return@LaunchedEffect
        }
        val excl = pendingDirectThreadMergeExcludePeer
        if (excl != null) {
            pendingDirectThreadMergeExcludePeer = null
        }
        val vLabel = context.getString(R.string.channel_preview_voice)
        val iLabel = context.getString(R.string.channel_preview_image)
        dmThreadsMerged = withContext(Dispatchers.IO) {
            mergeDmThreadSummariesWithFileLastMedia(
                context.applicationContext,
                macNormForDm,
                historyNodeIdHex,
                dmThreads,
                vLabel,
                iLabel,
                forceExcludeDirectPeer = excl,
            )
        }
    }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                MeshIncomingChatRepository.incomingVoice.collect { ev ->
                    if (ev.dmPeerNodeNum != null) dmFilePreviewBump++
                }
            }
            launch {
                MeshIncomingChatRepository.incomingDirectImages.collect {
                    dmFilePreviewBump++
                }
            }
            launch {
                MeshIncomingChatRepository.dmThreadListPreviewRefresh.collect {
                    dmFilePreviewBump++
                }
            }
        }
    }
    val localNodeIdHexForBubble = remember(nodeId) { normalizeNodeIdHexForBubbleColor(nodeId) }
    var clearDmPeerNum by remember { mutableStateOf<Long?>(null) }
    val clearDmScope = rememberCoroutineScope()

    LaunchedEffect(bleConnected) {
        nodesVm.setBleConnected(bleConnected)
    }
    LaunchedEffect(addr) {
        MeshNodeDbRepository.attachDevice(addr)
    }

    if (showGroupsScreen) {
        GroupsScreen(
            allNodes = nodesUiState.nodes,
            nowEpochSec = nodesUiState.nowEpochSec,
            vm = groupsVm,
            onBack = { onShowGroupsScreenChange(false) },
            onGroupMemberOpenProfile = { node, groupFolderId ->
                onShowGroupsScreenChange(false)
                onRequestNodesTabForNode(node, groupFolderId)
            },
            onGroupMemberOpenDirectMessage = { node ->
                onShowGroupsScreenChange(false)
                onOpenDirectThread(node)
            },
        )
        return
    }

    LaunchedEffect(addr, channelPreviewRefreshKey) {
        if (addr == null) {
            channels = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        MeshNodeSyncMemoryStore.getChannels(addr)?.let { cached ->
            loading = false
            error = null
            channels = cached.channels.map { it.copyForEdit() }
            return@LaunchedEffect
        }
        loading = true
        error = null
        channels = emptyList()
        val snapshotAddr = addr
        fetchMeshWireChannels(
            context.applicationContext,
            snapshotAddr,
            onSyncProgress = null,
        ) { result, err ->
            if (latestAddr != snapshotAddr) return@fetchMeshWireChannels
            loading = false
            if (result != null) {
                val stored = MeshNodeSyncMemoryStore.putChannels(snapshotAddr, result)
                channels = stored.channels.map { it.copyForEdit() }
                error = null
            } else {
                error = err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить каналы."
                channels = emptyList()
            }
        }
    }
    LaunchedEffect(channels) {
        if (channels.isEmpty()) {
            orderedChannelsMain = emptyList()
            return@LaunchedEffect
        }
        val prev = orderedChannelsMain
        if (prev.isEmpty()) {
            val savedOrder = ChatChannelOrderPreferences.getOrderedChannelIndexes(context, addr)
            val byIndex = channels.associateBy { it.index }
            val reordered = mutableListOf<MeshStoredChannel>()
            for (idx in savedOrder) {
                byIndex[idx]?.let { reordered.add(it.copyForEdit()) }
            }
            for (ch in channels.sortedBy { it.index }) {
                if (reordered.none { it.index == ch.index }) reordered.add(ch.copyForEdit())
            }
            orderedChannelsMain = reordered
            return@LaunchedEffect
        }
        val newByKey = channels.associateBy { it.rowKey }
        val merged = mutableListOf<MeshStoredChannel>()
        for (c in prev) {
            newByKey[c.rowKey]?.let { merged.add(it.copyForEdit()) }
        }
        val missing = channels.filter { ch -> merged.none { it.rowKey == ch.rowKey } }
        for (c in missing.sortedBy { it.index }) {
            merged.add(c.copyForEdit())
        }
        orderedChannelsMain = if (merged.size != channels.size) {
            channels.map { it.copyForEdit() }.sortedBy { it.index }
        } else {
            merged
        }
    }
    LaunchedEffect(channels, selectedMapChannelIndex, modemPreset) {
        if (channels.isEmpty()) return@LaunchedEffect
        val target = channels.firstOrNull { it.index == selectedMapChannelIndex }
            ?: channels.firstOrNull { it.index == 0 }
            ?: channels.firstOrNull()
            ?: return@LaunchedEffect
        onChannelSelectForMap(target)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .offset(y = (-75).dp),
    ) {
        clearDmPeerNum?.let { peerForClear ->
            val dmTitle = displayTitleForDmPeer(peerForClear, nodesUiState.nodes)
            AlertDialog(
                onDismissRequest = { clearDmPeerNum = null },
                containerColor = Color(0xFF0E1F33),
                titleContentColor = Color(0xFFE7FCFF),
                textContentColor = Color(0xFF8CB0BF),
                title = { Text("Очистить историю чата?") },
                text = {
                    Text(
                        "Сообщения с «$dmTitle» будут убраны из списка на этом устройстве. " +
                            "Копии остаются во внутреннем хранилище (архив личных чатов). Эфир и другие устройства не затрагиваются.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val mac = addr?.let { MeshNodeSyncMemoryStore.normalizeKey(it) }
                            val peer = peerForClear
                            clearDmPeerNum = null
                            if (mac != null) {
                                val idHex = historyNodeIdHex
                                val nodesSnap = nodesUiState.nodes
                                val peerFolder = ChatHistoryFileStore.directThreadFolderName(
                                    displayTitleForDmPeer(peer, nodesSnap),
                                    peer,
                                )
                                clearDmScope.launch(Dispatchers.IO) {
                                    val rows = chatDao.getAllForDirectPeerAsc(mac, peer)
                                    if (idHex != null) {
                                        ChatHistoryFileStore.ensureArchiveLayout(
                                            context.applicationContext,
                                            idHex,
                                        )
                                    }
                                    rows.groupBy { it.channelIndex }.forEach { (chIdx, groupRows) ->
                                        ChatHistoryFileStore.syncMissingDirectTextMessagesToArchive(
                                            context.applicationContext,
                                            mac,
                                            chIdx,
                                            peerFolder,
                                            groupRows,
                                            idHex,
                                        )
                                    }
                                    ChatHistoryFileStore.purgeAllDirectThreadMediaForHistoryClear(
                                        context.applicationContext,
                                        idHex,
                                        peerFolder,
                                    )
                                    chatDao.deleteAllForDirectPeer(mac, peer)
                                    withContext(Dispatchers.Main) {
                                        pendingDirectThreadMergeExcludePeer = peer and 0xFFFF_FFFFL
                                        onDirectThreadHistoryCleared(peer)
                                        dmFilePreviewBump++
                                    }
                                }
                            }
                        },
                    ) {
                        Text("Очистить", color = Color(0xFFFF6B6B))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clearDmPeerNum = null }) {
                        Text("Отмена", color = Color(0xFF8CB0BF))
                    }
                },
            )
        }
        when {
            addr == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D1A2E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color(0xFF1E3A5F),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Нет активных соединений",
                        color = Color(0xFF2A3A4A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Подключитесь к mesh-устройству\nчерез Bluetooth для начала общения",
                        color = Color(0xFF1E2D3A),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
            }
            loading && channels.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color(0xFF00D4FF))
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error!!,
                        color = Color(0xFFFF8A80),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            channels.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Каналы не найдены",
                        color = Color(0xFF2A3A4A),
                        fontSize = 15.sp,
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    ChatTabGroupsFolderRow(
                        onClick = { onShowGroupsScreenChange(true) },
                    )
                    DirectMessagesSection(
                        threads = dmThreadsMerged,
                        nodes = nodesUiState.nodes,
                        deviceAddress = addr,
                        onThreadClick = { peerNum ->
                            onOpenDirectThread(
                                meshtasticPeerSummaryForDm(peerNum, nodesUiState.nodes),
                            )
                        },
                        onThreadLongPress = { peerNum -> clearDmPeerNum = peerNum },
                    )
                    Text(
                        text = "Каналы",
                        color = Color(0xFF8CB0BF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
                    )
                    val hapticMain = LocalHapticFeedback.current
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        itemsIndexed(
                            orderedChannelsMain,
                            key = { _, ch -> ch.rowKey },
                        ) { index, ch ->
                            ConversationChannelRow(
                                channel = ch,
                                displayTitle = conversationChannelTitle(ch, modemPreset),
                                securityUi = meshStoredChannelSecurityUi(ch),
                                onClick = { onChannelClick(ch) },
                                onLongPressClear = { onChannelLongPressClear(ch) },
                                selectedForMap = false,
                                dao = chatDao,
                                notificationDeviceAddress = addr,
                                deviceMac = addr?.let { MeshNodeSyncMemoryStore.normalizeKey(it) },
                                historyNodeIdHex = historyNodeIdHex,
                                channelPreviewRefreshKey = channelPreviewRefreshKey,
                                reorderArrowsEnabled = true,
                                canMoveUp = index > 0,
                                canMoveDown = index < orderedChannelsMain.lastIndex,
                                onMoveUp = {
                                    if (index > 0) {
                                        val next = orderedChannelsMain.toMutableList().apply {
                                            val t = this[index]
                                            this[index] = this[index - 1]
                                            this[index - 1] = t
                                        }
                                        orderedChannelsMain = next
                                        ChatChannelOrderPreferences.setOrderedChannelIndexes(
                                            context,
                                            addr,
                                            next.map { it.index },
                                        )
                                        runCatching {
                                            hapticMain.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                },
                                onMoveDown = {
                                    if (index < orderedChannelsMain.lastIndex) {
                                        val next = orderedChannelsMain.toMutableList().apply {
                                            val t = this[index]
                                            this[index] = this[index + 1]
                                            this[index + 1] = t
                                        }
                                        orderedChannelsMain = next
                                        ChatChannelOrderPreferences.setOrderedChannelIndexes(
                                            context,
                                            addr,
                                            next.map { it.index },
                                        )
                                        runCatching {
                                            hapticMain.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTabGroupsFolderRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0A2036).copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color(0xFF42E6FF).copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF112033)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = Color(0xFF6DEBFF),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Группы",
                    color = Color(0xFFE7FCFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Управление группами контактов",
                    color = Color(0xFF8CB0BF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "›",
                color = Color(0xFF6DEBFF),
                fontSize = 22.sp,
            )
        }
    }
}

/**
 * Строка канала: [Card] с обводкой, [AssistChip] с индексом (канал 0 — акцентным цветом),
 * иконка безопасности, название.
 */
@Composable
private fun ConversationChannelRow(
    channel: MeshStoredChannel,
    displayTitle: String,
    securityUi: MeshChannelSecurityUi,
    onClick: () -> Unit,
    onLongPressClear: (() -> Unit)? = null,
    selectedForMap: Boolean = false,
    dao: com.example.aura.data.local.ChannelChatMessageDao? = null,
    notificationDeviceAddress: String? = null,
    deviceMac: String? = null,
    /** Как в [ChannelChatManager]: папка ChatHistory_<id>/channels/… */
    historyNodeIdHex: String? = null,
    /** Увеличивается после очистки истории — пересчитать превью из файлов. */
    channelPreviewRefreshKey: Int = 0,
    /** Стрелки слева (главная): только порядок строк, индексы каналов не меняются. */
    reorderArrowsEnabled: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
) {
    val accent = Color(0xFF00D4FF)
    val titleColor = if (channel.index == 0) accent else Color(0xFFDDE8F0)
    val context = LocalContext.current
    val muteAddress = notificationDeviceAddress ?: deviceMac
    var notificationsMuted by remember(muteAddress, channel.index) {
        mutableStateOf(
            ChatChannelNotificationPreferences.isChannelMuted(
                context,
                muteAddress,
                channel.index,
            ),
        )
    }
    val voiceLabel = stringResource(R.string.channel_preview_voice)
    val imageLabel = stringResource(R.string.channel_preview_image)
    val beaconPreviewLabel = stringResource(R.string.channel_preview_beacon)
    val pollVotePreviewLabel = stringResource(R.string.preview_poll_vote_recorded)
    val checklistTogglePreviewLabel = stringResource(R.string.preview_checklist_choice_made)

    val lastChannelRoomMessageFlow = remember(dao, deviceMac, channel.index) {
        if (dao != null && deviceMac != null) dao.observeLastMessage(deviceMac, channel.index)
        else flowOf(null)
    }
    val lastChannelRoomMessage by lastChannelRoomMessageFlow.collectAsStateWithLifecycle(
        initialValue = null,
    )

    var fileLastMediaPreview by remember(deviceMac, channel.index) {
        mutableStateOf<Pair<Long, String>?>(null)
    }
    LaunchedEffect(deviceMac, channel.index, voiceLabel, imageLabel, historyNodeIdHex, channelPreviewRefreshKey) {
        val mac = deviceMac ?: return@LaunchedEffect
        suspend fun refreshFromFiles() {
                fileLastMediaPreview = withContext(Dispatchers.IO) {
                val voices = ChatHistoryFileStore.loadVoiceAttachments(
                    context.applicationContext,
                    mac,
                    channel.index,
                    historyNodeIdHex,
                )
                val imgs = ChatHistoryFileStore.loadImageAttachments(
                    context.applicationContext,
                    mac,
                    channel.index,
                    historyNodeIdHex,
                )
                val vb = voices.maxByOrNull { it.timeMs }?.let { it.timeMs to voiceLabel }
                val ib = imgs.maxByOrNull { it.timeMs }?.let { it.timeMs to imageLabel }
                when {
                    vb == null -> ib
                    ib == null -> vb
                    else -> if (vb.first >= ib.first) vb else ib
                }
            }
        }
        refreshFromFiles()
        launch {
            MeshIncomingChatRepository.incomingVoice.collect { ev ->
                if (ev.deviceMacNorm == mac && ev.channelIndex == channel.index) {
                    refreshFromFiles()
                }
            }
        }
    }

    val unreadFlow = remember(dao, deviceMac, channel.index) {
        if (dao != null && deviceMac != null) dao.observeUnreadCount(deviceMac, channel.index)
        else flowOf(0)
    }
    val unreadCount by unreadFlow.collectAsStateWithLifecycle(initialValue = 0)

    val previewPair: Pair<Long, String>? = run {
        val roomC = lastChannelRoomMessage?.let { msg ->
            val body = normalizeChatPreviewText(
                msg.text.replace('\n', ' ').trim(),
                beaconPreviewLabel,
                pollVotePreviewLabel,
                checklistTogglePreviewLabel,
            )
            if (body.isEmpty()) null else msg.createdAtMs to body
        }
        val fileC = fileLastMediaPreview
        when {
            roomC == null -> fileC
            fileC == null -> roomC
            else -> if (roomC.first >= fileC.first) roomC else fileC
        }
    }
    val lastText = previewPair?.second
    val lastTime = previewPair?.first?.let { ms ->
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    }
    val haptic = LocalHapticFeedback.current

    val clickModifier = Modifier.combinedClickable(
        onClick = onClick,
        onLongClick = {
            onLongPressClear?.invoke()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
    )

    val arrowTintActive = Color(0xFF00D4FF)
    val arrowTintMuted = Color(0xFF4A5A6A).copy(alpha = 0.45f)

    @Composable
    fun channelCard(cardOuterModifier: Modifier) {
        Card(
            modifier = cardOuterModifier,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            border = if (selectedForMap) {
                BorderStroke(1.6.dp, Color(0xFF4AF263))
            } else {
                CardDefaults.outlinedCardBorder()
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            AssistChip(
                onClick = onClick,
                label = {
                    Text(
                        text = channel.index.toString(),
                        color = titleColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF0D1A2E),
                    labelColor = titleColor,
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = Color(0xFF2A3A4A),
                ),
            )
            Icon(
                imageVector = securityLockIcon(securityUi),
                contentDescription = null,
                tint = securityLockTint(securityUi),
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
            Text(
                text = displayTitle,
                color = titleColor,
                fontSize = 16.sp,
                fontWeight = if (channel.index == 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (lastTime != null) {
                        Text(
                            text = lastTime,
                            color = Color(0xFF4A5A6A),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = !notificationsMuted
                            notificationsMuted = next
                            ChatChannelNotificationPreferences.setChannelMuted(
                                context,
                                muteAddress,
                                channel.index,
                                next,
                            )
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = if (notificationsMuted) {
                                Icons.Filled.NotificationsOff
                            } else {
                                Icons.Filled.Notifications
                            },
                            contentDescription = if (notificationsMuted) {
                                "Включить уведомления канала"
                            } else {
                                "Отключить уведомления канала"
                            },
                            tint = if (notificationsMuted) {
                                Color(0xFFFF8A80)
                            } else {
                                Color(0xFF7DA8C6)
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (lastText != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lastText,
                            color = Color(0xFF6A7A8A),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 22.dp)
                                    .widthIn(min = 22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (reorderArrowsEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 2.dp),
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Выше",
                        tint = if (canMoveUp) arrowTintActive else arrowTintMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Ниже",
                        tint = if (canMoveDown) arrowTintActive else arrowTintMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            channelCard(
                Modifier
                    .weight(1f)
                    .then(clickModifier),
            )
        }
    } else {
        channelCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .then(clickModifier),
        )
    }
}

private sealed class ChannelBubbleItem {
    abstract val stableId: String
    abstract val from: UInt?
    abstract val mine: Boolean
    abstract val timeMs: Long

    data class TextMsg(
        override val stableId: String,
        override val from: UInt?,
        val text: String,
        override val mine: Boolean,
        val relayHops: Int? = null,
        val viaMqtt: Boolean = false,
        override val timeMs: Long,
        /** Room id для повтора исходящего после FAILED. */
        val rowId: Long,
        val outgoingDelivery: ChatMessageDeliveryStatus? = null,
        /** MeshPacket.id на эфире — нужен для tapback. */
        val meshPacketId: UInt? = null,
        val replyToPacketId: UInt? = null,
        val replyToFromNodeNum: Long? = null,
        val replyPreviewText: String? = null,
        /** Сгруппированные реакции для чипов (см. [GroupedReactionUi]). */
        val reactionChips: List<GroupedReactionUi> = emptyList(),
    ) : ChannelBubbleItem()

    data class ImageMsg(
        override val stableId: String,
        override val from: UInt?,
        val jpeg: ByteArray,
        override val mine: Boolean,
        val relayHops: Int? = null,
        val viaMqtt: Boolean = false,
        override val timeMs: Long,
        val meshPacketId: UInt? = null,
    ) : ChannelBubbleItem()

    data class VoiceMsg(
        override val stableId: String,
        override val from: UInt?,
        val attachment: ChannelVoiceAttachment,
        override val mine: Boolean,
        override val timeMs: Long,
        val outgoingDelivery: ChatMessageDeliveryStatus? = null,
        val meshPacketId: UInt? = null,
        val reactionChips: List<GroupedReactionUi> = emptyList(),
    ) : ChannelBubbleItem()
}

private sealed class MessageBubbleMenuTarget {
    data class Text(val item: ChannelBubbleItem.TextMsg) : MessageBubbleMenuTarget()
    data class Image(val item: ChannelBubbleItem.ImageMsg) : MessageBubbleMenuTarget()
    data class Voice(val item: ChannelBubbleItem.VoiceMsg) : MessageBubbleMenuTarget()
}

private data class MessageBubbleMenuState(
    val target: MessageBubbleMenuTarget,
)

/** Колбэки меню по зажатию сообщения — поднимаются в [ChatScreen], чтобы оверлей перекрывал весь Scaffold. */
private data class BubbleMenuSession(
    val state: MessageBubbleMenuState,
    val onReply: () -> Unit,
    val onCopyText: () -> Unit,
    val onReaction: (emojiListIndex: Int) -> Unit,
    val onPin: () -> Unit,
    val onDelete: () -> Unit,
)

/** Меню и полоса реакций — в 2 раза меньше базовых размеров. */
private const val ContextMenuVisualScale = 0.5f

/** Множитель визуального масштаба полосы реакций (2× к прежнему отображению). */
private const val ContextMenuReactionScaleMul = 2f

/** Множитель для нижнего выпадающего меню (ширина и строки); +10% к прежним 130%. */
private const val ContextMenuDropdownScale = 1.3f * 1.1f

/** Радиус размытия фона: 80% от шкалы (макс. 100px). */
private const val ContextMenuBlurRadiusPx = 100f * 0.8f
private const val VoiceRecordMaxDurationMs = 3_000L
private const val LocalGifMessagePrefix = "GIF_LOCAL:"
private const val PollMessagePrefix = "📊 ОПРОС"
private const val ChecklistMessagePrefix = "🧾 СПИСОК"
private const val ChecklistUpdatePrefix = "🧾U"
private const val UrgentMeshFirstLine = "⚡URGENT"
private const val UrgentAckMeshFirstLine = "⚡ACK"

/** Префикс id в ⚡ACK для подтверждения голосового (не пересекается с id срочных UUID). */
private const val VoiceReceiptAckIdPrefix = "voice:"

private data class UrgentMeshPayload(val id: String, val body: String)

private data class UrgentAckMeshPayload(val id: String, val timeMs: Long)

/** Одно нажатие «галочки» получателя по срочному сообщению (агрегируется из строк ⚡ACK). */
private data class UrgentAckEntry(val fromNodeNum: Long, val timeMs: Long)

private fun buildUrgentMeshMessage(id: String, body: String): String = buildString {
    append(UrgentMeshFirstLine)
    append('\n')
    append("id:")
    append(id.trim())
    append('\n')
    append(body.trim())
}

private fun parseUrgentMeshMessage(text: String): UrgentMeshPayload? {
    val lines = text.trimEnd().split('\n').map { it.trimEnd() }
    if (lines.size < 3) return null
    if (!lines[0].equals(UrgentMeshFirstLine, ignoreCase = true)) return null
    val idLine = lines[1].trim()
    if (!idLine.startsWith("id:", ignoreCase = true)) return null
    val id = idLine.substringAfter(":", "").trim()
    if (id.isBlank()) return null
    val body = lines.drop(2).joinToString("\n").trimEnd()
    return UrgentMeshPayload(id = id, body = body)
}

private fun buildUrgentAckMeshMessage(id: String, timeMs: Long = System.currentTimeMillis()): String = buildString {
    append(UrgentAckMeshFirstLine)
    append('\n')
    append("id:")
    append(id.trim())
    append('\n')
    append("t:")
    append(timeMs)
}

private fun parseUrgentAckMeshMessage(text: String): UrgentAckMeshPayload? {
    val lines = text.trim().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 2) return null
    if (!lines[0].equals(UrgentAckMeshFirstLine, ignoreCase = true)) return null
    val idLine = lines.getOrNull(1) ?: return null
    if (!idLine.startsWith("id:", ignoreCase = true)) return null
    val id = idLine.substringAfter(":", "").trim()
    if (id.isBlank()) return null
    val tLine = lines.getOrNull(2)?.trim().orEmpty()
    val timeMs = if (tLine.startsWith("t:", ignoreCase = true)) {
        tLine.substringAfter(":", "").trim().toLongOrNull() ?: System.currentTimeMillis()
    } else {
        System.currentTimeMillis()
    }
    return UrgentAckMeshPayload(id = id, timeMs = timeMs)
}

/** id срочного сообщения → список подтверждений (последнее время на узел). */
private fun buildUrgentAckSnapshots(rows: List<ChannelChatMessageEntity>): Map<String, List<UrgentAckEntry>> {
    val byId = mutableMapOf<String, MutableMap<Long, Long>>()
    rows.sortedBy { it.createdAtMs }.forEach { row ->
        val ack = parseUrgentAckMeshMessage(row.text) ?: return@forEach
        val from = row.fromNodeNum and 0xFFFF_FFFFL
        val m = byId.getOrPut(ack.id) { mutableMapOf() }
        m[from] = ack.timeMs
    }
    return byId.mapValues { (_, nodeToTime) ->
        nodeToTime.entries
            .map { (node, t) -> UrgentAckEntry(fromNodeNum = node, timeMs = t) }
            .sortedWith(
                compareByDescending<UrgentAckEntry> { it.timeMs }
                    .thenBy { it.fromNodeNum and 0xFFFF_FFFFL },
            )
    }
}

private fun parseVoiceRecordIdFromStableId(stableId: String): UInt? {
    if (stableId.startsWith("v_out_")) {
        val rest = stableId.removePrefix("v_out_")
        return rest.substringBefore('_').toUIntOrNull()
    }
    if (stableId.startsWith("v_")) {
        val rest = stableId.removePrefix("v_")
        val parts = rest.split('_')
        if (parts.size >= 2) return parts[1].toUIntOrNull()
    }
    return null
}

/** Id для строки ⚡ACK (тот же парсер, что у срочных). */
private fun voiceReceiptAckId(attach: ChannelVoiceAttachment): String? {
    val sender = attach.from ?: return null
    val rid = if (attach.voiceRecordId != 0u) {
        attach.voiceRecordId
    } else {
        parseVoiceRecordIdFromStableId(attach.stableId) ?: return null
    }
    return "${VoiceReceiptAckIdPrefix}${sender.toLong() and 0xFFFFFFFFL}_${rid.toLong() and 0xFFFFFFFFL}"
}

private fun normalizeChatPreviewText(
    raw: String,
    beaconPreviewLabel: String,
    pollVotePreviewLabel: String,
    checklistTogglePreviewLabel: String,
): String = IncomingMessagePreviewFormatter.previewLabel(
    raw,
    beaconPreviewLabel,
    pollVotePreviewLabel,
    checklistTogglePreviewLabel,
)

@Composable
private fun BubbleMenuRow(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    compact: Boolean = false,
    // при compact: множитель высоты/шрифта строк (напр. меню по зажатию)
    compactScale: Float = 1f,
) {
    val rowH = if (compact) (24 * compactScale).dp else 48.dp
    val padH = if (compact) (8 * compactScale).dp else 16.dp
    val fs = if (compact) (8 * compactScale).sp else 16.sp
    val iconSz = if (compact) (11 * compactScale).dp else 22.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowH)
            .clickable(onClick = onClick)
            .padding(horizontal = padH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = tint, fontSize = fs, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSz))
    }
}

@Composable
private fun MessageBubbleMenuPreview(target: MessageBubbleMenuTarget) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    when (target) {
        is MessageBubbleMenuTarget.Text -> {
            val msg = target.item
            val theme = channelBubbleTheme(nodeIdHexFromNodeNum(msg.from))
            val tLabel = timeFmt.format(Date(msg.timeMs))
            val menuBody = remember(msg.text) { parseUrgentMeshMessage(msg.text)?.body ?: msg.text }
            Surface(
                shape = channelBubbleShape(msg.mine),
                color = theme.bg,
                shadowElevation = 4.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = menuBody,
                        color = theme.body,
                        fontSize = (15f * 0.8f).sp,
                        lineHeight = (20f * 0.8f).sp,
                        fontWeight = FontWeight.ExtraLight,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (msg.viaMqtt) {
                            Icon(
                                Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = theme.mqttTint,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = tLabel,
                            color = theme.timeMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
        is MessageBubbleMenuTarget.Image -> {
            val msg = target.item
            val theme = channelBubbleTheme(nodeIdHexFromNodeNum(msg.from))
            val bmp = remember(msg.jpeg) {
                BitmapFactory.decodeByteArray(msg.jpeg, 0, msg.jpeg.size)
            }
            Surface(
                shape = channelBubbleShape(msg.mine),
                color = theme.bg,
                shadowElevation = 4.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Column(Modifier.padding(8.dp)) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .heightIn(max = 180.dp)
                                .widthIn(max = 260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Text("Фото", color = theme.body.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }
        }
        is MessageBubbleMenuTarget.Voice -> {
            val msg = target.item
            val a = msg.attachment
            val theme = channelBubbleTheme(nodeIdHexFromNodeNum(msg.from))
            val tLabel = timeFmt.format(Date(msg.timeMs))
            Surface(
                shape = channelBubbleShape(msg.mine),
                color = theme.bg,
                shadowElevation = 4.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.widthIn(min = 160.dp, max = 280.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = theme.iconOnBubble,
                        modifier = Modifier.size(32.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Голос · ${a.durationMs / 1000f}s",
                            color = theme.body.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                        )
                        Text(
                            tLabel,
                            color = theme.timeMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelPinnedTelegramBar(
    pinned: ChannelPinnedSnapshot,
    authorText: String,
    onNavigateToMessage: () -> Unit,
    onUnpin: () -> Unit,
) {
    val pinnedBarShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(pinnedBarShape)
            .background(Color(0xFF182533)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNavigateToMessage,
                )
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = null,
                tint = Color(0xFF33A0E8),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = authorText.ifBlank { " " },
                    color = Color(0xFF33A0E8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pinned.snippet,
                    color = Color(0xFF8A9A8E),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onUnpin) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = Color(0xFF8A9A8E),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MessageBubbleContextOverlay(
    state: MessageBubbleMenuState,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopyText: () -> Unit,
    onReaction: (emojiListIndex: Int) -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    var fullReactionPickerExpanded by remember { mutableStateOf(false) }
    val s = ContextMenuVisualScale
    val d = ContextMenuDropdownScale
    val menuW = 280.dp * s * d
    val reactionShape = RoundedCornerShape(28.dp * s)
    val menuShape = RoundedCornerShape(12.dp * s * d)

    // Отдельное окно + FLAG_BLUR_BEHIND: размывается контент активити под диалогом.
    // Compose BlurEffect на пустом Box размывает только пустой слой — визуально не видно.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    win.attributes = win.attributes.also { attrs ->
                        attrs.blurBehindRadius = ContextMenuBlurRadiusPx.toInt().coerceIn(1, 200)
                    }
                }
                win.setDimAmount(0f)
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Реакции: базовый s × ContextMenuReactionScaleMul (в 2 раза крупнее прежнего)
            val reactionLayerScale = s * ContextMenuReactionScaleMul
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = reactionLayerScale
                    scaleY = reactionLayerScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(Color(0xFF2C2C2E), reactionShape)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val quickEnd = minOf(
                                MeshReactionEmojiRegistry.QUICK_EMOJI_COUNT,
                                MeshReactionEmojiRegistry.ALL_EMOJIS.size,
                            )
                            for (index in 0 until quickEnd) {
                                val emoji = MeshReactionEmojiRegistry.ALL_EMOJIS[index]
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            onReaction(index)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(emoji, fontSize = 24.sp)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        fullReactionPickerExpanded = !fullReactionPickerExpanded
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            rotationZ = if (fullReactionPickerExpanded) 180f else 0f
                                        },
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = fullReactionPickerExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .heightIn(max = 240.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1C1C1E),
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                            ) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(8),
                                    contentPadding = PaddingValues(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.heightIn(max = 232.dp),
                                ) {
                                    itemsIndexed(MeshReactionEmojiRegistry.ALL_EMOJIS) { index, emoji ->
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    onReaction(index)
                                                    onDismiss()
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(emoji, fontSize = 20.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MessageBubbleMenuPreview(state.target)

            // Выпадающее меню снизу: к правому краю; масштаб ContextMenuDropdownScale от половинной базы
            Box(Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(menuW),
                    shape = menuShape,
                    color = Color(0xFF2C2C2E),
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp * s * d,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        BubbleMenuRow(
                            stringResource(R.string.msg_menu_reply),
                            Icons.AutoMirrored.Filled.Reply,
                            Color.White,
                            compact = true,
                            compactScale = d,
                            onClick = {
                                onReply()
                                onDismiss()
                            },
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = (0.5f * d).dp,
                        )
                        BubbleMenuRow(
                            stringResource(R.string.msg_menu_pin),
                            Icons.Filled.PushPin,
                            Color.White,
                            compact = true,
                            compactScale = d,
                            onClick = {
                                onPin()
                                onDismiss()
                            },
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = (0.5f * d).dp,
                        )
                        BubbleMenuRow(
                            stringResource(R.string.msg_menu_copy),
                            Icons.Outlined.ContentCopy,
                            Color.White,
                            compact = true,
                            compactScale = d,
                            onClick = {
                                onCopyText()
                                onDismiss()
                            },
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = (0.5f * d).dp,
                        )
                        BubbleMenuRow(
                            stringResource(R.string.msg_menu_delete),
                            Icons.Outlined.Delete,
                            Color(0xFFFF6B6B),
                            compact = true,
                            compactScale = d,
                            onClick = {
                                onDelete()
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
        }
    }
}

private fun ChannelChatMessageEntity.toTextBubble(localNodeNum: UInt?): ChannelBubbleItem.TextMsg {
    val fromU = (fromNodeNum and 0xFFFFFFFFL).toUInt()
    val mySenderId = localNodeNum?.toLong()?.and(0xFFFF_FFFFL)?.toString()
    return ChannelBubbleItem.TextMsg(
        stableId = "db_$id",
        from = fromU,
        text = text,
        mine = isOutgoing,
        relayHops = relayHops,
        viaMqtt = viaMqtt,
        timeMs = createdAtMs,
        rowId = id,
        outgoingDelivery = if (isOutgoing) {
            ChatMessageDeliveryStatus.forOutgoingTicks(deliveryStatus)
        } else {
            null
        },
        meshPacketId = meshPacketId?.let { (it and 0xFFFF_FFFFL).toUInt() },
        replyToPacketId = replyToPacketId?.let { (it and 0xFFFF_FFFFL).toUInt() },
        replyToFromNodeNum = replyToFromNodeNum,
        replyPreviewText = replyPreviewText,
        reactionChips = ChatMessageReactionsJson.reactionPresentationChips(reactionsJson, mySenderId),
    )
}

private fun ChannelImageAttachment.toImageBubble(): ChannelBubbleItem.ImageMsg =
    ChannelBubbleItem.ImageMsg(
        stableId = stableId,
        from = from,
        jpeg = jpeg,
        mine = mine,
        timeMs = timeMs,
    )

private fun ChannelVoiceAttachment.toVoiceBubble(localNodeNum: UInt?): ChannelBubbleItem.VoiceMsg {
    val mySenderId = localNodeNum?.toLong()?.and(0xFFFF_FFFFL)?.toString()
    return ChannelBubbleItem.VoiceMsg(
        stableId = stableId,
        from = from,
        attachment = this,
        mine = mine,
        timeMs = timeMs,
        outgoingDelivery = if (mine) {
            ChatMessageDeliveryStatus.forOutgoingTicks(deliveryStatus)
        } else {
            null
        },
        meshPacketId = meshPacketId,
        reactionChips = ChatMessageReactionsJson.reactionPresentationChips(reactionsJson, mySenderId),
    )
}

/** Виртуальные заголовки даты + сообщения для [LazyColumn]. */
private sealed class ChannelChatListRow {
    data class DateHeader(val day: LocalDate) : ChannelChatListRow()
    data class Message(val bubble: ChannelBubbleItem) : ChannelChatListRow()
}

/**
 * Вставляет [ChannelChatListRow.DateHeader] перед первым сообщением каждого календарного дня
 * ([LocalDate] в системной зоне [ZoneId.systemDefault]).
 */
private fun insertChatDateHeaders(messages: List<ChannelBubbleItem>): List<ChannelChatListRow> {
    if (messages.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val sorted = messages.sortedBy { it.timeMs }
    val out = ArrayList<ChannelChatListRow>()
    var prevDay: LocalDate? = null
    for (m in sorted) {
        val day = Instant.ofEpochMilli(m.timeMs).atZone(zone).toLocalDate()
        if (prevDay == null || day != prevDay) {
            out.add(ChannelChatListRow.DateHeader(day))
            prevDay = day
        }
        out.add(ChannelChatListRow.Message(m))
    }
    return out
}

/** Индекс строки в плоском списке (заголовки дат + сообщения). */
private fun flatListIndexForStableId(
    rows: List<ChannelChatListRow>,
    stableId: String,
): Int {
    rows.forEachIndexed { index, row ->
        if (row is ChannelChatListRow.Message && row.bubble.stableId == stableId) {
            return index
        }
    }
    return -1
}

@Composable
private fun DateSeparator(
    date: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun chatDayLabel(day: LocalDate): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val locale = Locale.getDefault()
    val todayStr = stringResource(R.string.chat_date_today)
    val yesterdayStr = stringResource(R.string.chat_date_yesterday)
    return remember(day, today, locale, todayStr, yesterdayStr) {
        when {
            day == today -> todayStr
            day == today.minusDays(1) -> yesterdayStr
            day.year != today.year -> DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(day)
            else -> DateTimeFormatter.ofPattern("d MMMM", locale).format(day)
        }
    }
}

/**
 * В канальном чате при первом открытии шлём read-receipts только по последним N входящим,
 * чтобы не заспамить эфир (LoRa — узкая полоса). Все новые сообщения, приходящие далее в эту
 * же сессию, получают квитанцию как обычно.
 */
private const val CHANNEL_READ_RECEIPT_BACKFILL_LIMIT = 20

/** Серая — с моей ноды; две синие — квитанция «Прочитано» от Aura в личке или в канале (первый прочитавший). */
private val OutgoingTickSentGray = Color(0xFFB0BEC5)
private val OutgoingTickDeliveredBlue = Color(0xFF34B7F1)

/** Галочки статуса доставки: на 30% меньше базового размера. */
private const val OutgoingTickScale = 0.7f

@Composable
private fun OutgoingDeliveryTicks(
    status: ChatMessageDeliveryStatus?,
    onRetry: (() -> Unit)? = null,
) {
    if (status == null) return
    val s = OutgoingTickScale
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp * s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            ChatMessageDeliveryStatus.SENDING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp * s),
                    strokeWidth = 2.dp * s,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            ChatMessageDeliveryStatus.SENT_TO_NODE,
            ChatMessageDeliveryStatus.DELIVERED_TO_NODE, // миграция / легаси в forOutgoingTicks → SENT; оставляем ветку на всякий
            -> {
                Icon(
                    Icons.Default.Done,
                    contentDescription = "Отправлено с моей ноды",
                    tint = OutgoingTickSentGray,
                    modifier = Modifier.size(18.dp * s),
                )
            }
            ChatMessageDeliveryStatus.READ_IN_PEER_APP -> {
                Box(
                    modifier = Modifier
                        .width(26.dp * s)
                        .height(18.dp * s),
                ) {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = null,
                        tint = OutgoingTickDeliveredBlue,
                        modifier = Modifier
                            .size(18.dp * s)
                            .align(Alignment.CenterStart),
                    )
                    Icon(
                        Icons.Default.Done,
                        contentDescription = "Собеседник прочитал в своём Aura-Mesh",
                        tint = OutgoingTickDeliveredBlue,
                        modifier = Modifier
                            .size(18.dp * s)
                            .align(Alignment.CenterStart)
                            .offset(x = 7.dp * s),
                    )
                }
            }
            ChatMessageDeliveryStatus.FAILED -> {
                if (onRetry != null) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Повторить отправку",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier
                            .size(18.dp * s)
                            .clickable(onClick = onRetry),
                    )
                } else {
                    Text("!", color = Color(0xFFFF8A80), fontSize = (14f * s).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChannelShortNameBubble(
    mine: Boolean,
    shortBadge: String,
    senderNodeIdHex: String? = null,
    urgentTint: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    val maxShortNameDp = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * 0.42f).dp
    }
    val nodeTheme = channelBubbleTheme(senderNodeIdHex)
    val shortNameStyle = if (urgentTint) {
        TextStyle(
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    } else {
        TextStyle(
            color = nodeTheme.body,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    val badgeBg = when {
        urgentTint && mine -> Color(0xFF8B2020)
        urgentTint && !mine -> Color(0xFF7A2528)
        else -> nodeTheme.bg
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = badgeBg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = shortBadge.ifBlank { "load" },
            style = shortNameStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        modifier = Modifier
                .widthIn(max = maxShortNameDp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Если на последней строке полной ширины не хватает места под время+галочки (с зазором),
 * или при узкой колонке под текст+футер появляется больше строк — время на отдельной строке (Telegram).
 */
private fun channelBubbleTelegramStackedTime(
    textMeasurer: TextMeasurer,
    text: String,
    bodyStyle: TextStyle,
    bubbleInnerMaxPx: Int,
    footerTrailingPx: Int,
    gapPx: Int,
): Boolean {
    if (text.isBlank()) return false
    val layoutFull = textMeasurer.measure(
        AnnotatedString(text),
        style = bodyStyle,
        constraints = Constraints(maxWidth = bubbleInnerMaxPx),
    )
    // Несколько строк: время и галочки только под текстом — иначе Row + IntrinsicSize даёт кривую вёрстку.
    if (layoutFull.lineCount >= 2) return true
    val textMaxPx = (bubbleInnerMaxPx - footerTrailingPx - gapPx).coerceAtLeast(1)
    val layoutNarrow = textMeasurer.measure(
        AnnotatedString(text),
        style = bodyStyle,
        constraints = Constraints(maxWidth = textMaxPx),
    )
    if (layoutNarrow.lineCount > layoutFull.lineCount) return true
    if (layoutFull.lineCount == 0) return false
    val last = layoutFull.lineCount - 1
    val lineRight = ceil(layoutFull.getLineRight(last).toDouble()).toInt()
    val spaceAfterText = (bubbleInnerMaxPx - lineRight).coerceAtLeast(0)
    return spaceAfterText < footerTrailingPx + gapPx
}

/** Минимальная внутренняя ширина под строку времени (одна линия), MQTT и при необходимости галочки доставки. */
private fun channelBubbleTimeFooterMinInnerPx(
    textMeasurer: TextMeasurer,
    timeLabel: String,
    timeStyle: TextStyle,
    viaMqtt: Boolean,
    density: Density,
    maxInnerPx: Int,
    /** Доп. место под OutgoingDeliveryTicks + Spacer (как в голосовом пузыре). */
    extraTrailingPx: Int = 0,
): Int {
    val timePx = textMeasurer.measure(
        AnnotatedString(timeLabel),
        style = timeStyle,
        constraints = Constraints(maxWidth = maxInnerPx),
        maxLines = 1,
        softWrap = false,
    ).size.width
    val mqttExtra = if (viaMqtt) {
        with(density) { 14.dp.roundToPx() + 4.dp.roundToPx() }
    } else {
        0
    }
    return (mqttExtra + timePx + extraTrailingPx).coerceIn(1, maxInnerPx)
}

@Composable
private fun ReactionChipBadge(
    chip: GroupedReactionUi,
    messageFontScale: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    incomingLightBubble: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val countAnimated by animateIntAsState(
        targetValue = chip.count,
        animationSpec = tween(durationMillis = 160),
        label = "reaction_count",
    )
    val borderColor = if (incomingLightBubble) {
        if (chip.includesMine) Color(0xFF1A1C1E).copy(alpha = 0.4f) else Color.Transparent
    } else if (chip.includesMine) {
        Color.White.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
        Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (incomingLightBubble) {
            if (chip.includesMine) Color(0xFF1A1C1E).copy(alpha = 0.12f) else Color(0xFF1A1C1E).copy(alpha = 0.06f)
        } else if (chip.includesMine) {
            Color.White.copy(alpha = 0.26f)
        } else {
            Color.White.copy(alpha = 0.12f)
        },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(if (chip.includesMine) 1.dp else 0.dp, borderColor),
        modifier = Modifier
            .padding(end = 4.dp, top = 2.dp)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                text = chip.emoji,
                fontSize = (12f * messageFontScale).sp,
                lineHeight = (14f * messageFontScale).sp,
                            maxLines = 1,
                        )
            if (countAnimated > 1) {
                    Text(
                    text = countAnimated.toString(),
                    color = if (incomingLightBubble) {
                        Color(0xFF1A1C1E).copy(alpha = 0.85f)
                    } else {
                        Color.White.copy(alpha = 0.85f)
                    },
                    fontSize = (10f * messageFontScale).sp,
                        fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelBubbleReactionRow(
    chips: List<GroupedReactionUi>,
    messageFontScale: Float,
    modifier: Modifier = Modifier,
    onChipClick: ((GroupedReactionUi) -> Unit)? = null,
    incomingLightBubble: Boolean = false,
) {
    AnimatedContent(
        targetState = chips,
        transitionSpec = {
            fadeIn(animationSpec = tween(140)) togetherWith fadeOut(animationSpec = tween(220))
        },
        label = "reaction_chips",
    ) { target ->
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = Int.MAX_VALUE,
        ) {
            target.forEach { chip ->
                ReactionChipBadge(
                    chip = chip,
                    messageFontScale = messageFontScale,
                    enabled = onChipClick != null,
                    onClick = { onChipClick?.invoke(chip) },
                    incomingLightBubble = incomingLightBubble,
                )
            }
        }
    }
}

@Composable
private fun ChannelBubbleTimeFooterTrailing(
    viaMqtt: Boolean,
    timeLabel: String,
    mine: Boolean,
    outgoingDelivery: ChatMessageDeliveryStatus?,
    onRetryFailedDelivery: (() -> Unit)?,
    channelTimeStyle: TextStyle,
    mqttTint: Color = Color.White.copy(alpha = 0.65f),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
                    if (viaMqtt) {
                        Icon(
                            Icons.Outlined.Cloud,
                            contentDescription = "MQTT",
                tint = mqttTint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = timeLabel,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = channelTimeStyle,
        )
        if (mine && outgoingDelivery != null) {
            Spacer(Modifier.width(6.dp))
            OutgoingDeliveryTicks(
                outgoingDelivery,
                onRetry = if (outgoingDelivery == ChatMessageDeliveryStatus.FAILED) {
                    onRetryFailedDelivery
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ChannelBubbleTelegramReplyQuote(
    authorName: String?,
    snippet: String,
    mine: Boolean,
    messageFontScale: Float,
    theme: ChannelBubbleTheme,
) {
    val accent = if (mine) TelegramReplyAccentOutgoingBubble else TelegramReplyAccentIncomingBubble
    val snippetColor = theme.body.copy(alpha = 0.62f)
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
    ) {
        Box(
                                modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Column(
            modifier = Modifier.padding(start = 8.dp),
        ) {
            if (!authorName.isNullOrBlank()) {
                                    Text(
                    text = authorName,
                    color = accent,
                    fontSize = (14f * messageFontScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = snippet,
                color = snippetColor,
                fontSize = (13f * messageFontScale).sp,
                lineHeight = (16f * messageFontScale).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UrgentMessageAckFooter(
    mine: Boolean,
    acks: List<UrgentAckEntry>,
    localNodeNumLong: Long?,
    nodes: List<MeshWireNodeSummary>,
    messageFontScale: Float,
    canSendMesh: Boolean,
    onSendAck: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    val myKey = localNodeNumLong ?: -1L
    val hasMyAck = acks.any { (it.fromNodeNum and 0xFFFF_FFFFL) == myKey }
    val count = acks.size
    val muted = Color(0xFF8A9A8E)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            Checkbox(
                checked = hasMyAck,
                onCheckedChange = { checked ->
                    if (checked && !hasMyAck && canSendMesh) onSendAck()
                },
                enabled = !hasMyAck && canSendMesh,
            )
            Text(
                text = if (hasMyAck) "Подтвердил" else "Подтвердить получение",
                color = muted,
                fontSize = (12f * messageFontScale).sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = acks.isNotEmpty()) {
                        if (acks.isNotEmpty()) showDetails = true
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (count > 0) Color(0xFF00E676) else muted,
                    modifier = Modifier.size((22f * messageFontScale).dp),
                )
                if (count > 0) {
                    Text(
                        text = count.toString(),
                        color = Color(0xFFFF8A80),
                        fontSize = (13f * messageFontScale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
    if (showDetails && mine && acks.isNotEmpty()) {
        val timeFmt = remember { SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.getDefault()) }
        Dialog(onDismissRequest = { showDetails = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2A2F3A),
                tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Подтвердили получение",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        acks.forEach { e ->
                            val u = (e.fromNodeNum and 0xFFFF_FFFFL).toUInt()
                            val node = nodes.firstOrNull {
                                (it.nodeNum and 0xFFFF_FFFFL) == (e.fromNodeNum and 0xFFFF_FFFFL)
                            }
                            val longName = node?.longName?.trim().orEmpty().ifBlank { "—" }
                            val hex = MeshWireNodeNum.formatHex(u)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "$longName\n$hex · ${timeFmt.format(Date(e.timeMs))}",
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }
                    }
                    TextButton(
                        onClick = { showDetails = false },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Закрыть", color = Color(0xFF90CAF9))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelBubbleClickableChannelText(
    text: String,
    style: TextStyle,
    bodyColor: Color,
    linkColor: Color,
    softWrap: Boolean,
    modifier: Modifier = Modifier,
    onBeaconShareLinkClick: (BeaconSharePayload, String) -> Unit = { _, _ -> },
    /** LongName отправителя этого пузыря (для импорта «Моя позиция» на карте). */
    beaconShareSenderLabel: String = "",
    /** Канал текущего чата: старые сообщения только с координатами рисуются как ссылка «Метка». */
    beaconCoordDisplayChannelIndex: Int? = null,
    beaconCoordDisplayChannelTitle: String? = null,
) {
    val latestBeaconClick = rememberUpdatedState(onBeaconShareLinkClick)
    val annotated = remember(
        text,
        bodyColor,
        linkColor,
        beaconShareSenderLabel,
        beaconCoordDisplayChannelIndex,
        beaconCoordDisplayChannelTitle,
    ) {
        BeaconShareLink.buildAnnotated(
            text = text,
            bodyColor = bodyColor,
            linkColor = linkColor,
            onLinkUriClicked = { uri ->
                BeaconShareLink.parseUri(uri)?.let { p ->
                    latestBeaconClick.value(p, beaconShareSenderLabel)
                }
            },
            coordinatesAsLinkChannelIndex = beaconCoordDisplayChannelIndex,
            coordinatesAsLinkChannelTitle = beaconCoordDisplayChannelTitle,
        )
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        softWrap = softWrap,
    )
}

@Composable
private fun ChannelMessageBubble(
    mine: Boolean,
    senderNodeIdHex: String? = null,
    shortBadge: String,
    onShortBadgeClick: (() -> Unit)? = null,
    viaMqtt: Boolean,
    timeLabel: String,
    outgoingDelivery: ChatMessageDeliveryStatus? = null,
    /** Срочное сообщение: красное оформление пузыря и shortname. */
    urgentHighlight: Boolean = false,
    /** Блок под реакциями (напр. подтверждения срочного). */
    urgentFooter: (@Composable () -> Unit)? = null,
    /** Повтор при [ChatMessageDeliveryStatus.FAILED] (текст). */
    onRetryFailedDelivery: (() -> Unit)? = null,
    /** Текст канала: ширина пузыря по последней строке (и длине самого длинного слова). */
    channelTextBody: String? = null,
    /** Цитата ответа (как в Telegram): автор и сниппет исходного сообщения. */
    replyQuoteAuthor: String? = null,
    replyQuoteSnippet: String? = null,
    /** Долгое нажатие на пузырь (меню реакций и действий). */
    onBubbleLongClick: ((LayoutCoordinates) -> Unit)? = null,
    /** Реакции в одной строке с временем: слева, часы и индикация — справа. */
    reactionChips: List<GroupedReactionUi> = emptyList(),
    /** Клик по чипу (toggle); долгое нажатие только на теле сообщения — меню. */
    onReactionChipClick: ((GroupedReactionUi) -> Unit)? = null,
    /** Множитель размера шрифта тела и времени (1 = базовый). */
    messageFontScale: Float = 1f,
    /** Клик по ссылке `aura://map/beacon?…` (или устаревшему `aurus://…`) в тексте сообщения; второй аргумент — подпись отправителя для карты. */
    onBeaconShareLinkClick: (BeaconSharePayload, String) -> Unit = { _, _ -> },
    /** LongName (или shortName) автора сообщения для диалога импорта метки. */
    beaconShareSenderLabel: String = "",
    /** Канал чата: для истории с текстом координат без URI — показ «Метка» как ссылка. */
    beaconCoordDisplayChannelIndex: Int? = null,
    beaconCoordDisplayChannelTitle: String? = null,
    content: @Composable () -> Unit = {},
) {
    var bubbleLayout by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val maxBubbleDp = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * 0.8f).dp
    }
    val theme = channelBubbleTheme(senderNodeIdHex)
    val bubbleTheme = if (urgentHighlight) {
        theme.copy(
            body = Color.White,
            timeMuted = Color.White.copy(alpha = 0.7f),
            mqttTint = Color.White.copy(alpha = 0.78f),
            iconOnBubble = Color.White,
        )
    } else {
        theme
    }
    val linkTint = if (isSystemInDarkTheme()) Color(0xFF64B5F6) else Color(0xFF3390EC)
    val urgentBubbleBg = if (mine) Color(0xFF5C1E22) else Color(0xFF4A2428)
    val surfaceColor = if (urgentHighlight) urgentBubbleBg else theme.bg
    val surfaceBorder = if (urgentHighlight) BorderStroke(2.dp, Color(0xFFFF5252)) else null
    val textMeasurer = rememberTextMeasurer()
    val channelBodyStyle = TextStyle(
        color = bubbleTheme.body,
        fontSize = (14f * messageFontScale).sp,
        lineHeight = (19f * messageFontScale).sp,
        fontWeight = FontWeight.Normal,
    )
    // Как в голосовом пузыре: время 10.sp
    val channelTimeStyle = TextStyle(
        color = bubbleTheme.timeMuted,
        fontSize = (10f * messageFontScale).sp,
        lineHeight = (12f * messageFontScale).sp,
    )
    val maxTextInnerPx = remember(maxBubbleDp, density) {
        with(density) { (maxBubbleDp - 24.dp).roundToPx().coerceAtLeast(1) }
    }
    val bubbleInnerDp = maxBubbleDp - 24.dp
    val tickExtraPx = remember(mine, outgoingDelivery, density) {
        if (mine && outgoingDelivery != null) {
            val s = OutgoingTickScale
            with(density) { 6.dp.roundToPx() + (26.dp * s).roundToPx() }
        } else {
            0
        }
    }
    val footerMinInnerPx = remember(
        timeLabel,
        viaMqtt,
        channelTimeStyle,
        maxTextInnerPx,
        textMeasurer,
        density,
        tickExtraPx,
    ) {
        channelBubbleTimeFooterMinInnerPx(
            textMeasurer,
            timeLabel,
            channelTimeStyle,
            viaMqtt,
            density,
            maxTextInnerPx,
            extraTrailingPx = tickExtraPx,
        )
    }
    val textTimeGapDp = 6.dp
    val textTimeGapPx = remember(density) { with(density) { textTimeGapDp.roundToPx() } }
    val telegramStackedTime = remember(
        channelTextBody,
        channelBodyStyle,
        maxTextInnerPx,
        footerMinInnerPx,
        textTimeGapPx,
        textMeasurer,
    ) {
        if (channelTextBody.isNullOrBlank()) false
        else channelBubbleTelegramStackedTime(
            textMeasurer,
            channelTextBody,
            channelBodyStyle,
            maxTextInnerPx,
            footerMinInnerPx,
            textTimeGapPx,
        )
    }

    // Как в Telegram: свои сообщения — кластер к правому краю, чужие — к левому.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (mine) 40.dp else 6.dp,
                top = 3.dp,
                end = if (mine) 6.dp else 40.dp,
                bottom = 3.dp,
            ),
    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        ) {
            ChannelShortNameBubble(
                mine = mine,
                shortBadge = shortBadge,
                senderNodeIdHex = senderNodeIdHex,
                urgentTint = urgentHighlight,
                onClick = onShortBadgeClick,
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = channelBubbleShape(mine),
                color = surfaceColor,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                border = surfaceBorder,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = maxBubbleDp)
                    .onGloballyPositioned { bubbleLayout = it },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = bubbleInnerDp)
                        .width(IntrinsicSize.Max)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (onBubbleLongClick != null) {
                                    Modifier.combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            bubbleLayout?.takeIf { it.isAttached }?.let { onBubbleLongClick(it) }
                                        },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (channelTextBody != null) {
                            Column(
                                modifier = Modifier.width(IntrinsicSize.Max),
                            ) {
                                if (!replyQuoteSnippet.isNullOrBlank()) {
                                    ChannelBubbleTelegramReplyQuote(
                                        authorName = replyQuoteAuthor,
                                        snippet = replyQuoteSnippet,
                                        mine = mine,
                                        messageFontScale = messageFontScale,
                                        theme = bubbleTheme,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                if (telegramStackedTime) {
                                    ChannelBubbleClickableChannelText(
                                        text = channelTextBody,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = channelBodyStyle,
                                        bodyColor = bubbleTheme.body,
                                        linkColor = if (urgentHighlight) Color.White else linkTint,
                                        softWrap = true,
                                        onBeaconShareLinkClick = onBeaconShareLinkClick,
                                        beaconShareSenderLabel = beaconShareSenderLabel,
                                        beaconCoordDisplayChannelIndex = beaconCoordDisplayChannelIndex,
                                        beaconCoordDisplayChannelTitle = beaconCoordDisplayChannelTitle,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ChannelBubbleTimeFooterTrailing(
                                            viaMqtt = viaMqtt,
                                            timeLabel = timeLabel,
                                            mine = mine,
                                            outgoingDelivery = outgoingDelivery,
                                            onRetryFailedDelivery = onRetryFailedDelivery,
                                            channelTimeStyle = channelTimeStyle,
                                            mqttTint = bubbleTheme.mqttTint,
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Bottom,
                                    ) {
                                        ChannelBubbleClickableChannelText(
                                            text = channelTextBody,
                                            modifier = Modifier.weight(1f, fill = false),
                                            style = channelBodyStyle,
                                            bodyColor = bubbleTheme.body,
                                            linkColor = if (urgentHighlight) Color.White else linkTint,
                                            softWrap = true,
                                            onBeaconShareLinkClick = onBeaconShareLinkClick,
                                            beaconShareSenderLabel = beaconShareSenderLabel,
                                            beaconCoordDisplayChannelIndex = beaconCoordDisplayChannelIndex,
                                            beaconCoordDisplayChannelTitle = beaconCoordDisplayChannelTitle,
                                        )
                                        Spacer(Modifier.width(textTimeGapDp))
                                        ChannelBubbleTimeFooterTrailing(
                                            viaMqtt = viaMqtt,
                                            timeLabel = timeLabel,
                                            mine = mine,
                                            outgoingDelivery = outgoingDelivery,
                                            onRetryFailedDelivery = onRetryFailedDelivery,
                                            channelTimeStyle = channelTimeStyle,
                                            mqttTint = bubbleTheme.mqttTint,
                                        )
                                    }
                                }
                            }
                        } else {
                content()
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (channelTextBody != null) {
                        if (reactionChips.isNotEmpty()) {
                            ChannelBubbleReactionRow(
                                chips = reactionChips,
                                messageFontScale = messageFontScale,
                                modifier = Modifier.fillMaxWidth(),
                                onChipClick = onReactionChipClick,
                                incomingLightBubble = !mine,
                            )
                        }
                        if (urgentFooter != null) {
                            Spacer(Modifier.height(6.dp))
                            urgentFooter()
                        }
                    } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                            ) {
                                if (reactionChips.isNotEmpty()) {
                                    ChannelBubbleReactionRow(
                                        chips = reactionChips,
                                        messageFontScale = messageFontScale,
                                        modifier = Modifier.fillMaxWidth(),
                                        onChipClick = onReactionChipClick,
                                        incomingLightBubble = !mine,
                                    )
                                }
                            }
                            ChannelBubbleTimeFooterTrailing(
                                viaMqtt = viaMqtt,
                                timeLabel = timeLabel,
                                mine = mine,
                                outgoingDelivery = outgoingDelivery,
                                onRetryFailedDelivery = onRetryFailedDelivery,
                                channelTimeStyle = channelTimeStyle,
                                mqttTint = bubbleTheme.mqttTint,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Нормализованные высоты столбиков [0..1] по сегментам Codec2-payload (псевдо-амплитуда). */
private fun voiceWaveformHeights(payload: ByteArray, barCount: Int): List<Float> {
    if (barCount <= 0) return emptyList()
    if (payload.isEmpty()) return List(barCount) { 0.25f }
    val n = barCount
    val chunk = payload.size / n
    if (chunk <= 0) {
        return List(n) { i ->
            val b = payload[i % payload.size].toInt() and 0xFF
            val v = kotlin.math.abs(b - 128) / 128f
            (0.2f + v.coerceIn(0f, 1f) * 0.8f)
        }
    }
    return List(n) { i ->
        val start = i * chunk
        val end = if (i == n - 1) payload.size else start + chunk
        var sum = 0
        for (j in start until end) {
            sum += kotlin.math.abs(payload[j].toInt())
        }
        val avg = sum.toFloat() / (end - start) / 128f
        (0.15f + avg.coerceIn(0f, 1f) * 0.85f).coerceIn(0.15f, 1f)
    }
}

@Composable
private fun VoiceMessageWaveform(
    payload: ByteArray,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    barColor: Color = Color(0xFF00E676),
    trackColor: Color = Color.White.copy(alpha = 0.12f),
) {
    val heights = remember(payload, barCount) { voiceWaveformHeights(payload, barCount) }
    val trackH = 28.dp
    val padV = 4.dp
    val innerMaxH = trackH - padV * 2
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackH)
            .background(trackColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = padV),
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(innerMaxH * h)
                        .background(barColor, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelVoiceMessageBubble(
    mine: Boolean,
    senderNodeIdHex: String? = null,
    shortBadge: String,
    onShortBadgeClick: (() -> Unit)? = null,
    attach: ChannelVoiceAttachment,
    viaMqtt: Boolean = false,
    timeLabel: String,
    outgoingDelivery: ChatMessageDeliveryStatus? = null,
    /** Подтверждение получения (⚡ACK в канал). */
    urgentFooter: (@Composable () -> Unit)? = null,
    onRetryFailedDelivery: (() -> Unit)? = null,
    onBubbleLongPress: ((LayoutCoordinates) -> Unit)? = null,
    reactionChips: List<GroupedReactionUi> = emptyList(),
    onReactionChipClick: ((GroupedReactionUi) -> Unit)? = null,
    messageFontScale: Float = 1f,
) {
    var voiceBubbleLayout by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxBubbleDp = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * 0.8f).dp
    }
    val theme = channelBubbleTheme(senderNodeIdHex)
    val voiceOutgoingFailed = remember(mine, outgoingDelivery, attach.deliveryStatus, attach.stableId) {
        if (!mine) {
            false
        } else {
            (outgoingDelivery ?: ChatMessageDeliveryStatus.fromCode(attach.deliveryStatus)) ==
                ChatMessageDeliveryStatus.FAILED
        }
    }
    var playing by remember(attach.stableId) { mutableStateOf(false) }
    val channelTimeStyle = TextStyle(
        color = theme.timeMuted,
        fontSize = (10f * messageFontScale).sp,
        lineHeight = (12f * messageFontScale).sp,
    )
    val voiceBarColor = if (mine) Color(0xFF00E676) else Color(0xFF0F9D58)
    val voiceTrackColor = if (mine) {
        Color.White.copy(alpha = 0.12f)
    } else {
        theme.body.copy(alpha = 0.14f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (mine) 40.dp else 6.dp,
                top = 3.dp,
                end = if (mine) 6.dp else 40.dp,
                bottom = 3.dp,
            ),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            ) {
                ChannelShortNameBubble(
                    mine = mine,
                    shortBadge = shortBadge,
                    senderNodeIdHex = senderNodeIdHex,
                    onClick = onShortBadgeClick,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = channelBubbleShape(mine),
                    color = theme.bg,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(min = 160.dp, max = maxBubbleDp)
                        .onGloballyPositioned { voiceBubbleLayout = it },
                ) {
                    Column(
                        Modifier
                            .width(IntrinsicSize.Max)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    if (playing) {
                                        VoicePlayback.stop()
                                    } else {
                                        VoicePlayback.playEncodedCodec2(context, attach.codecPayload) {
                                            playing = false
                                        }
                                        playing = true
                                    }
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "Пауза" else "Воспроизвести",
                                    tint = theme.iconOnBubble,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (onBubbleLongPress != null) {
                                            Modifier.combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {},
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    voiceBubbleLayout?.takeIf { it.isAttached }?.let { onBubbleLongPress(it) }
                                                },
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                ) {
                    Text(
                                    text = "Голос · ${attach.durationMs / 1000f}s",
                                    color = theme.body.copy(alpha = 0.85f),
                                    fontSize = (12f * messageFontScale).sp,
                                )
                                VoiceMessageWaveform(
                                    payload = attach.codecPayload,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    barColor = voiceBarColor,
                                    trackColor = voiceTrackColor,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (reactionChips.isNotEmpty()) {
                                    ChannelBubbleReactionRow(
                                        chips = reactionChips,
                                        messageFontScale = messageFontScale,
                                        modifier = Modifier.fillMaxWidth(),
                                        onChipClick = onReactionChipClick,
                                        incomingLightBubble = !mine,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.wrapContentWidth(),
                                horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                                if (viaMqtt) {
                                    Icon(
                                        Icons.Outlined.Cloud,
                                        contentDescription = "MQTT",
                                        tint = theme.mqttTint,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                        Text(
                                    text = timeLabel,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = channelTimeStyle,
                                )
                                if (mine && voiceOutgoingFailed && onRetryFailedDelivery != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Повторить отправку",
                                        tint = Color(0xFF64B5F6),
                                        modifier = Modifier
                                            .size((18f * OutgoingTickScale).dp)
                                            .clickable { onRetryFailedDelivery() },
                                    )
                                }
                            }
                        }
                        if (urgentFooter != null) {
                            Spacer(Modifier.height(6.dp))
                            urgentFooter()
                        }
                    }
                }
            }
        }
    }
}

private fun chatWallpaperDrawableRes(index: Int): Int? = when (index) {
    1 -> R.drawable.chat_wallpaper_1
    2 -> R.drawable.chat_wallpaper_2
    3 -> R.drawable.chat_wallpaper_3
    4 -> R.drawable.chat_wallpaper_4
    5 -> R.drawable.chat_wallpaper_5
    6 -> R.drawable.chat_wallpaper_6
    7 -> R.drawable.chat_wallpaper_7
    else -> null
}

@Composable
private fun ChatWallpaperPickerDialog(
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1B2820),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.chat_wallpaper_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .fillMaxWidth(),
                ) {
                    items(8) { idx ->
                        val selected = currentIndex == idx
                        val borderColor = if (selected) {
                            ConversationOverflowMenuAccent
                        } else {
                            Color.White.copy(alpha = 0.22f)
                        }
                        val borderW = if (selected) 2.dp else 1.dp
                        if (idx == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.65f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(borderW, borderColor, RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2A2F2C))
                                    .clickable { onSelect(0) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.chat_wallpaper_none),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        } else {
                            val res = chatWallpaperDrawableRes(idx)!!
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.65f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(borderW, borderColor, RoundedCornerShape(12.dp))
                                    .clickable { onSelect(idx) },
                            ) {
                                Image(
                                    painter = painterResource(res),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.action_cancel), color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

private fun buildPollMessage(
    pollId: String,
    question: String,
    options: List<String>,
    anonymous: Boolean,
    multipleAnswers: Boolean,
): String {
    val meta = buildString {
        append(if (anonymous) "анонимный" else "неанонимный")
        if (multipleAnswers) append(", несколько ответов")
    }
    val opts = options.mapIndexed { idx, opt -> "${idx + 1}) $opt" }
    return buildString {
        append("📊 ОПРОС")
        append('\n')
        append("id:")
        append(pollId.trim())
        append('\n')
        append(question.trim())
        append('\n')
        append("($meta)")
        if (opts.isNotEmpty()) {
            append('\n')
            append(opts.joinToString(separator = "\n"))
        }
    }
}

private data class PollPayload(
    val pollId: String?,
    val question: String,
    val options: List<String>,
    val anonymous: Boolean,
    val multipleAnswers: Boolean,
)

private data class PollVotePayload(
    val pollId: String,
    val optionIndexes: Set<Int>,
)

private data class PollVoteSnapshot(
    val totalVotes: Int,
    val optionVoteCounts: Map<Int, Int>,
    val mySelection: Set<Int>,
)

private fun generatePollId(localNodeNum: UInt?): String {
    val who = localNodeNum?.toLong()?.and(0xFFFF_FFFFL)?.toString(16) ?: "anon"
    val time = System.currentTimeMillis().toString(36)
    val nano = (System.nanoTime() and 0xFFFFFL).toString(36)
    return "p_${who}_${time}_$nano"
}

private fun parsePollMessage(text: String): PollPayload? {
    val lines = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.size < 4) return null
    if (!lines.first().startsWith("📊 ОПРОС")) return null
    val hasIdLine = lines.getOrNull(1)?.lowercase(Locale.ROOT)?.startsWith("id:") == true
    val pollId = if (hasIdLine) {
        lines.getOrNull(1)?.substringAfter("id:", "")?.trim()?.takeIf { it.isNotBlank() }
    } else {
        null
    }
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
    val multiple = "несколько ответов" in meta
    return PollPayload(
        pollId = pollId,
        question = question,
        options = options,
        anonymous = anonymous,
        multipleAnswers = multiple,
    )
}

private fun buildPollVoteMessage(pollId: String, optionIndexes: Set<Int>): String {
    val encoded = optionIndexes.sorted().joinToString(",")
    return buildString {
        append("🗳️ VOTE")
        append('\n')
        append("id:")
        append(pollId.trim())
        append('\n')
        append("opts:")
        append(encoded)
    }
}

private fun parsePollVoteMessage(text: String): PollVotePayload? {
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
    return PollVotePayload(
        pollId = pollId,
        optionIndexes = options,
    )
}

private fun buildPollVoteSnapshots(
    dbRows: List<ChannelChatMessageEntity>,
    localNodeNum: UInt?,
): Map<String, PollVoteSnapshot> {
    val latestByPollAndVoter = mutableMapOf<String, MutableMap<Long, Pair<Long, Set<Int>>>>()
    dbRows.forEach { row ->
        val vote = parsePollVoteMessage(row.text) ?: return@forEach
        val voter = row.fromNodeNum and 0xFFFF_FFFFL
        val pollMap = latestByPollAndVoter.getOrPut(vote.pollId) { mutableMapOf() }
        val prev = pollMap[voter]
        if (prev == null || row.createdAtMs >= prev.first) {
            pollMap[voter] = row.createdAtMs to vote.optionIndexes
        }
    }
    val myVoterId = localNodeNum?.toLong()?.and(0xFFFF_FFFFL)
    return latestByPollAndVoter.mapValues { (_, perVoter) ->
        val effectiveVotes = perVoter.values.map { it.second }.filter { it.isNotEmpty() }
        val optionCounts = mutableMapOf<Int, Int>()
        effectiveVotes.forEach { selected ->
            selected.forEach { idx ->
                optionCounts[idx] = (optionCounts[idx] ?: 0) + 1
            }
        }
        val mySelection = if (myVoterId != null) perVoter[myVoterId]?.second ?: emptySet() else emptySet()
        PollVoteSnapshot(
            totalVotes = effectiveVotes.size,
            optionVoteCounts = optionCounts,
            mySelection = mySelection,
        )
    }
}

private data class ChecklistPayload(
    val listId: String?,
    val title: String,
    val items: List<String>,
)

private data class ChecklistTogglePayload(
    val listId: String,
    val itemIndex: Int,
    val checked: Boolean,
)

private data class ChecklistItemState(
    val checked: Boolean,
    val ownerNodeNum: Long? = null,
)

private fun generateChecklistId(localNodeNum: UInt?): String {
    val who = localNodeNum?.toLong()?.and(0xFFFF_FFFFL)?.toString(16) ?: "anon"
    val time = System.currentTimeMillis().toString(36)
    val nano = (System.nanoTime() and 0xFFFFFL).toString(36)
    return "l_${who}_${time}_$nano"
}

private fun buildChecklistMessage(
    listId: String,
    title: String,
    items: List<String>,
): String {
    val rows = items.mapIndexed { idx, text -> "${idx + 1}) ${text.trim()}" }
    return buildString {
        append("🧾 СПИСОК")
        append('\n')
        append("id:")
        append(listId.trim())
        append('\n')
        append(title.trim())
        if (rows.isNotEmpty()) {
            append('\n')
            append(rows.joinToString(separator = "\n"))
        }
    }
}

private fun parseChecklistMessage(text: String): ChecklistPayload? {
    val lines = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.size < 3) return null
    if (!lines.first().startsWith("🧾 СПИСОК")) return null
    val hasIdLine = lines.getOrNull(1)?.lowercase(Locale.ROOT)?.startsWith("id:") == true
    val listId = if (hasIdLine) {
        lines.getOrNull(1)?.substringAfter("id:", "")?.trim()?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    val titleIndex = if (hasIdLine) 2 else 1
    val itemsStart = titleIndex + 1
    val title = lines.getOrNull(titleIndex)?.takeIf { it.isNotBlank() } ?: return null
    val items = lines.drop(itemsStart).mapNotNull { line ->
        val idx = line.indexOf(")")
        if (idx <= 0 || idx >= line.lastIndex) return@mapNotNull null
        line.substring(idx + 1).trim().takeIf { it.isNotEmpty() }
    }
    if (items.isEmpty()) return null
    return ChecklistPayload(
        listId = listId,
        title = title,
        items = items,
    )
}

private fun buildChecklistToggleMessage(
    listId: String,
    itemIndex: Int,
    checked: Boolean,
): String {
    return buildString {
        append("🧾U")
        append('\n')
        append("id:")
        append(listId.trim())
        append('\n')
        append("i:")
        append(itemIndex)
        append('\n')
        append("v:")
        append(if (checked) "1" else "0")
    }
}

private fun parseChecklistToggleMessage(text: String): ChecklistTogglePayload? {
    val lines = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.size < 4) return null
    if (!lines.first().equals("🧾U", ignoreCase = true)) return null
    val listId = lines.getOrNull(1)?.substringAfter("id:", "")?.trim().orEmpty()
    if (listId.isBlank()) return null
    val itemIndex = lines.getOrNull(2)?.substringAfter("i:", "")?.trim()?.toIntOrNull() ?: return null
    val checkedValue = lines.getOrNull(3)?.substringAfter("v:", "")?.trim() ?: return null
    val checked = checkedValue == "1" || checkedValue.equals("true", ignoreCase = true)
    if (itemIndex < 0) return null
    return ChecklistTogglePayload(
        listId = listId,
        itemIndex = itemIndex,
        checked = checked,
    )
}

private fun buildChecklistStateSnapshots(
    dbRows: List<ChannelChatMessageEntity>,
): Map<String, Map<Int, ChecklistItemState>> {
    val byList = mutableMapOf<String, MutableMap<Int, ChecklistItemState>>()
    dbRows
        .sortedBy { it.createdAtMs }
        .forEach { row ->
            val upd = parseChecklistToggleMessage(row.text) ?: return@forEach
            val stateMap = byList.getOrPut(upd.listId) { mutableMapOf() }
            val actorNodeNum = row.fromNodeNum and 0xFFFF_FFFFL
            val current = stateMap[upd.itemIndex]
            if (upd.checked) {
                // Отметить может любой, но только если пункт сейчас не отмечен.
                if (current?.checked == true) return@forEach
                stateMap[upd.itemIndex] = ChecklistItemState(
                    checked = true,
                    ownerNodeNum = actorNodeNum,
                )
            } else {
                // Снять отметку может только тот, кто ее поставил.
                if (current?.checked == true && current.ownerNodeNum == actorNodeNum) {
                    stateMap[upd.itemIndex] = ChecklistItemState(checked = false, ownerNodeNum = null)
                }
            }
        }
    return byList.mapValues { it.value.toMap() }
}

private data class LocalGifEntry(
    val id: String,
    val assetPath: String,
)

private fun listLocalGifEntries(context: Context): List<LocalGifEntry> {
    val appCtx = context.applicationContext
    return try {
        val names = appCtx.assets.list("gifs").orEmpty()
            .filter { it.endsWith(".gif", ignoreCase = true) }
            .sorted()
        names.map { file ->
            val id = file.substringBeforeLast(".")
            LocalGifEntry(id = id, assetPath = "gifs/$file")
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun buildLocalGifMessage(gifId: String): String = "$LocalGifMessagePrefix${gifId.trim()}"

private fun parseLocalGifMessage(text: String): String? {
    val trimmed = text.trim()
    if (!trimmed.startsWith(LocalGifMessagePrefix)) return null
    return trimmed.substringAfter(LocalGifMessagePrefix).trim().takeIf { it.isNotEmpty() }
}

@Composable
private fun LocalGifBubbleContent(
    gifId: String,
    mutedColor: Color,
    messageFontScale: Float,
) {
    val context = LocalContext.current
    val entry = remember(gifId) {
        listLocalGifEntries(context).firstOrNull { it.id.equals(gifId, ignoreCase = true) }
    }
    Column(Modifier.fillMaxWidth()) {
        if (entry == null) {
            Text(
                text = "GIF \"$gifId\" не найден локально",
                color = mutedColor,
                fontSize = (12f * messageFontScale).sp,
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { imageView ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val src = ImageDecoder.createSource(context.assets, entry.assetPath)
                            val drawable = ImageDecoder.decodeDrawable(src)
                            imageView.setImageDrawable(drawable)
                            (drawable as? AnimatedImageDrawable)?.let {
                                it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                                it.start()
                            }
                        } else {
                            val bmp = context.assets.open(entry.assetPath).use { BitmapFactory.decodeStream(it) }
                            imageView.setImageBitmap(bmp)
                        }
                    } catch (_: Exception) {
                        imageView.setImageDrawable(null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalGifPickerSheet(
    onDismiss: () -> Unit,
    onPick: (gifId: String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gifs = remember { listLocalGifEntries(context) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E22),
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (gifs.isEmpty()) {
                Text(
                    text = "Папка assets/gifs пуста.\nДобавьте одинаковые GIF в приложение на обоих устройствах.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    gifs.forEach { gif ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF233329),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(gif.id) },
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        adjustViewBounds = true
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                    }
                                },
                                update = { imageView ->
                                    try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                            val src = ImageDecoder.createSource(context.assets, gif.assetPath)
                                            val drawable = ImageDecoder.decodeDrawable(src)
                                            imageView.setImageDrawable(drawable)
                                            (drawable as? AnimatedImageDrawable)?.let {
                                                it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                                                it.start()
                                            }
                                        } else {
                                            val bmp = context.assets.open(gif.assetPath).use {
                                                BitmapFactory.decodeStream(it)
                                            }
                                            imageView.setImageBitmap(bmp)
                                        }
                                    } catch (_: Exception) {
                                        imageView.setImageDrawable(null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun ChecklistMessageContent(
    checklist: ChecklistPayload,
    itemStates: Map<Int, ChecklistItemState>,
    localNodeNumLong: Long?,
    textColor: Color,
    mutedColor: Color,
    messageFontScale: Float,
    ownerShortName: (ownerNodeNum: Long?) -> String?,
    onToggleItem: (itemIndex: Int, checked: Boolean) -> Unit,
) {
    // Общий cooldown на все галочки в этом списке: 3 секунды после любого нажатия.
    var cooldownUntilMs by remember(checklist.listId, checklist.title) { mutableLongStateOf(0L) }
    LaunchedEffect(cooldownUntilMs) {
        val until = cooldownUntilMs
        if (until <= 0L) return@LaunchedEffect
        val left = until - System.currentTimeMillis()
        if (left > 0L) delay(left)
        if (cooldownUntilMs == until) cooldownUntilMs = 0L
    }
    val cooldownActive = remember(cooldownUntilMs) { cooldownUntilMs > System.currentTimeMillis() }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = checklist.title,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14f * messageFontScale).sp,
            lineHeight = (18f * messageFontScale).sp,
        )
        Spacer(Modifier.height(6.dp))
        checklist.items.forEachIndexed { idx, item ->
            val itemState = itemStates[idx]
            val checked = itemState?.checked == true
            val canUncheck = itemState?.ownerNodeNum == localNodeNumLong
            val canToggleByOwnership = !checked || canUncheck
            val canToggle = canToggleByOwnership && !cooldownActive
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canToggleByOwnership) {
                            Modifier.clickable {
                                if (cooldownActive) return@clickable
                                cooldownUntilMs = System.currentTimeMillis() + 3_000L
                                onToggleItem(idx, !checked)
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { next ->
                        if (canToggleByOwnership && !cooldownActive) {
                            cooldownUntilMs = System.currentTimeMillis() + 3_000L
                            onToggleItem(idx, next)
                        }
                    },
                    enabled = canToggleByOwnership && !cooldownActive,
                )
                Text(
                    text = item,
                    color = if (canToggleByOwnership || !checked) textColor else textColor.copy(alpha = 0.75f),
                    fontSize = (13f * messageFontScale).sp,
                    lineHeight = (17f * messageFontScale).sp,
                    modifier = Modifier.weight(1f),
                )
                if (checked) {
                    val name = ownerShortName(itemState?.ownerNodeNum)
                    if (name != null) {
                        Text(
                            text = name,
                            color = mutedColor,
                            fontSize = (10f * messageFontScale).sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Список",
            color = mutedColor,
            fontSize = (11f * messageFontScale).sp,
        )
    }
}

@Composable
private fun PollMessageContent(
    poll: PollPayload,
    selectedOptions: Set<Int>,
    optionVoteCounts: Map<Int, Int>,
    totalVotes: Int,
    textColor: Color,
    accentColor: Color,
    mutedColor: Color,
    messageFontScale: Float,
    onToggleOption: (Int) -> Unit,
) {
    val selectedCount = selectedOptions.size
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "📊 ${poll.question}",
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14f * messageFontScale).sp,
            lineHeight = (18f * messageFontScale).sp,
        )
        Spacer(Modifier.height(6.dp))
        poll.options.forEachIndexed { idx, option ->
            val selected = selectedOptions.contains(idx)
            val votesForOption = optionVoteCounts[idx] ?: 0
            val progress = if (totalVotes <= 0) 0f else votesForOption.toFloat() / totalVotes.toFloat()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                shape = RoundedCornerShape(10.dp),
                color = textColor.copy(alpha = if (selected) 0.18f else 0.08f),
                border = BorderStroke(
                    1.dp,
                    if (selected) accentColor.copy(alpha = 0.65f) else textColor.copy(alpha = 0.14f),
                ),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggleOption(idx) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = option,
                            color = textColor,
                            fontSize = (13f * messageFontScale).sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(
                                text = "✓",
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (13f * messageFontScale).sp,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        color = accentColor.copy(alpha = 0.9f),
                        trackColor = textColor.copy(alpha = 0.10f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                    Text(
                        text = if (totalVotes > 0) "$votesForOption из $totalVotes" else "0 голосов",
                        color = mutedColor.copy(alpha = 0.86f),
                        fontSize = (10f * messageFontScale).sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Опрос",
                color = mutedColor,
                fontSize = (11f * messageFontScale).sp,
            )
            Text(
                text = if (selectedCount == 0) {
                    if (poll.multipleAnswers) "Можно выбрать несколько вариантов" else "Нажмите, чтобы проголосовать"
                } else {
                    "Ваш голос учтен${if (poll.multipleAnswers) " (${selectedCount})" else ""}"
                },
                color = mutedColor,
                fontSize = (11f * messageFontScale).sp,
            )
        }
    }
}

/** Поля ввода в диалогах опроса/списка — как на экранах настроек [MstSett]. */
@Composable
private fun chatComposerDialogFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = MstSett.text,
    unfocusedTextColor = MstSett.text,
    disabledTextColor = MstSett.muted,
    focusedContainerColor = MstSett.cancelBtn,
    unfocusedContainerColor = MstSett.cancelBtn,
    disabledContainerColor = MstSett.cancelBtn,
    focusedIndicatorColor = MstSett.accent,
    unfocusedIndicatorColor = MstSett.dividerInCard,
    disabledIndicatorColor = MstSett.dividerInCard,
    cursorColor = MstSett.accent,
    errorCursorColor = MstSett.accent,
    focusedLabelColor = MstSett.muted,
    unfocusedLabelColor = MstSett.muted,
    disabledLabelColor = MstSett.muted.copy(alpha = 0.55f),
    focusedPlaceholderColor = MstSett.muted.copy(alpha = 0.65f),
    unfocusedPlaceholderColor = MstSett.muted.copy(alpha = 0.65f),
)

@Composable
private fun PollComposerDialog(
    onDismiss: () -> Unit,
    onSubmit: (question: String, options: List<String>, anonymous: Boolean, multipleAnswers: Boolean) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var anonymous by remember { mutableStateOf(true) }
    var multipleAnswers by remember { mutableStateOf(false) }

    val normalizedOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
    val canSend = question.trim().isNotEmpty() && normalizedOptions.size >= 2

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MstSett.onAccent,
        checkedTrackColor = MstSett.accent.copy(alpha = 0.5f),
        uncheckedThumbColor = MstSett.switchThumbUnchecked,
        uncheckedTrackColor = MstSett.switchTrackUnchecked,
    )

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        containerColor = MstSett.card,
        shape = RoundedCornerShape(24.dp),
        titleContentColor = MstSett.text,
        textContentColor = MstSett.muted,
        title = {
            Text(
                text = "Создать опрос",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            val formScrollMaxH = (LocalConfiguration.current.screenHeightDp * 0.52f).dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = formScrollMaxH)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    value = question,
                    onValueChange = { question = it },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 6,
                    label = { Text("Вопрос") },
                    placeholder = { Text("О чем опрос?") },
                    colors = chatComposerDialogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { idx, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = value,
                            onValueChange = { options[idx] = it },
                            singleLine = true,
                            label = { Text("Вариант ${idx + 1}") },
                            modifier = Modifier.weight(1f),
                            colors = chatComposerDialogFieldColors(),
                        )
                        if (options.size > 2) {
                            IconButton(
                                onClick = { options.removeAt(idx) },
                                modifier = Modifier.padding(start = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Удалить вариант",
                                    tint = MstSett.muted,
                                )
                            }
                        }
                    }
                }
                if (options.size < 10) {
                    TextButton(onClick = { options.add("") }) {
                        Text("Добавить вариант", color = MstSett.accent)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Анонимный опрос", color = MstSett.text)
                    Switch(
                        checked = anonymous,
                        onCheckedChange = { anonymous = it },
                        colors = switchColors,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Несколько ответов", color = MstSett.text)
                    Switch(
                        checked = multipleAnswers,
                        onCheckedChange = { multipleAnswers = it },
                        colors = switchColors,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = MstSett.muted)
                    }
                    TextButton(
                        enabled = canSend,
                        onClick = {
                            onSubmit(question, normalizedOptions, anonymous, multipleAnswers)
                        },
                    ) {
                        Text("Отправить", color = if (canSend) MstSett.accent else MstSett.muted)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChecklistComposerSheet(
    onDismiss: () -> Unit,
    onSubmit: (title: String, items: List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { next -> next != SheetValue.Hidden },
    )
    var title by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf("", "") }
    val normalizedItems = items.map { it.trim() }.filter { it.isNotEmpty() }
    val canSend = title.trim().isNotEmpty() && normalizedItems.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = MstSett.card,
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Создать список",
                color = MstSett.text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            TextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Заголовок списка") },
                colors = chatComposerDialogFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            items.forEachIndexed { idx, value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = value,
                        onValueChange = { items[idx] = it },
                        singleLine = true,
                        label = { Text("Пункт ${idx + 1}") },
                        colors = chatComposerDialogFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    if (items.size > 1) {
                        IconButton(onClick = { items.removeAt(idx) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Удалить пункт",
                                tint = MstSett.muted,
                            )
                        }
                    }
                }
            }
            if (items.size < 20) {
                TextButton(onClick = { items.add("") }) {
                    Text("Добавить пункт", color = MstSett.accent)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Отмена", color = MstSett.muted)
                }
                TextButton(
                    enabled = canSend,
                    onClick = { onSubmit(title.trim(), normalizedItems) },
                ) {
                    Text(
                        "Отправить",
                        color = if (canSend) MstSett.accent else MstSett.muted,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun compressImageForMesh(context: android.content.Context, uri: Uri): ByteArray? {
    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: return null
    val w = bmp.width
    val h = bmp.height
    val scale = min(960.0 / w, min(960.0 / h, 1.0))
    val tw = (w * scale).toInt().coerceAtLeast(1)
    val th = (h * scale).toInt().coerceAtLeast(1)
    val scaled = if (scale < 1.0) Bitmap.createScaledBitmap(bmp, tw, th, true) else bmp
    val os = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 78, os)
    val out = os.toByteArray()
    if (scaled != bmp) scaled.recycle()
    bmp.recycle()
    if (out.isEmpty() || out.size > 24 * 1024) return null
    return out
}

@Composable
private fun ChannelConversationScreen(
    padding: PaddingValues,
    channel: MeshStoredChannel,
    /** Не null — режим личной переписки с узлом (индекс LoRa из [channel], обычно primary). */
    directMessagePeer: MeshWireNodeSummary? = null,
    deviceAddress: String?,
    bleConnected: Boolean,
    localNodeNum: UInt?,
    nodeId: String,
    hideCoordinatesTransmission: Boolean,
    historyClearBumpForConversation: Int,
    onBubbleMenuSessionChange: (BubbleMenuSession?) -> Unit,
    messageFontScalePercent: Float,
    messageSearchQuery: String,
    messageSearchActive: Boolean,
    chatWallpaperResId: Int?,
    matrixBackdropActive: Boolean,
    initialDraftText: String?,
    onInitialDraftConsumed: () -> Unit,
    onBeaconShareLinkClick: (BeaconSharePayload, String) -> Unit = { _, _ -> },
    onOpenUserProfile: (MeshWireNodeSummary) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as AuraApplication
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    /** Истёкший VIP: блокируем меню «скрепки» и клики по меткам внутри беседы. */
    val vipRestrictedInConversation = rememberVipRestricted()
    val listState = rememberLazyListState()
    var initialScrollApplied by remember(channel.rowKey, channel.index) { mutableStateOf(false) }
    var pinnedSnapshot by remember(channel.rowKey, addr) { mutableStateOf<ChannelPinnedSnapshot?>(null) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(channel.rowKey, channel.index, initialDraftText) {
        val initial = initialDraftText?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (draft.isBlank()) {
            draft = clampChannelChatDraftUtf8(initial)
        }
        onInitialDraftConsumed()
    }

    var showPollComposer by remember { mutableStateOf(false) }
    var showChecklistComposer by remember { mutableStateOf(false) }
    var showLocalGifPicker by remember { mutableStateOf(false) }
    var messageInputFocused by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sendCooldownUntilMs by remember(channel.rowKey, channel.index) { mutableLongStateOf(0L) }
    var sendCooldownNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var replyDraft by remember { mutableStateOf<MessageReplyDraft?>(null) }
    var voiceRecording by remember { mutableStateOf(false) }
    var voiceLocked by remember { mutableStateOf(false) }
    var voiceElapsedMs by remember { mutableLongStateOf(0L) }
    val voiceMgr = remember { VoiceMessageManager(context) }
    var nodes by remember { mutableStateOf<List<MeshWireNodeSummary>>(emptyList()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val macNormForLora = remember(addr) { addr?.let { MeshNodeSyncMemoryStore.normalizeKey(it) } }
    val modemPresetForBubbleTitle = remember(macNormForLora) {
        macNormForLora?.let { MeshNodeSyncMemoryStore.getLora(it)?.modemPreset }
    }
    val beaconThreadChannelTitle = remember(channel, modemPresetForBubbleTitle) {
        conversationChannelTitle(channel, modemPresetForBubbleTitle)
    }

    // rowKey один может оставаться при смене слота (index) в редакторе канала — обязательно ключуем index,
    // иначе ToRadio уйдёт со старым channel index (не тот канал).
    val historyNodeIdHex = remember(nodeId) {
        ChatHistoryFileStore.normalizeNodeIdHex(nodeId).ifEmpty { null }
    }
    val localNodeIdHexForBubble = remember(nodeId) { normalizeNodeIdHexForBubbleColor(nodeId) }
    val chatSession = remember(
        channel.rowKey,
        channel.index,
        addr,
        localNodeNum,
        historyNodeIdHex,
        directMessagePeer?.nodeNum,
        nodes.size,
    ) {
        if (addr == null) {
            null
        } else if (directMessagePeer != null) {
            val peerLong = directMessagePeer.nodeNum and 0xFFFF_FFFFL
            val macNorm = MeshNodeSyncMemoryStore.normalizeKey(addr)
            val folder = ChatHistoryFileStore.directThreadFolderNameForPeer(
                app.applicationContext,
                macNorm,
                peerLong,
            )
            DirectChatManager(
                app.applicationContext,
                app.chatDatabase,
                addr,
                channel,
                peerLong,
                folder,
                localNodeNum,
                historyNodeIdHex,
                app.channelMessageRepository,
                conversationDisplayName = displayTitleForDmPeer(directMessagePeer.nodeNum, nodes),
            )
        } else {
            ChannelChatManager(
                app.applicationContext,
                app.chatDatabase,
                addr,
                channel,
                localNodeNum,
                app.channelMessageRepository,
                historyNodeIdHex,
            )
        }
    }

    LaunchedEffect(addr, channel.rowKey) {
        val a = addr ?: return@LaunchedEffect
        if (directMessagePeer == null) {
            pinnedSnapshot = ChannelPinnedMessageStore.read(context.applicationContext, a, channel.rowKey)
        }
    }

    LaunchedEffect(historyClearBumpForConversation, chatSession, channel.rowKey, channel.index) {
        if (historyClearBumpForConversation == 0) return@LaunchedEffect
        chatSession?.clearLocalChatUiAfterHistoryClear()
        val a = addr ?: return@LaunchedEffect
        if (directMessagePeer == null) {
            ChannelPinnedMessageStore.write(context.applicationContext, a, channel.rowKey, null)
            pinnedSnapshot = null
        }
    }

    val chatReadDao = remember { app.chatDatabase.channelChatMessageDao() }
    LaunchedEffect(addr, channel.index, channel.rowKey, directMessagePeer?.nodeNum) {
        val a = addr ?: return@LaunchedEffect
        val macNorm = MeshNodeSyncMemoryStore.normalizeKey(a)
        val peerLong = directMessagePeer?.nodeNum?.and(0xFFFF_FFFFL)
        withContext(NonCancellable) {
            if (peerLong != null) {
                chatReadDao.markIncomingReadForDirectThread(macNorm, peerLong)
            } else {
                chatReadDao.markIncomingReadForChannel(macNorm, channel.index)
            }
        }
        MeshNotificationDispatcher.clearMessageNotifications(context.applicationContext)
        MeshIncomingChatRepository.incomingMessages.collect { evt ->
            if (evt.entity.deviceMac != macNorm) return@collect
            if (peerLong != null) {
                if (evt.entity.dmPeerNodeNum != peerLong) return@collect
            } else {
                if (evt.entity.channelIndex != channel.index) return@collect
            }
            if (peerLong == null) {
                if (evt.entity.dmPeerNodeNum != null) return@collect
            }
            withContext(NonCancellable) {
                if (peerLong != null) {
                    chatReadDao.markIncomingReadForDirectThread(macNorm, peerLong)
                } else {
                    chatReadDao.markIncomingReadForChannel(macNorm, channel.index)
                }
            }
            MeshNotificationDispatcher.clearMessageNotifications(context.applicationContext)
        }
    }

    DisposableEffect(addr, channel.index, directMessagePeer?.nodeNum) {
        onDispose {
            val a = addr ?: return@onDispose
            val macNorm = MeshNodeSyncMemoryStore.normalizeKey(a)
            val pl = directMessagePeer?.nodeNum?.and(0xFFFF_FFFFL)
            CoroutineScope(Dispatchers.IO).launch {
                if (pl != null) {
                    chatReadDao.markIncomingReadForDirectThread(macNorm, pl)
                } else {
                    chatReadDao.markIncomingReadForChannel(macNorm, channel.index)
                }
            }
        }
    }

    DisposableEffect(chatSession, channel.rowKey, channel.index, addr, localNodeNum) {
        onDispose {
            chatSession?.stop()
        }
    }

    LaunchedEffect(chatSession, bleConnected) {
        if (chatSession != null && bleConnected) {
            chatSession.start()
        } else {
            chatSession?.stop()
        }
    }

    val messagesFlow = remember(chatSession) { chatSession?.messages ?: flowOf(emptyList()) }
    val dbRows by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    /** Уже ушли квитанции «Прочитано» по [meshPacketId] входящего, чтобы не дублировать. */
    val readReceiptsSent = remember(addr, directMessagePeer?.nodeNum, channel.rowKey, channel.index) {
        mutableSetOf<Long>()
    }
    /** Первый проход логики read-receipts в данной сессии чата. */
    val readReceiptsBackfillDone = remember(addr, directMessagePeer?.nodeNum, channel.rowKey, channel.index) {
        booleanArrayOf(false)
    }
    val imgFlow = remember(chatSession) { chatSession?.imageAttachments ?: flowOf(emptyList()) }
    val imgAtt by imgFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val voiceFlow = remember(chatSession) { chatSession?.voiceAttachments ?: flowOf(emptyList()) }
    val voiceAtt by voiceFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val pollVoteSnapshots = remember(dbRows, localNodeNum) {
        buildPollVoteSnapshots(dbRows, localNodeNum)
    }
    val localNodeNumLong = remember(localNodeNum) { localNodeNum?.toLong()?.and(0xFFFF_FFFFL) }
    val checklistStateSnapshots = remember(dbRows) {
        buildChecklistStateSnapshots(dbRows)
    }
    val urgentAckSnapshots = remember(dbRows) {
        buildUrgentAckSnapshots(dbRows)
    }
    // Read receipt (личка и канал): тело «Прочитано», id прочитанного пакета в Data.reply_id
    // (см. MeshWireReadReceiptCodec). В канале квитанция шлётся броадкастом; исходный отправитель
    // матчит её по meshPacketId и ставит две синие галочки (READ_IN_PEER_APP). Остальные
    // участники канала получают «Прочитано», но не создают пузырь и не трогают свои строки.
    //
    // Канал: при открытии чата историю не догоняем — чтобы не заспамить эфир квитанциями
    // по всем старым сообщениям. Отправляем квитанции только по последним
    // [CHANNEL_READ_RECEIPT_BACKFILL_LIMIT] входящим и по всем новым, приходящим далее.
    // Личка: догоняем всю историю (трафик минимальный, 1-на-1).
    LaunchedEffect(bleConnected, directMessagePeer?.nodeNum, channel.index, chatSession, dbRows) {
        val session = chatSession
        if (session == null || !bleConnected) return@LaunchedEffect

        if (!readReceiptsBackfillDone[0]) {
            if (directMessagePeer == null) {
                val incomingPids = dbRows.asSequence()
                    .filter { !it.isOutgoing }
                    .mapNotNull { it.meshPacketId }
                    .map { it and 0xFFFF_FFFFL }
                    .toList()
                val skipUntil = (incomingPids.size - CHANNEL_READ_RECEIPT_BACKFILL_LIMIT).coerceAtLeast(0)
                for (i in 0 until skipUntil) {
                    readReceiptsSent.add(incomingPids[i])
                }
            }
            readReceiptsBackfillDone[0] = true
        }

        for (row in dbRows) {
            if (row.isOutgoing) continue
            val rawPid = row.meshPacketId ?: continue
            val p = rawPid and 0xFFFF_FFFFL
            // Атомарно: add() возвращает false, если уже было — страхует от двойной отправки
            // при отмене/перезапуске эффекта во время работы sendMessage().
            if (!readReceiptsSent.add(p)) continue
            if (!isActive) return@LaunchedEffect
            val replyToPid = (p and 0xFFFF_FFFFL).toUInt()
            val replyFrom = (row.fromNodeNum and 0xFFFF_FFFFL).toUInt()
            session.sendMessage(
                MeshWireReadReceiptCodec.readReceiptWireText(),
                0xFFFFFFFFL,
                replyToPacketId = replyToPid,
                replyToFromNodeNum = replyFrom,
                replyPreviewText = null,
            )
            delay(300)
        }
    }
    LaunchedEffect(sendCooldownUntilMs) {
        while (isActive && sendCooldownUntilMs > System.currentTimeMillis()) {
            sendCooldownNowMs = System.currentTimeMillis()
            delay(200)
        }
        sendCooldownNowMs = System.currentTimeMillis()
    }
    val sendCooldownSecondsLeft = remember(sendCooldownUntilMs, sendCooldownNowMs) {
        (((sendCooldownUntilMs - sendCooldownNowMs).coerceAtLeast(0L) + 999L) / 1000L).toInt()
    }

    val mergedItems = remember(dbRows, imgAtt, voiceAtt, localNodeNum) {
        val texts = dbRows.map { it.toTextBubble(localNodeNum) }
        val imgs = imgAtt.map { it.toImageBubble() }
        val voices = voiceAtt.map { it.toVoiceBubble(localNodeNum) }
        (texts + imgs + voices).sortedBy { it.timeMs }
    }
    val chatVisibleItems = remember(mergedItems) {
        mergedItems.filterNot { item ->
            item is ChannelBubbleItem.TextMsg &&
                (
                    parsePollVoteMessage(item.text) != null ||
                        parseChecklistToggleMessage(item.text) != null ||
                        parseUrgentAckMeshMessage(item.text) != null ||
                        MeshWireReadReceiptCodec.shouldHideReadReceiptInChatList(
                            text = item.text,
                            isOutgoing = item.mine,
                            replyToPacketId = item.replyToPacketId,
                        )
                    )
        }
    }

    val messageFontScale = remember(messageFontScalePercent) {
        channelBubbleFontScaleMultiplier(messageFontScalePercent)
    }
    val inputCursorColor = remember(channel) {
        securityLockTint(meshStoredChannelSecurityUi(channel))
    }

    val filteredMergedItems = remember(chatVisibleItems, messageSearchQuery, messageSearchActive) {
        if (!messageSearchActive) return@remember chatVisibleItems
        val q = messageSearchQuery.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) return@remember chatVisibleItems
        chatVisibleItems.filter { msg ->
            when (msg) {
                is ChannelBubbleItem.TextMsg -> msg.text.lowercase(Locale.getDefault()).contains(q)
                is ChannelBubbleItem.ImageMsg,
                is ChannelBubbleItem.VoiceMsg,
                -> false
            }
        }
    }

    val chatListRows = remember(filteredMergedItems) {
        insertChatDateHeaders(filteredMergedItems)
    }

    fun nodeFor(from: UInt?): MeshWireNodeSummary? {
        val f = from ?: return null
        val want = f.toLong() and 0xFFFF_FFFFL
        return nodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == want }
    }

    fun bubbleSenderProfileNode(from: UInt?, mine: Boolean): MeshWireNodeSummary? {
        nodeFor(from)?.let { return it }
        if (!mine) return null
        val local = localNodeNum?.toLong()?.and(0xFFFF_FFFFL) ?: return null
        return nodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == local }
    }

    /** В истории чата: longName + nodeId (если longName пуст — shortName + nodeId). */
    fun bubbleSenderLabel(from: UInt?): String {
        val n = nodeFor(from)
        val hex = from?.let { MeshWireNodeNum.formatHex(it).lowercase(Locale.ROOT) } ?: "!unknown"
        val longName = n?.longName?.trim().orEmpty()
        val shortName = n?.shortName?.trim().orEmpty()
        val baseName = when {
            longName.isNotBlank() -> longName
            shortName.isNotBlank() && shortName != "?" -> shortName
            else -> "Node"
        }
        return "$baseName ${hex.removePrefix("!").take(12)}"
    }

    /** Для маленького бейджа у сообщения: только shortName (fallback на longName/hex). */
    fun bubbleSenderShortBadge(from: UInt?): String {
        val n = nodeFor(from)
        val shortName = n?.shortName?.trim().orEmpty()
        if (shortName.isNotBlank() && shortName != "?") return shortName
        val longName = n?.longName?.trim().orEmpty()
        if (longName.isNotBlank()) return longName.take(24)
        val hex = from?.let { MeshWireNodeNum.formatHex(it).lowercase(Locale.ROOT) } ?: "!unknown"
        return hex.removePrefix("!").take(12)
    }

    /** Только longName узла (для поля «Название метки» при импорте «Моя позиция»); пусто — если longName не задан. */
    fun bubbleSenderLongNamePreferred(from: UInt?): String {
        val n = nodeFor(from) ?: return ""
        return n.longName?.trim().orEmpty()
    }

    /** Строка «имя» в закрепе: только shortName узла, без longName и без суффикса node id. */
    fun bubblePinnedAuthorShortOnly(from: UInt?): String {
        val n = nodeFor(from) ?: return ""
        val shortName = n.shortName?.trim().orEmpty()
        return if (shortName.isNotBlank() && shortName != "?") shortName else ""
    }

    fun pinnedAuthorDisplay(p: ChannelPinnedSnapshot): String {
        val uid = p.fromNodeNum?.let { (it and 0xFFFF_FFFFL).toUInt() }
        if (uid != null) return bubblePinnedAuthorShortOnly(uid)
        return p.authorLabel
    }

    fun openBubbleMenu(coords: LayoutCoordinates, target: MessageBubbleMenuTarget) {
        if (!coords.isAttached) return
        val st = MessageBubbleMenuState(target = target)
        onBubbleMenuSessionChange(
            BubbleMenuSession(
                state = st,
                onReply = {
                    when (val t = st.target) {
                        is MessageBubbleMenuTarget.Text -> {
                            val m = t.item
                            val label = bubbleSenderLabel(m.from)
                            if (m.meshPacketId != null && m.from != null) {
                                val snippet = m.text.replace("\n", " ").trim().take(200)
                                replyDraft = MessageReplyDraft(
                                    replyToPacketId = m.meshPacketId,
                                    replyToFromNodeNum = m.from,
                                    authorName = label,
                                    quotedSnippet = snippet,
                                )
                            } else {
                                replyDraft = null
                            }
                        }
                        is MessageBubbleMenuTarget.Image -> {
                            val im = t.item
                            val label = bubbleSenderLabel(im.from)
                            val desc = "[Фото]"
                            if (im.meshPacketId != null && im.from != null) {
                                replyDraft = MessageReplyDraft(
                                    replyToPacketId = im.meshPacketId,
                                    replyToFromNodeNum = im.from,
                                    authorName = label,
                                    quotedSnippet = desc,
                                )
                            } else {
                                replyDraft = null
                            }
                        }
                        is MessageBubbleMenuTarget.Voice -> {
                            val vm = t.item
                            val a = vm.attachment
                            val label = bubbleSenderLabel(a.from)
                            val desc = "[Голос · ${a.durationMs / 1000f}s]"
                            val packetId = vm.meshPacketId ?: a.meshPacketId
                            if (packetId != null && a.from != null) {
                                replyDraft = MessageReplyDraft(
                                    replyToPacketId = packetId,
                                    replyToFromNodeNum = a.from,
                                    authorName = label,
                                    quotedSnippet = desc,
                                )
                            } else {
                                replyDraft = null
                            }
                        }
                    }
                },
                onCopyText = {
                    when (val t = st.target) {
                        is MessageBubbleMenuTarget.Text -> {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("chat", t.item.text))
                            Toast.makeText(context, R.string.msg_menu_copied, Toast.LENGTH_SHORT).show()
                        }
                        else -> Toast.makeText(context, R.string.msg_menu_not_implemented, Toast.LENGTH_SHORT).show()
                    }
                },
                onReaction = { emojiListIndex ->
                    when (val t = st.target) {
                        is MessageBubbleMenuTarget.Text -> {
                            val m = t.item
                            m.meshPacketId?.let { pid ->
                                chatSession?.sendReaction(
                                    pid.toLong() and 0xFFFF_FFFFL,
                                    emojiListIndex,
                                )
                            }
                        }
                        is MessageBubbleMenuTarget.Image -> {
                            val im = t.item
                            im.meshPacketId?.let { pid ->
                                chatSession?.sendReaction(
                                    pid.toLong() and 0xFFFF_FFFFL,
                                    emojiListIndex,
                                )
                            }
                        }
                        is MessageBubbleMenuTarget.Voice -> {
                            val vm = t.item
                            vm.meshPacketId?.let { pid ->
                                chatSession?.sendReaction(
                                    pid.toLong() and 0xFFFF_FFFFL,
                                    emojiListIndex,
                                )
                            }
                        }
                    }
                },
                onPin = onPin@{
                    val a = addr ?: return@onPin
                    when (val t = st.target) {
                        is MessageBubbleMenuTarget.Text -> {
                            val m = t.item
                            val snap = ChannelPinnedSnapshot(
                                stableId = m.stableId,
                                kind = "text",
                                authorLabel = bubblePinnedAuthorShortOnly(m.from),
                                snippet = m.text.replace("\n", " ").trim().take(120),
                                timeMs = m.timeMs,
                                fromNodeNum = m.from?.toLong()?.and(0xFFFF_FFFFL),
                            )
                            ChannelPinnedMessageStore.write(context.applicationContext, a, channel.rowKey, snap)
                            pinnedSnapshot = snap
                            Toast.makeText(context, R.string.msg_pinned_toast, Toast.LENGTH_SHORT).show()
                        }
                        is MessageBubbleMenuTarget.Image -> {
                            val im = t.item
                            val snap = ChannelPinnedSnapshot(
                                stableId = im.stableId,
                                kind = "image",
                                authorLabel = bubblePinnedAuthorShortOnly(im.from),
                                snippet = context.getString(R.string.msg_pin_preview_photo),
                                timeMs = im.timeMs,
                                fromNodeNum = im.from?.toLong()?.and(0xFFFF_FFFFL),
                            )
                            ChannelPinnedMessageStore.write(context.applicationContext, a, channel.rowKey, snap)
                            pinnedSnapshot = snap
                            Toast.makeText(context, R.string.msg_pinned_toast, Toast.LENGTH_SHORT).show()
                        }
                        is MessageBubbleMenuTarget.Voice -> {
                            val vm = t.item
                            val att = vm.attachment
                            val desc = "[Голос · ${att.durationMs / 1000f}s]"
                            val snap = ChannelPinnedSnapshot(
                                stableId = vm.stableId,
                                kind = "voice",
                                authorLabel = bubblePinnedAuthorShortOnly(att.from),
                                snippet = desc,
                                timeMs = vm.timeMs,
                                fromNodeNum = att.from?.toLong()?.and(0xFFFF_FFFFL),
                            )
                            ChannelPinnedMessageStore.write(context.applicationContext, a, channel.rowKey, snap)
                            pinnedSnapshot = snap
                            Toast.makeText(context, R.string.msg_pinned_toast, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDelete = onDelete@{
                    fun clearPinIfMatches(sid: String) {
                        if (pinnedSnapshot?.stableId != sid) return
                        val a = addr ?: return
                        ChannelPinnedMessageStore.write(context.applicationContext, a, channel.rowKey, null)
                        pinnedSnapshot = null
                    }
                    when (val t = st.target) {
                        is MessageBubbleMenuTarget.Text -> {
                            val m = t.item
                            val mgr = chatSession
                            if (mgr == null) {
                                Toast.makeText(context, R.string.msg_menu_not_implemented, Toast.LENGTH_SHORT).show()
                                return@onDelete
                            }
                            scope.launch {
                                mgr.removeTextMessageFromChatPreservingHistory(m.rowId)
                                withContext(Dispatchers.Main) {
                                    clearPinIfMatches(m.stableId)
                                    Toast.makeText(context, R.string.msg_deleted_from_chat, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        is MessageBubbleMenuTarget.Image -> {
                            val im = t.item
                            val mgr = chatSession
                            if (mgr == null) {
                                Toast.makeText(context, R.string.msg_menu_not_implemented, Toast.LENGTH_SHORT).show()
                                return@onDelete
                            }
                            scope.launch {
                                mgr.removeImageAttachmentFromChat(im.stableId)
                                withContext(Dispatchers.Main) {
                                    clearPinIfMatches(im.stableId)
                                    Toast.makeText(context, R.string.msg_deleted_from_chat, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        is MessageBubbleMenuTarget.Voice -> {
                            val vm = t.item
                            val mgr = chatSession
                            if (mgr == null) {
                                Toast.makeText(context, R.string.msg_menu_not_implemented, Toast.LENGTH_SHORT).show()
                                return@onDelete
                            }
                            scope.launch {
                                mgr.removeVoiceAttachmentFromChat(vm.stableId)
                                withContext(Dispatchers.Main) {
                                    clearPinIfMatches(vm.stableId)
                                    Toast.makeText(context, R.string.msg_deleted_from_chat, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
            ),
        )
    }

    LaunchedEffect(addr) {
        if (addr == null) {
            nodes = emptyList()
            return@LaunchedEffect
        }
        while (isActive) {
            MeshNodeListDiskCache.load(context.applicationContext, addr)?.let { nodes = it }
            delay(4000)
        }
    }

    LaunchedEffect(chatListRows.size, messageSearchActive, initialScrollApplied) {
        if (messageSearchActive) return@LaunchedEffect
        if (chatListRows.isNotEmpty()) {
            if (!initialScrollApplied) {
                // При первом открытии канала прыгать сразу к последнему сообщению без видимой анимации.
                listState.scrollToItem(chatListRows.size - 1)
                initialScrollApplied = true
            } else {
                listState.animateScrollToItem(chatListRows.size - 1)
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null || addr == null) return@rememberLauncherForActivityResult
        if (!bleConnected) {
            Toast.makeText(context, "Нужен Bluetooth к ноде", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                compressImageForMesh(context, uri)
            }
            if (jpeg == null) {
                Toast.makeText(context, "Фото слишком большое или не читается (макс. ~24 КБ JPEG)", Toast.LENGTH_LONG).show()
                return@launch
            }
            val m = chatSession
            if (m == null) {
                Toast.makeText(context, "Нет соединения с чатом", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val session = UUID.randomUUID().toString().replace("-", "").take(8)
            val lines = MeshImageChunkCodec.buildLines(session, jpeg)
            sending = true
            val (ok, err) = m.sendImageLines(lines)
            sending = false
            if (ok) {
                m.appendLocalImageAttachment(jpeg.copyOf(), "local_${session}_${System.currentTimeMillis()}")
            } else {
                Toast.makeText(
                    context,
                    err ?: "Не удалось отправить фото",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun tryAppendLocationCoordinatesToDraft() {
        if (MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext)) {
            Toast.makeText(
                context,
                "Передача координат отключена в профиле (узел → Местоположение)",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val loc = MeshPhoneLocationToMeshSender.lastKnownLocationOrNull(context) ?: run {
            Toast.makeText(
                context,
                "Нет данных о местоположении — включите GPS или откройте карту",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val uri = BeaconShareLink.buildUri(
            BeaconSharePayload(
                lat = loc.latitude,
                lon = loc.longitude,
                title = "Моя позиция",
                ttlMs = MapBeaconViewModel.LEGACY_BEACON_TTL_MS,
                color = "#39E7FF",
                channelId = mapChannelIdForIndex(channel.index),
                channelIndex = channel.index,
                channelTitle = channel.name.ifBlank { "ch ${channel.index}" },
            ),
        )
        val prefix = when {
            draft.isEmpty() -> ""
            draft.endsWith("\n") -> ""
            else -> " "
        }
        draft = clampChannelChatDraftUtf8(draft + prefix + uri)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!ok) {
            Toast.makeText(context, "Нужно разрешение на геолокацию", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        tryAppendLocationCoordinatesToDraft()
    }

    var pendingVoiceAfterPermission by remember { mutableStateOf(false) }

    val chatSessionRef = rememberUpdatedState(chatSession)
    val addrRef = rememberUpdatedState(addr)
    val bleRef = rememberUpdatedState(bleConnected)

    val enqueueVoiceSend: () -> Unit = remember(scope, context, voiceMgr) {
        {
            voiceRecording = false
            voiceLocked = false
            val m = chatSessionRef.value
            if (m != null) {
                scope.launch {
                    try {
                        sending = true
                        val (enc, dur) = withContext(Dispatchers.Default) {
                            voiceMgr.stopRecordingAndEncode()
                        }
                        voiceElapsedMs = 0L
                        if (enc.isEmpty()) {
                            Toast.makeText(context, "Запись слишком короткая", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        try {
                            val (ok, err) = m.sendVoicePayload(enc, dur)
                            if (!ok) {
                                Toast.makeText(
                                    context,
                                    err ?: "Не удалось отправить голос",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        } catch (t: Throwable) {
                            if (t is CancellationException) return@launch
                            Toast.makeText(
                                context,
                                t.message ?: "Ошибка отправки голоса",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } finally {
                        sending = false
                    }
                }
            } else {
                voiceMgr.cancelRecording()
                voiceElapsedMs = 0L
            }
        }
    }

    val cancelVoiceRecording: () -> Unit = remember(voiceMgr) {
        {
            voiceMgr.cancelRecording()
            voiceRecording = false
            voiceLocked = false
            voiceElapsedMs = 0L
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            pendingVoiceAfterPermission = false
            Toast.makeText(context, "Нужно разрешение на микрофон", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (!pendingVoiceAfterPermission) return@rememberLauncherForActivityResult
        pendingVoiceAfterPermission = false
        val a = addrRef.value
        if (a != null && bleRef.value && chatSessionRef.value != null) {
            if (voiceMgr.startRecording()) {
                voiceRecording = true
                voiceLocked = false
                voiceElapsedMs = 0L
            } else {
                Toast.makeText(context, "Не удалось начать запись", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(voiceRecording) {
        while (voiceRecording) {
            delay(100)
            voiceElapsedMs += 100L
            if (voiceElapsedMs >= VoiceRecordMaxDurationMs) {
                enqueueVoiceSend()
                break
            }
        }
    }

    val onVoiceMic: (VoiceMicEvent) -> Unit = remember(
        addr,
        bleConnected,
        chatSession,
        context,
        voiceMgr,
        micPermissionLauncher,
        enqueueVoiceSend,
    ) {
        { ev ->
            when (ev) {
                VoiceMicEvent.Down -> {
                    if (addr != null && bleConnected && chatSession != null) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingVoiceAfterPermission = true
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (voiceMgr.startRecording()) {
                            voiceRecording = true
                            voiceLocked = false
                            voiceElapsedMs = 0L
                        } else {
                            Toast.makeText(context, "Не удалось начать запись", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                VoiceMicEvent.LockEngaged -> {
                    voiceLocked = true
                }
                is VoiceMicEvent.Up -> {
                    if (!ev.locked && voiceRecording) {
                        enqueueVoiceSend()
                    }
                }
            }
        }
    }

    /** bottomBar: [MessageInputPanel] — IME, ответ, эмодзи, вложения. */
    val wallpaperActive = chatWallpaperResId != null
    val canUseMessageInput = addr != null && bleConnected && chatSession != null && !sending
    val conversationDensity = LocalDensity.current
    val keyboardOpenConversation = WindowInsets.ime.getBottom(conversationDensity) > 0
    val showQuickRepliesBar = messageInputFocused && keyboardOpenConversation && draft.isEmpty()
    val quickRepliesBubbleGap = 12.dp // зазор до полосы быстрых ответов, как на референсе
    val chatListBottomContentPad = 4.dp + if (showQuickRepliesBar) quickRepliesBubbleGap else 0.dp

    /** Прокрутка к низу при открытии IME (без лишнего imePadding на всю колонку — он дублировал insets). */
    LaunchedEffect(keyboardOpenConversation, chatListRows.size) {
        if (!keyboardOpenConversation || chatListRows.isEmpty()) return@LaunchedEffect
        delay(100)
        val lastIdx = chatListRows.lastIndex
        val lastVis = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVis >= lastIdx - 6) {
            listState.animateScrollToItem(lastIdx)
        }
    }
    var prevShowQuickRepliesBar by remember { mutableStateOf(false) }
    /** Когда быстрые ответы скрылись (набрали текст и т.п.), остаётся IME — подтянуть ленту к композеру/клавиатуре. */
    LaunchedEffect(showQuickRepliesBar, keyboardOpenConversation, chatListRows.size) {
        if (!keyboardOpenConversation || chatListRows.isEmpty()) {
            prevShowQuickRepliesBar = showQuickRepliesBar
            return@LaunchedEffect
        }
        if (prevShowQuickRepliesBar && !showQuickRepliesBar) {
            delay(80)
            val lastIdx = chatListRows.lastIndex
            val lastVis = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: lastIdx
            if (lastVis >= lastIdx - 6) {
                listState.animateScrollToItem(lastIdx)
            }
        }
        prevShowQuickRepliesBar = showQuickRepliesBar
    }
    LaunchedEffect(showQuickRepliesBar, keyboardOpenConversation, chatListRows.size) {
        if (!showQuickRepliesBar || !keyboardOpenConversation || chatListRows.isEmpty()) {
            return@LaunchedEffect
        }
        delay(120)
        val lastIdx = chatListRows.lastIndex
        val lastVis = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVis >= lastIdx - 6) {
            listState.animateScrollToItem(lastIdx)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
                .background(
                    when {
                        wallpaperActive -> Color.Transparent
                        matrixBackdropActive -> Color.Transparent
                        else -> MstSett.mainTabBackground
                    },
                ),
            containerColor = when {
                wallpaperActive -> Color.Transparent
                matrixBackdropActive -> Color.Transparent
                else -> MstSett.mainTabBackground
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
                val bottomInsetDp = with(LocalDensity.current) {
                    val imeBottom = WindowInsets.ime.getBottom(this)
                    val navBottom = WindowInsets.navigationBars.getBottom(this)
                    val effectiveBottom = if (imeBottom > 0) {
                        max(0, imeBottom - navBottom)
                    } else {
                        0
                    }
                    effectiveBottom.toDp()
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomInsetDp),
            ) {
                    AnimatedVisibility(
                        visible = voiceRecording,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        val recInf = rememberInfiniteTransition(label = "recBar")
                        val recWave by recInf.animateFloat(
                            initialValue = 0.12f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "recWave",
                        )
                        Column(
                    modifier = Modifier
                        .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                                modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                            Text(
                                    text = "Запись ${String.format(Locale.US, "%.1f", voiceElapsedMs / 1000f)} с",
                                    color = Color(0xFF8A9A8E),
                                    fontSize = 13.sp,
                                )
                                if (voiceLocked) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = "Запись зафиксирована",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                if (!voiceLocked) {
                            Text(
                                        text = "↑ отпустить для отправки",
                                        color = Color(0xFF5A6678),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { recWave },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                color = Color(0xFF00E676),
                                trackColor = Color.White.copy(alpha = 0.08f),
                            )
                            if (voiceLocked) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    TextButton(
                                        onClick = { enqueueVoiceSend() },
                                        enabled = !sending && chatSession != null,
                                    ) {
                                        Text("Отправить", color = Color(0xFF00E676))
                                    }
                                    TextButton(
                                        onClick = { cancelVoiceRecording() },
                                        enabled = !sending,
                                    ) {
                                        Text("Удалить", color = Color(0xFFFF8A80))
                                    }
                                }
                            }
                        }
                    }
                    // Канал и личка (DM): MeshTextChatSession, срочный формат при urgent==true.
                    fun trySendMeshConversationText(source: String, urgent: Boolean = false) {
                        when {
                            addr == null || source.isBlank() -> Unit
                            !bleConnected -> Toast.makeText(context, "Нужен Bluetooth", Toast.LENGTH_SHORT).show()
                            sendCooldownUntilMs > System.currentTimeMillis() -> {
                                val leftSec = (((sendCooldownUntilMs - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L).toInt()
                                Toast.makeText(
                                    context,
                                    "Подождите ${leftSec} с",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            else -> {
                                val trimmed = source.trim()
                                if (MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext)) {
                                    if (BeaconShareLink.parseUri(trimmed) != null ||
                                        BeaconShareLink.parseCoordinatesOnlyMessage(trimmed) != null ||
                                        BeaconShareLink.canonicalBeaconShareUriForSend(trimmed) != null
                                    ) {
                                        Toast.makeText(
                                            context,
                                            "Передача координат отключена в профиле",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                        return
                                    }
                                }
                                val substituted = BeaconShareLink.canonicalBeaconShareUriForSend(trimmed)
                                    ?: BeaconShareLink.parseCoordinatesOnlyMessage(trimmed)?.let { (lat, lon) ->
                                        BeaconShareLink.buildUri(
                                            BeaconSharePayload(
                                                lat = lat,
                                                lon = lon,
                                                title = "Координаты",
                                                ttlMs = MapBeaconViewModel.LEGACY_BEACON_TTL_MS,
                                                color = "#39E7FF",
                                                channelId = mapChannelIdForIndex(channel.index),
                                                channelIndex = channel.index,
                                                channelTitle = channel.name.ifBlank { "ch ${channel.index}" },
                                            ),
                                        )
                                    }
                                    ?: trimmed
                                val body = clampChannelChatDraftUtf8(substituted)
                                val text = if (urgent) {
                                    val id = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
                                    buildUrgentMeshMessage(id, body)
                                } else {
                                    body
                                }
                                draft = ""
                                val m = chatSession
                                if (m == null) {
                                    draft = text
                                    Toast.makeText(context, "Нет менеджера чата", Toast.LENGTH_SHORT).show()
                                } else {
                                    val r = replyDraft
                                    m.sendMessage(
                                        text,
                                        0xFFFFFFFFL,
                                        replyToPacketId = r?.replyToPacketId,
                                        replyToFromNodeNum = r?.replyToFromNodeNum,
                                        replyPreviewText = r?.quotedSnippet,
                                    )
                                    replyDraft = null
                                    sendCooldownUntilMs = System.currentTimeMillis() + 3_000L
                                }
                            }
                        }
                    }
                    MessageInputPanel(
                        useDarkTheme = true,
                        text = draft,
                        onTextChange = { draft = it },
                        onSend = { urgent -> trySendMeshConversationText(draft, urgent) },
                        onOpenGallery = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onOpenFile = { showLocalGifPicker = true },
                        onOpenPoll = { showPollComposer = true },
                        onOpenList = { showChecklistComposer = true },
                        onOpenLocation = {
                            if (MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext)) {
                                Toast.makeText(
                                    context,
                                    "Передача координат отключена в профиле",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else if (MeshPhoneLocationToMeshSender.hasLocationPermission(context)) {
                                tryAppendLocationCoordinatesToDraft()
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                        locationAttachEnabled = !hideCoordinatesTransmission,
                        attachmentsEnabled = !vipRestrictedInConversation,
                        urgentLongPressEnabled = true,
                        vipRestricted = vipRestrictedInConversation,
                        onOpenContact = {
                            Toast.makeText(context, "Контакт — в разработке", Toast.LENGTH_SHORT).show()
                        },
                        onVoiceClick = {},
                        onVoiceMicEvent = onVoiceMic,
                        reply = replyDraft,
                        onDismissReply = { replyDraft = null },
                        enabled = canUseMessageInput,
                        sending = sending,
                        sendCooldownSecondsLeft = sendCooldownSecondsLeft,
                        cursorColor = inputCursorColor,
                        byteCounterText = "${channelChatDraftUtf8ByteLen(draft)}/${MeshWireLoRaToRadioEncoder.MESH_TEXT_PAYLOAD_MAX_UTF8_BYTES}",
                        onInputFocusChange = { messageInputFocused = it },
                        showQuickReplies = showQuickRepliesBar,
                        quickRepliesEnabled = canUseMessageInput && sendCooldownSecondsLeft <= 0,
                        onQuickReply = { trySendMeshConversationText(it, urgent = false) },
                    )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
        Column(Modifier.fillMaxSize()) {
        if (!bleConnected || addr == null) {
            Text(
                text = if (addr == null) "Привяжите ноду для переписки" else "Включите Bluetooth — иначе не приходят сообщения",
                color = Color(0xFF8A9A8E),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (bleConnected && addr != null && pinnedSnapshot != null) {
            val pin = pinnedSnapshot!!
            val pinnedAuthorText = remember(pin, nodes) {
                pinnedAuthorDisplay(pin).ifBlank { " " }
            }
            ChannelPinnedTelegramBar(
                pinned = pin,
                authorText = pinnedAuthorText,
                onNavigateToMessage = {
                    val idx = flatListIndexForStableId(chatListRows, pin.stableId)
                    if (idx < 0) {
                        Toast.makeText(context, R.string.msg_pinned_not_found, Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            listState.animateScrollToItem(idx)
                        }
                    }
                },
                onUnpin = onUnpin@{
                    val a = addr ?: return@onUnpin
                    ChannelPinnedMessageStore.write(context.applicationContext, a, channel.rowKey, null)
                    pinnedSnapshot = null
                },
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
        }
        val searchTrimmed = messageSearchQuery.trim()
        val showSearchEmpty = messageSearchActive && searchTrimmed.isNotEmpty() && filteredMergedItems.isEmpty()
        if (showSearchEmpty) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.chat_conversation_search_no_results),
                    color = Color(0xFF8A9A8E),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = chatListBottomContentPad,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                items = chatListRows,
                key = { row ->
                    when (row) {
                        is ChannelChatListRow.DateHeader -> "day_${row.day.toEpochDay()}"
                        is ChannelChatListRow.Message -> {
                            val msg = row.bubble
                when (msg) {
                                is ChannelBubbleItem.VoiceMsg ->
                                    "${msg.stableId}_ds${msg.attachment.deliveryStatus}_vr${msg.attachment.voiceRecordId}"
                                else -> msg.stableId
                            }
                        }
                    }
                },
            ) { row ->
                when (row) {
                    is ChannelChatListRow.DateHeader -> DateSeparator(date = chatDayLabel(row.day))
                    is ChannelChatListRow.Message -> {
                val msg = row.bubble
                val shortB = bubbleSenderShortBadge(msg.from)
                val senderNodeIdHex = nodeIdHexFromNodeNum(msg.from)
                    ?: if (msg.mine) localNodeIdHexForBubble else null
                val senderProfileNode = bubbleSenderProfileNode(msg.from, msg.mine)
                val tLabel = timeFmt.format(Date(msg.timeMs))
                when (msg) {
                    is ChannelBubbleItem.TextMsg -> {
                        val replySnippet = msg.replyPreviewText?.takeIf { it.isNotBlank() }
                        val replyFrom = msg.replyToFromNodeNum?.let { (it and 0xFFFF_FFFFL).toUInt() }
                        val replyAuthor = if (replySnippet != null && replyFrom != null) {
                            bubbleSenderLabel(replyFrom)
                        } else {
                            null
                        }
                        val poll = remember(msg.text) { parsePollMessage(msg.text) }
                        val checklist = remember(msg.text) { parseChecklistMessage(msg.text) }
                        val localGifId = remember(msg.text) { parseLocalGifMessage(msg.text) }
                        val urgentPayload = remember(msg.text) { parseUrgentMeshMessage(msg.text) }
                        val plainChannelText = remember(msg.text, urgentPayload) {
                            when {
                                urgentPayload != null -> urgentPayload.body
                                else -> msg.text
                            }
                        }
                        val urgentAckList = remember(urgentPayload, urgentAckSnapshots) {
                            urgentPayload?.id?.let { urgentAckSnapshots[it].orEmpty() }.orEmpty()
                        }
                        val bubbleTheme = channelBubbleTheme(senderNodeIdHex)
                        val resolvedPollId = remember(poll, msg.meshPacketId, msg.stableId) {
                            poll?.pollId?.takeIf { it.isNotBlank() }
                                ?: msg.meshPacketId?.let { "pkt_${it.toString(16)}" }
                                ?: "legacy_${msg.stableId}"
                        }
                        val resolvedChecklistId = remember(checklist, msg.meshPacketId, msg.stableId) {
                            checklist?.listId?.takeIf { it.isNotBlank() }
                                ?: msg.meshPacketId?.let { "pkt_${it.toString(16)}" }
                                ?: "legacy_${msg.stableId}"
                        }
                        val networkSnapshot = pollVoteSnapshots[resolvedPollId]
                        val networkChecklistState = checklistStateSnapshots[resolvedChecklistId].orEmpty()
                        var selectedPollOptions by remember(msg.stableId) {
                            mutableStateOf(ChatPollVotePreferences.getSelectedOptions(context, msg.stableId))
                        }
                        val effectiveSelectedOptions = remember(
                            networkSnapshot,
                            selectedPollOptions,
                            poll,
                        ) {
                            val raw = networkSnapshot?.mySelection ?: selectedPollOptions
                            if (poll == null) emptySet() else raw.filter { it in poll.options.indices }.toSet()
                        }
                        val optionVoteCounts = remember(networkSnapshot, effectiveSelectedOptions) {
                            if (networkSnapshot == null) {
                                effectiveSelectedOptions.associateWith { 1 }
                            } else {
                                networkSnapshot.optionVoteCounts
                            }
                        }
                        val totalVotes = remember(networkSnapshot, optionVoteCounts) {
                            networkSnapshot?.totalVotes
                                ?: if (optionVoteCounts.isEmpty()) 0 else 1
                        }
                        ChannelMessageBubble(
                            mine = msg.mine,
                            senderNodeIdHex = senderNodeIdHex,
                            shortBadge = shortB,
                            onShortBadgeClick = senderProfileNode?.let { node -> { onOpenUserProfile(node) } },
                            viaMqtt = msg.viaMqtt,
                            timeLabel = tLabel,
                            outgoingDelivery = msg.outgoingDelivery,
                            urgentHighlight = urgentPayload != null && poll == null && checklist == null && localGifId == null,
                            urgentFooter = if (
                                urgentPayload != null && poll == null && checklist == null && localGifId == null
                            ) {
                                {
                                    UrgentMessageAckFooter(
                                        mine = msg.mine,
                                        acks = urgentAckList,
                                        localNodeNumLong = localNodeNumLong,
                                        nodes = nodes,
                                        messageFontScale = messageFontScale,
                                        canSendMesh = canUseMessageInput,
                                        onSendAck = {
                                            val m = chatSession
                                            if (m != null && bleConnected) {
                                                val ackTxt = clampChannelChatDraftUtf8(
                                                    buildUrgentAckMeshMessage(urgentPayload.id),
                                                )
                                                m.sendMessage(ackTxt)
                                            }
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                            onRetryFailedDelivery = if (
                                msg.outgoingDelivery == ChatMessageDeliveryStatus.FAILED && chatSession != null
                            ) {
                                { chatSession.retryFailedOutgoingText(msg.rowId) }
                                } else {
                                null
                            },
                            channelTextBody = if (poll == null && checklist == null && localGifId == null) {
                                plainChannelText
                            } else {
                                null
                            },
                            replyQuoteAuthor = replyAuthor,
                            replyQuoteSnippet = replySnippet,
                            onBubbleLongClick = { coords ->
                                openBubbleMenu(coords, MessageBubbleMenuTarget.Text(msg))
                            },
                            reactionChips = msg.reactionChips,
                            onReactionChipClick = { chip ->
                                val pid = msg.meshPacketId
                                val idx = MeshReactionEmojiRegistry.listIndexForEmoji(chip.emoji)
                                if (pid != null && idx != null && chatSession != null) {
                                    chatSession.sendReactionUpdate(
                                        pid.toLong() and 0xFFFF_FFFFL,
                                        idx,
                                        isAdding = !chip.includesMine,
                                    )
                                }
                            },
                            messageFontScale = messageFontScale,
                            onBeaconShareLinkClick = { payload, senderLongName ->
                                if (vipRestrictedInConversation) {
                                    VipRestrictedToast.show(context)
                                } else {
                                    onBeaconShareLinkClick(payload, senderLongName)
                                }
                            },
                            beaconShareSenderLabel = bubbleSenderLongNamePreferred(msg.from),
                            beaconCoordDisplayChannelIndex = channel.index,
                            beaconCoordDisplayChannelTitle = beaconThreadChannelTitle,
                        ) {
                            if (poll != null) {
                                PollMessageContent(
                                    poll = poll,
                                    selectedOptions = effectiveSelectedOptions,
                                    optionVoteCounts = optionVoteCounts,
                                    totalVotes = totalVotes,
                                    textColor = bubbleTheme.body,
                                    accentColor = bubbleTheme.mqttTint,
                                    mutedColor = bubbleTheme.timeMuted,
                                    messageFontScale = messageFontScale,
                                    onToggleOption = { optionIdx ->
                                        val next = if (poll.multipleAnswers) {
                                            if (effectiveSelectedOptions.contains(optionIdx)) {
                                                effectiveSelectedOptions - optionIdx
                                            } else {
                                                effectiveSelectedOptions + optionIdx
                                            }
                                        } else {
                                            if (effectiveSelectedOptions.contains(optionIdx)) emptySet() else setOf(optionIdx)
                                        }
                                        selectedPollOptions = next
                                        ChatPollVotePreferences.setSelectedOptions(context, msg.stableId, next)
                                        val m = chatSession
                                        if (m != null && bleConnected) {
                                            val voteMessage = clampChannelChatDraftUtf8(
                                                buildPollVoteMessage(
                                                    pollId = resolvedPollId,
                                                    optionIndexes = next,
                                                ),
                                            )
                                            m.sendMessage(voteMessage, 0xFFFFFFFFL)
                                        }
                                    },
                                )
                            } else if (checklist != null) {
                                ChecklistMessageContent(
                                    checklist = checklist,
                                    itemStates = networkChecklistState,
                                    localNodeNumLong = localNodeNumLong,
                                    textColor = bubbleTheme.body,
                                    mutedColor = bubbleTheme.timeMuted,
                                    messageFontScale = messageFontScale,
                                    ownerShortName = { ownerNodeNum ->
                                        ownerNodeNum?.let { num ->
                                            bubbleSenderShortBadge((num and 0xFFFF_FFFFL).toUInt())
                                        }
                                    },
                                    onToggleItem = { itemIdx, checked ->
                                        val current = networkChecklistState[itemIdx]
                                        val canSendUpdate = if (checked) {
                                            current?.checked != true
                                        } else {
                                            current?.checked == true && current.ownerNodeNum == localNodeNumLong
                                        }
                                        if (canSendUpdate) {
                                            val m = chatSession
                                            if (m != null && bleConnected) {
                                                val update = clampChannelChatDraftUtf8(
                                                    buildChecklistToggleMessage(
                                                        listId = resolvedChecklistId,
                                                        itemIndex = itemIdx,
                                                        checked = checked,
                                                    ),
                                                )
                                                m.sendMessage(update, 0xFFFFFFFFL)
                                            }
                                        }
                                    },
                                )
                            } else if (localGifId != null) {
                                LocalGifBubbleContent(
                                    gifId = localGifId,
                                    mutedColor = bubbleTheme.timeMuted,
                                    messageFontScale = messageFontScale,
                                )
                            }
                        }
                    }
                    is ChannelBubbleItem.ImageMsg -> {
                        val bmp = remember(msg.jpeg) {
                            BitmapFactory.decodeByteArray(msg.jpeg, 0, msg.jpeg.size)
                        }
                        ChannelMessageBubble(
                            mine = msg.mine,
                            senderNodeIdHex = senderNodeIdHex,
                            shortBadge = shortB,
                            onShortBadgeClick = senderProfileNode?.let { node -> { onOpenUserProfile(node) } },
                            viaMqtt = msg.viaMqtt,
                            timeLabel = tLabel,
                            outgoingDelivery = null,
                            onBubbleLongClick = { coords ->
                                openBubbleMenu(coords, MessageBubbleMenuTarget.Image(msg))
                            },
                            messageFontScale = messageFontScale,
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .heightIn(max = 220.dp)
                                        .widthIn(max = 280.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                Text(
                                    "Не удалось показать фото",
                                    color = Color(0xFFFFB3B3),
                                    fontSize = (13f * messageFontScale).sp,
                                )
                            }
                        }
                    }
                    is ChannelBubbleItem.VoiceMsg -> {
                        val voiceAckId = remember(msg.attachment.stableId, msg.attachment.voiceRecordId, msg.attachment.from) {
                            voiceReceiptAckId(msg.attachment)
                        }
                        val voiceAckList = remember(voiceAckId, urgentAckSnapshots) {
                            voiceAckId?.let { urgentAckSnapshots[it].orEmpty() }.orEmpty()
                        }
                        ChannelVoiceMessageBubble(
                            mine = msg.mine,
                            senderNodeIdHex = senderNodeIdHex,
                            shortBadge = shortB,
                            onShortBadgeClick = senderProfileNode?.let { node -> { onOpenUserProfile(node) } },
                            attach = msg.attachment,
                            viaMqtt = false,
                            timeLabel = tLabel,
                            outgoingDelivery = msg.outgoingDelivery,
                            urgentFooter = if (voiceAckId != null) {
                                {
                                    UrgentMessageAckFooter(
                                        mine = msg.mine,
                                        acks = voiceAckList,
                                        localNodeNumLong = localNodeNumLong,
                                        nodes = nodes,
                                        messageFontScale = messageFontScale,
                                        canSendMesh = canUseMessageInput,
                                        onSendAck = {
                                            val m = chatSession
                                            if (m != null && bleConnected) {
                                                val ackTxt = clampChannelChatDraftUtf8(
                                                    buildUrgentAckMeshMessage(voiceAckId),
                                                )
                                                m.sendMessage(ackTxt)
                                            }
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                            onRetryFailedDelivery = if (
                                msg.outgoingDelivery == ChatMessageDeliveryStatus.FAILED && chatSession != null
                            ) {
                                {
                                    scope.launch {
                                        try {
                                            val (ok, err) = chatSession.retryFailedVoice(msg.attachment.stableId)
                                            if (!ok && err != null) {
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (t: Throwable) {
                                            if (t is CancellationException) return@launch
                                            Toast.makeText(
                                                context,
                                                t.message ?: "Ошибка повторной отправки",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                            onBubbleLongPress = { coords ->
                                openBubbleMenu(coords, MessageBubbleMenuTarget.Voice(msg))
                            },
                            reactionChips = msg.reactionChips,
                            onReactionChipClick = { chip ->
                                val pid = msg.meshPacketId
                                val idx = MeshReactionEmojiRegistry.listIndexForEmoji(chip.emoji)
                                if (pid != null && idx != null && chatSession != null) {
                                    chatSession.sendReactionUpdate(
                                        pid.toLong() and 0xFFFF_FFFFL,
                                        idx,
                                        isAdding = !chip.includesMine,
                                    )
                                }
                            },
                            messageFontScale = messageFontScale,
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
    }
    if (showPollComposer) {
        PollComposerDialog(
            onDismiss = { showPollComposer = false },
            onSubmit = { question, options, anonymous, multipleAnswers ->
                val m = chatSession
                if (addr == null || m == null) {
                    Toast.makeText(context, "Нет соединения с чатом", Toast.LENGTH_SHORT).show()
                    return@PollComposerDialog
                }
                if (!bleConnected) {
                    Toast.makeText(context, "Нужен Bluetooth", Toast.LENGTH_SHORT).show()
                    return@PollComposerDialog
                }
                val pollText = clampChannelChatDraftUtf8(
                    buildPollMessage(
                        pollId = generatePollId(localNodeNum),
                        question = question,
                        options = options,
                        anonymous = anonymous,
                        multipleAnswers = multipleAnswers,
                    ),
                )
                m.sendMessage(pollText, 0xFFFFFFFFL)
                showPollComposer = false
            },
        )
    }
    if (showChecklistComposer) {
        ChecklistComposerSheet(
            onDismiss = { showChecklistComposer = false },
            onSubmit = { title, items ->
                val m = chatSession
                if (addr == null || m == null) {
                    Toast.makeText(context, "Нет соединения с чатом", Toast.LENGTH_SHORT).show()
                    return@ChecklistComposerSheet
                }
                if (!bleConnected) {
                    Toast.makeText(context, "Нужен Bluetooth", Toast.LENGTH_SHORT).show()
                    return@ChecklistComposerSheet
                }
                val listText = clampChannelChatDraftUtf8(
                    buildChecklistMessage(
                        listId = generateChecklistId(localNodeNum),
                        title = title,
                        items = items,
                    ),
                )
                m.sendMessage(listText, 0xFFFFFFFFL)
                showChecklistComposer = false
            },
        )
    }
    if (showLocalGifPicker) {
        LocalGifPickerSheet(
            onDismiss = { showLocalGifPicker = false },
            onPick = { gifId ->
                val m = chatSession
                if (addr == null || m == null) {
                    Toast.makeText(context, "Нет соединения с чатом", Toast.LENGTH_SHORT).show()
                    return@LocalGifPickerSheet
                }
                if (!bleConnected) {
                    Toast.makeText(context, "Нужен Bluetooth", Toast.LENGTH_SHORT).show()
                    return@LocalGifPickerSheet
                }
                val payload = clampChannelChatDraftUtf8(buildLocalGifMessage(gifId))
                m.sendMessage(payload, 0xFFFFFFFFL)
                showLocalGifPicker = false
            },
        )
    }
}
