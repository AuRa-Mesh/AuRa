@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.aura.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.aura.R
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.aura.bluetooth.MeshDeviceTransport
import com.example.aura.bluetooth.MeshLocationPreferences
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.bluetooth.MeshNodeRemoteActions
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.matrix.MatrixRainLayer
import com.example.aura.bluetooth.fetchMeshWireNodes
import com.example.aura.bluetooth.isMeshNodeBluetoothLinked
import com.example.aura.meshwire.MeshWireGeo
import com.example.aura.meshwire.MeshWireNodeSummary
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.mesh.MeshStreamTransportPhase
import com.example.aura.mesh.MeshStreamTransportState
import com.example.aura.mesh.nodedb.MeshNodeInfoRefreshCoordinator
import com.example.aura.mesh.nodedb.NodeInfoRefreshResult
import com.example.aura.mesh.nodedb.MeshNodeDbRepository
import com.example.aura.preferences.ProfileLocalAvatarStore
import com.example.aura.ui.components.LocalProfileAvatarCircle
import com.example.aura.ui.vip.VipAvatarFrame
import com.example.aura.ui.vip.VipAvatarFrameNodesTabScaleMultiplier
import com.example.aura.ui.vip.rememberLocalVipActive
import com.example.aura.ui.vip.rememberPeerVipActive
import com.example.aura.mesh.nodedb.MeshNodeAvatar
import com.example.aura.mesh.nodedb.MeshNodeListRowFormatter
import com.example.aura.mesh.nodedb.MeshNodeListRowUi
import com.example.aura.mesh.qr.NodeIdentityQr
import com.example.aura.mesh.qr.NodeQrBitmap
import com.example.aura.sync.AuraProfileExportQr
import com.example.aura.mesh.nodedb.MeshNodeListSorter
import com.example.aura.mesh.nodedb.NodeCard
import com.example.aura.mesh.nodedb.NodePresenceLevel
import com.example.aura.mesh.nodedb.NodesTabViewModel
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.vip.VipStatusStore
import com.example.aura.app.AppUptimeTracker
import com.example.aura.ui.map.ProfileNodeLocationMap

private val Muted = Color(0xFF2A3A4A)
private val TextDim = Color(0xFF8A95A3)
private val Accent = Color(0xFF00D4FF)
private val AccentGreen = Color(0xFF67EA94)
private val CardStroke = Color(0xFF2A3A4A)
/** Хром / чёрный киберпанк-палитра для списка узлов. */
private val ChromeBorder = Color(0xFF4A4D55)
private val ChromeBg = Color(0xFF0C0D10)
private val ChromeText = Color(0xFFE8EAED)
private val ChromeMuted = Color(0xFF7A8088)
private val SnrBad = Color(0xFFFF4D6A)
private val SnrMid = Color(0xFFFFD166)
private val SnrGood = Color(0xFF39FFB6)
/** Как у официального клиента: чёрный фон и неоново-зелёные акценты. */
private val MsGreen = Color(0xFF00E676)
private val MsTextOnCard = Color(0xFFF5F5F5)
private val MsTextDimOnCard = Color(0xFFE0E0E0)

private enum class NodeSort {
    LAST_HEARD,
    AZ,
    DISTANCE,
    HOPS,
    CHANNEL,
    VIA_MQTT,
    FAVORITES,
}

private data class NodeFilterPrefs(
    val excludeInfrastructure: Boolean = false,
    val includeUnknown: Boolean = true,
    val hideOffline: Boolean = false,
    val onlyHeard: Boolean = false,
    val onlyIgnored: Boolean = false,
    val onlyVipUsers: Boolean = false,
)

/** Любой из «старых» фильтров — сортировка принудительно как по общей сети ([NodeSort.LAST_HEARD]). */
private fun legacyNodeFiltersActive(prefs: NodeFilterPrefs): Boolean =
    prefs.excludeInfrastructure ||
        !prefs.includeUnknown ||
        prefs.hideOffline ||
        prefs.onlyHeard ||
        prefs.onlyIgnored

private fun matchesVipOnlyFilter(n: MeshWireNodeSummary, prefs: NodeFilterPrefs, hasVip: (Long) -> Boolean): Boolean {
    if (!prefs.onlyVipUsers) return true
    return hasVip(n.nodeNum)
}

private fun filterNodesForPrefs(
    list: List<MeshWireNodeSummary>,
    prefs: NodeFilterPrefs,
    nowEpochSec: Long,
    hasVip: (Long) -> Boolean,
): List<MeshWireNodeSummary> =
    list.filter { n ->
        if (prefs.excludeInfrastructure && n.isInfrastructureRole()) return@filter false
        if (!prefs.includeUnknown && n.isUnknownProfile()) return@filter false
        if (prefs.hideOffline && !n.isOnline(nowEpochSec)) return@filter false
        if (prefs.onlyHeard && (n.lastSeenEpochSec == null || n.lastSeenEpochSec <= 0L)) return@filter false
        if (prefs.onlyIgnored && !n.isIgnored) return@filter false
        if (!matchesVipOnlyFilter(n, prefs, hasVip)) return@filter false
        true
    }

private fun distanceMetersOrNull(
    self: MeshWireNodeSummary?,
    node: MeshWireNodeSummary,
): Double? {
    if (self == null) return null
    val la = self.latitude
    val lo = self.longitude
    val nla = node.latitude
    val nlo = node.longitude
    if (la == null || lo == null || nla == null || nlo == null) return null
    return MeshWireGeo.haversineMeters(la, lo, nla, nlo)
}

private fun sortedNodes(
    list: List<MeshWireNodeSummary>,
    sort: NodeSort,
    selfNode: MeshWireNodeSummary?,
    nowEpochSec: Long,
): List<MeshWireNodeSummary> {
    val dist = { n: MeshWireNodeSummary -> distanceMetersOrNull(selfNode, n) }
    return when (sort) {
        NodeSort.LAST_HEARD -> list.sortedWith(
            compareByDescending<MeshWireNodeSummary> { it.isOnline(nowEpochSec) }
                .thenByDescending { it.lastSeenEpochSec ?: 0L },
        )
        NodeSort.AZ -> list.sortedBy { it.displayLongName().lowercase(Locale.ROOT) }
        NodeSort.DISTANCE -> list.sortedBy { dist(it) ?: Double.POSITIVE_INFINITY }
        NodeSort.HOPS -> list.sortedBy { it.hopsAway ?: UInt.MAX_VALUE }
        NodeSort.CHANNEL -> list.sortedBy { it.channel ?: UInt.MAX_VALUE }
        NodeSort.VIA_MQTT -> list.sortedWith(
            compareByDescending<MeshWireNodeSummary> { it.viaMqtt }
                .thenByDescending { it.lastSeenEpochSec ?: 0L },
        )
        NodeSort.FAVORITES -> list.sortedWith(
            compareByDescending<MeshWireNodeSummary> { it.isFavorite }
                .thenByDescending { it.isOnline(nowEpochSec) }
                .thenByDescending { it.lastSeenEpochSec ?: 0L },
        )
    }
}

private fun MeshWireNodeSummary.nodeIdDisplay(): String = nodeIdHex

private fun toastStub(ctx: android.content.Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

private fun formatUptimeVerbose(elapsed: Long): String {
    val s = (elapsed / 1000L).coerceAtLeast(0L)
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = s % 60L
    return String.format(Locale.ROOT, "%dh %dm %ds", h, m, sec)
}

/** Отображение аптайма из сети (секунды суммарно). */
private fun formatMeshPeerUptimeSecondsFromTotalSec(totalSec: Long): String {
    val sec = totalSec.coerceAtLeast(0L)
    val d = sec / 86400L
    val h = (sec % 86400L) / 3600L
    val m = (sec % 3600L) / 60L
    return "${d}д ${h}ч ${m}м"
}

private fun formatPeerNetworkUptimeUi(node: MeshWireNodeSummary, nowEpochSec: Long): String {
    val pr = node.peerReportedUptimeSec ?: return "Нет данных"
    val recv = node.peerUptimeReceivedEpochSec
    val base = formatMeshPeerUptimeSecondsFromTotalSec(pr)
    if (recv == null) return base
    val stale = (nowEpochSec - recv) > 6 * 3600L
    return if (stale) "$base\nДанные уточняются..." else base
}

/** Успех + расшифровка FromRadio, иначе короткий текст об отправке или ошибка. */
private fun toastRemoteActionResult(
    ctx: android.content.Context,
    ok: Boolean,
    err: String?,
    detail: String?,
    okFallback: String,
) {
    when {
        !ok -> toastStub(ctx, err?.takeIf { it.isNotBlank() } ?: "Ошибка записи ToRadio")
        !detail.isNullOrBlank() -> Toast.makeText(ctx, detail, Toast.LENGTH_LONG).show()
        else -> toastStub(ctx, okFallback)
    }
}

private fun MeshWireNodeSummary.meshDestUInt(): UInt =
    (nodeNum and 0xFFFFFFFFL).toUInt()

/** BLE GATT, либо TCP/USB-поток в READY — иначе ToRadio из экрана узла не уйдёт. */
private fun meshToRadioAvailable(deviceAddress: String?, bleConnected: Boolean): Boolean {
    val raw = deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val norm = MeshNodeSyncMemoryStore.normalizeKey(raw)
    return when {
        MeshDeviceTransport.isTcpAddress(norm) || MeshDeviceTransport.isUsbAddress(norm) ->
            MeshStreamTransportState.ui.value.phase == MeshStreamTransportPhase.READY
        else -> bleConnected || NodeGattConnection.isReady
    }
}

private fun withLocalRadio(
    ctx: android.content.Context,
    deviceAddress: String?,
    bleConnected: Boolean,
    localNodeNum: UInt?,
    block: (String, UInt) -> Unit,
) {
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    when {
        addr == null -> toastStub(ctx, "Нет адреса подключенной ноды")
        !meshToRadioAvailable(deviceAddress, bleConnected) ->
            toastStub(ctx, "Нода не готова к ToRadio — дождитесь связи (Bluetooth / TCP / USB)")
        localNodeNum == null -> toastStub(ctx, "Нужен Node ID сессии (!xxxxxxxx)")
        else -> block(addr, localNodeNum)
    }
}

@Composable
fun NodesTabScreen(
    padding: PaddingValues,
    deviceAddress: String?,
    bleConnected: Boolean,
    localNodeNum: UInt?,
    profileAvatarPath: String?,
    onProfileAvatarPathChange: (String?) -> Unit,
    selected: MeshWireNodeSummary?,
    onSelect: (MeshWireNodeSummary) -> Unit,
    nodeListClearSignal: Int = 0,
    onClearNodeListClick: () -> Unit = {},
    matrixBackdropActive: Boolean = false,
    matrixRainAnimationEnabled: Boolean = true,
    matrixDensity: Float = 1f,
    matrixSpeed: Float = 1f,
    matrixDim: Float = 0.38f,
    /** Прямое сообщение узлу: открыть чат с черновиком @node. */
    onOpenDirectMessage: (MeshWireNodeSummary) -> Unit = {},
    /** Сканирование QR узла (список узлов): камера. */
    onOpenNodeQrScanner: () -> Unit = {},
    /** После «забыть» узел из кэша приложения — сбросить открытый профиль, если это тот же узел. */
    onNodeForgottenFromCache: (MeshWireNodeSummary) -> Unit = {},
) {
    if (selected != null) {
        NodeDetailScreen(
            padding = padding,
            node = selected,
            deviceAddress = deviceAddress,
            bleConnected = bleConnected,
            localNodeNum = localNodeNum,
            profileAvatarPath = profileAvatarPath,
            onProfileAvatarPathChange = onProfileAvatarPathChange,
            matrixBackdropActive = matrixBackdropActive,
            onOpenDirectMessage = onOpenDirectMessage,
        )
    } else {
        val layoutDir = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(layoutDir),
                    end = padding.calculateEndPadding(layoutDir),
                ),
        ) {
            NodesImmersiveListScreen(
                deviceAddress = deviceAddress,
                bleConnected = bleConnected,
                profileAvatarPath = profileAvatarPath,
                matrixBackdropActive = matrixBackdropActive,
                matrixRainAnimationEnabled = matrixRainAnimationEnabled,
                matrixDensity = matrixDensity,
                matrixSpeed = matrixSpeed,
                matrixDim = matrixDim,
                overlayBottomInset = padding.calculateBottomPadding(),
                nodeListClearSignal = nodeListClearSignal,
                onClearNodeListClick = onClearNodeListClick,
                onNodeClick = onSelect,
                onNodeForgottenFromCache = onNodeForgottenFromCache,
            )
            FloatingActionButton(
                onClick = onOpenNodeQrScanner,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 20.dp,
                        bottom = 20.dp + padding.calculateBottomPadding(),
                    ),
                containerColor = Color(0xFF153A52),
                contentColor = Color(0xFF63FFD7),
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = "Сканировать QR узла",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeProfileAsNodeDetailScreen(
    nodeId: String,
    deviceAddress: String?,
    profileAvatarPath: String?,
    onProfileAvatarPathChange: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenDirectMessage: (MeshWireNodeSummary) -> Unit = {},
) {
    val vm: NodesTabViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val allNodes by MeshNodeDbRepository.nodes.collectAsState()
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val context = LocalContext.current
    val localNodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }
    var bleConnected by remember(deviceAddress) { mutableStateOf(false) }

    LaunchedEffect(addr) {
        MeshNodeDbRepository.attachDevice(addr)
    }
    LaunchedEffect(deviceAddress) {
        bleConnected = isMeshNodeBluetoothLinked(context, deviceAddress)
        while (isActive) {
            delay(1200)
            bleConnected = isMeshNodeBluetoothLinked(context, deviceAddress)
        }
    }

    val selfNode = remember(uiState.selfNode, allNodes, localNodeNum) {
        uiState.selfNode ?: allNodes.firstOrNull { node ->
            localNodeNum != null &&
                (node.nodeNum and 0xFFFF_FFFFL) == (localNodeNum.toLong() and 0xFFFF_FFFFL)
        }
    }

    // Системный жест «назад» с левого/правого края не завершает активити и не уводит приложение в фон.
    BackHandler(enabled = true) {
        // намеренно пусто — выход только через кнопку «Назад» в панели
    }

    Scaffold(
        containerColor = Color(0xFF071425),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Профиль",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1525).copy(alpha = 0.9f),
                ),
            )
        },
    ) { padding ->
        if (selfNode != null) {
            NodeDetailScreen(
                padding = padding,
                node = selfNode,
                deviceAddress = deviceAddress,
                bleConnected = bleConnected,
                localNodeNum = localNodeNum,
                profileAvatarPath = profileAvatarPath,
                onProfileAvatarPathChange = onProfileAvatarPathChange,
                matrixBackdropActive = true,
                onOpenDirectMessage = onOpenDirectMessage,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Нет данных профиля ноды",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun NodesImmersiveListScreen(
    deviceAddress: String?,
    bleConnected: Boolean,
    profileAvatarPath: String?,
    matrixBackdropActive: Boolean,
    matrixRainAnimationEnabled: Boolean,
    matrixDensity: Float,
    matrixSpeed: Float,
    matrixDim: Float,
    /** Нижняя системная/пузырь навигации: список уходит под неё и имеет contentPadding. */
    overlayBottomInset: Dp,
    /** Увеличивается после успешной «очистки списка узлов» — повторный запрос снимка с ноды. */
    nodeListClearSignal: Int = 0,
    onClearNodeListClick: () -> Unit = {},
    onNodeClick: (MeshWireNodeSummary) -> Unit,
    onNodeForgottenFromCache: (MeshWireNodeSummary) -> Unit = {},
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val vm: NodesTabViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val nowEpochSec = uiState.nowEpochSec
    var sortBy by remember { mutableStateOf(NodeSort.LAST_HEARD) }
    var filterPrefs by remember { mutableStateOf(NodeFilterPrefs()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var refreshEpoch by remember { mutableIntStateOf(0) }
    val arrivalOrder = remember { mutableStateMapOf<Long, Long>() }
    var nextArrivalOrder by remember { mutableLongStateOf(1L) }
    var forgetDialogNode by remember { mutableStateOf<MeshWireNodeSummary?>(null) }
    LaunchedEffect(appCtx) {
        VipStatusStore.ensureLoaded(appCtx)
    }
    val vipRevision by VipStatusStore.revision.collectAsState()
    LaunchedEffect(addr) {
        MeshNodeDbRepository.attachDevice(addr)
    }
    LaunchedEffect(bleConnected) {
        vm.setBleConnected(bleConnected)
    }
    LaunchedEffect(nodeListClearSignal) {
        if (nodeListClearSignal <= 0) return@LaunchedEffect
        if (addr == null) return@LaunchedEffect
        refreshEpoch++
    }
    LaunchedEffect(addr, bleConnected, refreshEpoch, deviceAddress) {
        if (addr == null || refreshEpoch == 0 || !meshToRadioAvailable(deviceAddress, bleConnected)) return@LaunchedEffect
        val appCtx = context.applicationContext
        val snap = addr
        fetchMeshWireNodes(appCtx, snap, onSyncProgress = null) { list, _ ->
            if (list != null && list.isNotEmpty()) {
                MeshNodeDbRepository.mergeFullSnapshot(snap, list)
            }
        }
    }
    LaunchedEffect(uiState.nodes, nowEpochSec) {
        val baseline = uiState.nodes.sortedWith(
            compareByDescending<MeshWireNodeSummary> { it.isOnline(nowEpochSec) }
                .thenByDescending { it.lastSeenEpochSec ?: 0L },
        )
        if (arrivalOrder.isEmpty() && baseline.isNotEmpty()) {
            baseline.forEach { node ->
                arrivalOrder[node.nodeNum] = nextArrivalOrder++
            }
        } else {
            baseline.forEach { node ->
                if (arrivalOrder[node.nodeNum] == null) {
                    arrivalOrder[node.nodeNum] = nextArrivalOrder++
                }
            }
        }
        val existing = baseline.map { it.nodeNum }.toSet()
        arrivalOrder.keys.toList().forEach { id ->
            if (id !in existing) arrivalOrder.remove(id)
        }
    }

    val selfNodeNum = uiState.selfNode?.nodeNum
    val effectiveSort = if (legacyNodeFiltersActive(filterPrefs)) NodeSort.LAST_HEARD else sortBy
    val prefFiltered = remember(uiState.nodes, filterPrefs, nowEpochSec, vipRevision) {
        filterNodesForPrefs(uiState.nodes, filterPrefs, nowEpochSec, VipStatusStore::hasVip)
    }
    val sortedByUser = remember(prefFiltered, effectiveSort, uiState.selfNode, nowEpochSec) {
        sortedNodes(prefFiltered, effectiveSort, uiState.selfNode, nowEpochSec)
    }
    val sorted = remember(sortedByUser, nowEpochSec, arrivalOrder.keys.size, selfNodeNum) {
        sortedByUser.sortedWith(
            compareByDescending<MeshWireNodeSummary> { selfNodeNum != null && it.nodeNum == selfNodeNum }
                .thenByDescending { it.isOnline(nowEpochSec) }
                .thenByDescending { arrivalOrder[it.nodeNum] ?: Long.MIN_VALUE }
                .thenByDescending { it.lastSeenEpochSec ?: 0L },
        )
    }
    val rows = remember(sorted, uiState.selfNode, nowEpochSec) {
        MeshNodeListRowFormatter.buildRows(sorted, uiState.selfNode, nowEpochSec)
    }

    val forgetTarget = forgetDialogNode
    if (forgetTarget != null) {
        AlertDialog(
            onDismissRequest = { forgetDialogNode = null },
            containerColor = Color(0xFF0E1522),
            titleContentColor = Color(0xFFE7FCFF),
            textContentColor = Color(0xFF8CB0BF),
            title = { Text(stringResource(R.string.nodes_forget_node_title)) },
            text = { Text(stringResource(R.string.nodes_forget_node_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        forgetDialogNode = null
                        if (addr != null) {
                            MeshNodeDbRepository.forgetCachedNode(addr, forgetTarget.nodeNum)
                            onNodeForgottenFromCache(forgetTarget)
                        }
                    },
                ) {
                    Text(stringResource(R.string.nodes_forget_node_yes), color = Color(0xFF63FFD7))
                }
            },
            dismissButton = {
                TextButton(onClick = { forgetDialogNode = null }) {
                    Text(stringResource(R.string.nodes_forget_node_no), color = Color(0xFF8CB0BF))
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        if (matrixBackdropActive) {
            MatrixRainLayer(
                densityMultiplier = matrixDensity,
                speedMultiplier = matrixSpeed,
                dimOverlayAlpha = matrixDim,
                rainAnimationEnabled = matrixRainAnimationEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Сдвиг как у «Группы» на каналах; offset не расширяет layout — добавляем высоту, чтобы список заполнял экран до низа.
        val chromeLift = 75.dp
        val listBottomPad = overlayBottomInset + 96.dp
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val lazyModifier = if (constraints.hasBoundedHeight) {
                Modifier
                    .fillMaxWidth()
                    .height(maxHeight + chromeLift)
                    .offset(y = -chromeLift)
            } else {
                Modifier
                    .fillMaxSize()
                    .offset(y = -chromeLift)
            }
            LazyColumn(
                modifier = lazyModifier,
                contentPadding = PaddingValues(bottom = listBottomPad),
            ) {
            stickyHeader(key = "nodes_search_bar") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0A2036).copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color(0xFF42E6FF).copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = vm::setSearchQuery,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Поиск узлов", color = Color(0xFF8CB0BF), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF6DEBFF))
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF121C28),
                                unfocusedContainerColor = Color(0xFF121C28),
                                focusedBorderColor = Color(0xFF42E6FF),
                                unfocusedBorderColor = Color(0xFF2A3A4A),
                                cursorColor = Color(0xFF63FFD7),
                                focusedTextColor = Color(0xFFE7FCFF),
                                unfocusedTextColor = Color(0xFFE7FCFF),
                            ),
                        )
                        IconButton(
                            onClick = onClearNodeListClick,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A2535)),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.settings_clear_node_list),
                                tint = Color(0xFF63FFD7),
                            )
                        }
                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A2535)),
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Фильтры", tint = Color(0xFF63FFD7))
                        }
                    }
                }
            }
            itemsIndexed(
                items = rows,
                key = { _, row -> row.node.nodeNum },
            ) { index, row ->
                NodesImmersiveRow(
                    row = row,
                    profileAvatarPath = profileAvatarPath,
                    selfNodeNum = selfNodeNum,
                    onClick = { onNodeClick(row.node) },
                    onLongClick = {
                        val isSelf = selfNodeNum != null && row.node.nodeNum == selfNodeNum
                        when {
                            isSelf ->
                                toastStub(context, context.getString(R.string.nodes_forget_node_self_denied))
                            addr == null ->
                                toastStub(context, context.getString(R.string.nodes_forget_need_device))
                            else -> forgetDialogNode = row.node
                        }
                    },
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = Color(0xFF26D9FF).copy(alpha = 0.22f),
                        thickness = 0.6.dp,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .background(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(22.dp),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .background(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(22.dp),
                            ),
                    )
                }
            }
        }
        }
        if (rows.isEmpty()) {
            Text(
                text = if (!meshToRadioAvailable(deviceAddress, bleConnected)) {
                    "Нет связи с нодой. Подключите Bluetooth, TCP или USB."
                } else {
                    "Список узлов пуст"
                },
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.Center),
                fontSize = 14.sp,
            )
        }
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = filterSheetState,
                containerColor = Color(0xFF0C0D10),
            ) {
                NodesSortFilterSheet(
                    sort = sortBy,
                    onSort = { sortBy = it },
                    prefs = filterPrefs,
                    onPrefs = { filterPrefs = it },
                    onClearCache = {
                        if (addr == null) {
                            toastStub(context, "Нет привязанной ноды")
                        } else {
                            MeshNodeListDiskCache.clear(context.applicationContext, addr)
                            MeshNodeDbRepository.clearForDevice(addr)
                            refreshEpoch++
                            toastStub(context, "Кэш узлов очищен")
                        }
                        showFilterSheet = false
                    },
                    onDismiss = { showFilterSheet = false },
                )
            }
        }
    }
}

@Composable
private fun NodesImmersiveRow(
    row: MeshNodeListRowUi,
    profileAvatarPath: String?,
    selfNodeNum: Long?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val nameColor = if (row.nameBright) Color(0xFFE7FCFF) else Color(0xFFC7EAF3)
    val statusColor = if (row.presence == NodePresenceLevel.ONLINE) Color(0xFF63FFD7) else Color(0xFF8CB0BF)
    val battery = row.batteryPercentText ?: "—"
    val statusLine = "${row.statusCompact}  ·  $battery"
    val rightPrimary = row.distanceLine?.removePrefix("Расстояние: ") ?: row.lastSeenLine.removePrefix("Последний контакт: ")
    val rightIcon = if (row.distanceLine != null) Icons.Default.Landscape else Icons.Default.Schedule

    val immersiveBubbleShape = RoundedCornerShape(22.dp)
    // [Box] без clip у пузыря — значок VIP не обрезается скруглением [Surface].
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .shadow(elevation = 1.dp, shape = immersiveBubbleShape, clip = false)
            .background(Color(0xFF0A2036).copy(alpha = 0.78f), immersiveBubbleShape)
            .border(
                width = 1.dp,
                color = Color(0xFF42E6FF).copy(alpha = 0.22f),
                shape = immersiveBubbleShape,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isSelf = selfNodeNum != null && row.node.nodeNum == selfNodeNum
            val offlineA = if (row.presence == NodePresenceLevel.OFFLINE) 0.55f else 1f
            if (isSelf) {
                val photo = profileAvatarPath?.trim()?.takeIf { it.isNotEmpty() }
                VipAvatarFrame(
                    active = rememberLocalVipActive(),
                    avatarSize = 48.dp,
                    frameScaleMultiplier = VipAvatarFrameNodesTabScaleMultiplier,
                    useWindowPopupOverlay = false,
                    onAvatarClick = onClick,
                    nodeIdHex = row.node.nodeIdHex,
                ) {
                    if (photo != null) {
                        LocalProfileAvatarCircle(
                            filePath = photo,
                            size = 48.dp,
                            placeholderBackground = Color(0xFF1A2434),
                            placeholderIconTint = Color(0xFF8FA1B3),
                            contentDescription = "Профиль",
                            modifier = Modifier.graphicsLayer { alpha = offlineA },
                        )
                    } else {
                        MeshNodeAvatar(
                            node = row.node,
                            size = 48.dp,
                            contentAlpha = offlineA,
                        )
                    }
                }
            } else {
                VipAvatarFrame(
                    active = rememberPeerVipActive(row.node.nodeNum),
                    avatarSize = 48.dp,
                    frameScaleMultiplier = VipAvatarFrameNodesTabScaleMultiplier,
                    useWindowPopupOverlay = false,
                    onAvatarClick = onClick,
                    nodeIdHex = row.node.nodeIdHex,
                ) {
                    MeshNodeAvatar(
                        node = row.node,
                        size = 48.dp,
                        contentAlpha = offlineA,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = row.longNameBold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = statusLine,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = rightIcon,
                    contentDescription = null,
                    tint = Color(0xFF6DEBFF),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = rightPrimary,
                    color = Color(0xFFA8D7E5),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NodesListScreen(
    padding: PaddingValues,
    deviceAddress: String?,
    bleConnected: Boolean,
    localNodeNum: UInt?,
    profileAvatarPath: String?,
    onNodeClick: (MeshWireNodeSummary) -> Unit,
    nodeListClearSignal: Int = 0,
    onClearNodeListClick: () -> Unit = {},
    matrixBackdropActive: Boolean = false,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val vm: NodesTabViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    var sortBy by remember { mutableStateOf(NodeSort.LAST_HEARD) }
    var filterPrefs by remember { mutableStateOf(NodeFilterPrefs()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var refreshEpoch by remember { mutableIntStateOf(0) }
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    LaunchedEffect(appCtx) {
        VipStatusStore.ensureLoaded(appCtx)
    }
    val vipRevision by VipStatusStore.revision.collectAsState()

    LaunchedEffect(addr) {
        MeshNodeDbRepository.attachDevice(addr)
    }
    LaunchedEffect(bleConnected) {
        vm.setBleConnected(bleConnected)
    }
    LaunchedEffect(nodeListClearSignal) {
        if (nodeListClearSignal <= 0) return@LaunchedEffect
        if (addr == null) return@LaunchedEffect
        refreshEpoch++
    }
    LaunchedEffect(addr, bleConnected, refreshEpoch) {
        if (addr == null || !bleConnected || refreshEpoch == 0) return@LaunchedEffect
        val appCtx = context.applicationContext
        val snap = addr
        fetchMeshWireNodes(appCtx, snap, onSyncProgress = null) { list, _ ->
            if (list != null && list.isNotEmpty()) {
                MeshNodeDbRepository.mergeFullSnapshot(snap, list)
            }
        }
    }

    val selfNode = uiState.selfNode
    val nowEpochSec = uiState.nowEpochSec
    val onlineCount = remember(uiState.nodes, nowEpochSec) {
        uiState.nodes.count { it.isOnline(nowEpochSec, MeshNodeListSorter.DEFAULT_ONLINE_WINDOW_SEC) }
    }
    val effectiveSort = if (legacyNodeFiltersActive(filterPrefs)) NodeSort.LAST_HEARD else sortBy
    val prefFiltered = remember(uiState.nodes, filterPrefs, nowEpochSec, vipRevision) {
        filterNodesForPrefs(uiState.nodes, filterPrefs, nowEpochSec, VipStatusStore::hasVip)
    }
    val sorted = remember(prefFiltered, effectiveSort, selfNode, nowEpochSec) {
        sortedNodes(prefFiltered, effectiveSort, selfNode, nowEpochSec)
    }
    val rows = remember(sorted, selfNode, nowEpochSec) {
        MeshNodeListRowFormatter.buildRows(sorted, selfNode, nowEpochSec)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = MsGreen,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Узлы",
                        modifier = Modifier.weight(1f),
                        color = ChromeText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onClearNodeListClick,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChromeBg),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.settings_clear_node_list),
                            tint = MsGreen,
                        )
                    }
                }
                Text(
                    text = "(онлайн $onlineCount / показано ${rows.size} / всего ${uiState.nodes.size})",
                    color = ChromeMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 36.dp, top = 2.dp),
                )
            }
            if (uiState.showInitialDumpProgress && addr != null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MsGreen,
                    trackColor = ChromeBorder,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = vm::setSearchQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Фильтр", color = ChromeMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = ChromeMuted)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ChromeBg,
                        unfocusedContainerColor = ChromeBg,
                        focusedBorderColor = MsGreen,
                        unfocusedBorderColor = ChromeBorder,
                        cursorColor = MsGreen,
                        focusedTextColor = ChromeText,
                        unfocusedTextColor = ChromeText,
                    ),
                )
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChromeBg),
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Сортировка и фильтр", tint = MsGreen)
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                addr == null -> {
                    NodesEmptyBleState(noDevice = true)
                }
                !bleConnected -> {
                    if (uiState.nodes.isEmpty()) {
                        NodesEmptyBleState(noDevice = false)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                Text(
                                    "Нет BLE — показан сохранённый снимок",
                                    color = ChromeMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                            itemsIndexed(
                                items = rows,
                                key = { _, r -> r.node.nodeNum },
                            ) { _, row ->
                                NodeCard(
                                    row = row,
                                    selfNode = selfNode,
                                    profileAvatarPath = profileAvatarPath,
                                    onClick = { onNodeClick(row.node) },
                                )
                            }
                        }
                    }
                }
                uiState.showInitialDumpProgress && rows.isEmpty() && bleConnected -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MsGreen)
                    }
                }
                rows.isEmpty() && uiState.nodes.isNotEmpty() -> {
                    val msg = when {
                        searchQuery.isNotBlank() && uiState.nodes.none { n ->
                            val q = searchQuery.trim().lowercase(Locale.ROOT)
                            val hex = n.nodeIdDisplay().lowercase(Locale.ROOT)
                            n.displayLongName().lowercase(Locale.ROOT).contains(q) ||
                                n.longName.lowercase(Locale.ROOT).contains(q) ||
                                n.shortName.lowercase(Locale.ROOT).contains(q) ||
                                hex.contains(q)
                        } -> "Нет совпадений по запросу"
                        else -> "Нет узлов по выбранным фильтрам"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(msg, color = ChromeMuted, fontSize = 14.sp)
                    }
                }
                rows.isEmpty() -> {
                    NodesListEmptyState(onRefresh = { if (bleConnected) refreshEpoch++ })
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = rows,
                            key = { _, r -> r.node.nodeNum },
                        ) { _, row ->
                            NodeCard(
                                row = row,
                                selfNode = selfNode,
                                profileAvatarPath = profileAvatarPath,
                                onClick = { onNodeClick(row.node) },
                            )
                        }
                    }
                }
            }
        }
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = filterSheetState,
                containerColor = ChromeBg,
            ) {
                NodesSortFilterSheet(
                    sort = sortBy,
                    onSort = { sortBy = it },
                    prefs = filterPrefs,
                    onPrefs = { filterPrefs = it },
                    onClearCache = {
                        if (addr == null) {
                            toastStub(context, "Нет привязанной ноды")
                        } else {
                            MeshNodeListDiskCache.clear(context.applicationContext, addr)
                            MeshNodeDbRepository.clearForDevice(addr)
                            refreshEpoch++
                            toastStub(context, "Кэш узлов очищен")
                        }
                        showFilterSheet = false
                    },
                    onDismiss = { showFilterSheet = false },
                )
            }
        }
    }
}

@Composable
private fun NodesSortFilterSheet(
    sort: NodeSort,
    onSort: (NodeSort) -> Unit,
    prefs: NodeFilterPrefs,
    onPrefs: (NodeFilterPrefs) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
    ) {
        Text("Сортировать по", color = ChromeText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        val sortOptions = listOf(
            NodeSort.LAST_HEARD to "Последний раз слышен",
            NodeSort.AZ to "А-Я",
            NodeSort.DISTANCE to "Расстояние",
            NodeSort.HOPS to "Прыжков",
            NodeSort.CHANNEL to "Канал",
            NodeSort.VIA_MQTT to "через MQTT",
            NodeSort.FAVORITES to "по фаворитам",
        )
        sortOptions.forEach { (opt, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSort(opt)
                        onDismiss()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = sort == opt,
                    onClick = {
                        onSort(opt)
                        onDismiss()
                    },
                )
                Text(label, color = ChromeText, fontSize = 14.sp)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ChromeBorder)
        Text("Фильтр по", color = ChromeText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        NodeFilterCheckRow(
            checked = prefs.excludeInfrastructure,
            label = "Исключить инфраструктуру",
            onChecked = { onPrefs(prefs.copy(excludeInfrastructure = it)) },
        )
        NodeFilterCheckRow(
            checked = prefs.includeUnknown,
            label = "Включить неизвестные",
            onChecked = { onPrefs(prefs.copy(includeUnknown = it)) },
        )
        NodeFilterCheckRow(
            checked = prefs.hideOffline,
            label = "Скрыть узлы офлайн",
            onChecked = { onPrefs(prefs.copy(hideOffline = it)) },
        )
        NodeFilterCheckRow(
            checked = prefs.onlyHeard,
            label = "Отображать только слышимые узлы",
            onChecked = { onPrefs(prefs.copy(onlyHeard = it)) },
        )
        NodeFilterCheckRow(
            checked = prefs.onlyIgnored,
            label = "Показать только игнорируемые узлы",
            onChecked = { onPrefs(prefs.copy(onlyIgnored = it)) },
        )
        NodeFilterCheckRow(
            checked = prefs.onlyVipUsers,
            label = "VIP пользователи",
            onChecked = { onPrefs(prefs.copy(onlyVipUsers = it)) },
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onClearCache,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Очистить кэш узлов", color = SnrMid, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NodeFilterCheckRow(
    checked: Boolean,
    label: String,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
        )
        Text(
            label,
            color = ChromeText,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(start = 4.dp)
                .clickable { onChecked(!checked) },
        )
    }
}

@Composable
private fun NodesEmptyBleState(noDevice: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (noDevice) Icons.Default.BluetoothDisabled else Icons.Default.Bluetooth,
            contentDescription = null,
            tint = ChromeMuted,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (noDevice) "Нет привязанной ноды" else "Bluetooth не подключён",
            color = ChromeText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (noDevice) {
                "Привяжите радио в профиле, затем откройте вкладку снова."
            } else {
                "Подключите Bluetooth к ноде, чтобы подтянуть NodeDB и онлайн-статус."
            },
            color = ChromeMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun NodesListEmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.People,
            null,
            tint = ChromeBorder,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Список узлов пуст", color = ChromeText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "В NodeDB радио пока нет других узлов или выполните повторную загрузку.",
            color = ChromeMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onRefresh,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Обновить", color = MsGreen)
        }
    }
}

@Composable
private fun NodeIdentityQrDialog(
    nodeNum: UInt,
    isSelf: Boolean,
    onDismiss: () -> Unit,
) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val qrSidePx = remember(cfg, density) {
        val m = kotlin.math.min(cfg.screenWidthDp, cfg.screenHeightDp)
        (m * density.density * 0.88f).toInt().coerceIn(256, 1024)
    }
    val payload = remember(nodeNum, isSelf) {
        if (isSelf) {
            runCatching { AuraProfileExportQr.buildQrTextBlocking(context) }
                .getOrElse { NodeIdentityQr.payloadForNodeNum(nodeNum) }
        } else {
            NodeIdentityQr.payloadForNodeNum(nodeNum)
        }
    }
    val bitmap = remember(nodeNum, qrSidePx, payload) {
        NodeQrBitmap.encode(payload, qrSidePx).asImageBitmap()
    }
    val flipRotation by animateFloatAsState(
        targetValue = 180f,
        animationSpec = tween(520),
        label = "identityFlip",
    )
    var dismissAccum by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = remember(density) { with(density) { 72.dp.toPx() } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF040A12))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dismissAccum = 0f },
                        onHorizontalDrag = { _, dx -> dismissAccum += dx },
                        onDragEnd = {
                            if (kotlin.math.abs(dismissAccum) > dismissThreshold) {
                                onDismiss()
                            }
                            dismissAccum = 0f
                        },
                    )
                },
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Закрыть",
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            rotationY = flipRotation
                            cameraDistance = 12f * density.density
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (flipRotation <= 90f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A2535), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "AuRA",
                                color = Color(0xFF63FFD7),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationY = 180f
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                },
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = if (isSelf) "QR профиля (для сайта)" else "QR идентификатор узла",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isSelf) {
                        "Покажите экран сайту и отсканируйте камерой.\nСвайп влево/вправо или «назад» — закрыть."
                    } else {
                        "Покажите экран другому устройству для сканирования.\nСвайп влево/вправо или «назад» — закрыть."
                    },
                    color = ChromeMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NodeDetailScreen(
    padding: PaddingValues,
    node: MeshWireNodeSummary,
    deviceAddress: String?,
    bleConnected: Boolean,
    localNodeNum: UInt?,
    profileAvatarPath: String?,
    onProfileAvatarPathChange: (String?) -> Unit,
    matrixBackdropActive: Boolean = false,
    onOpenDirectMessage: (MeshWireNodeSummary) -> Unit = {},
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val groupsVm: GroupsViewModel = viewModel()
    val groups by groupsVm.groups.collectAsState()
    var favoriteMenuExpanded by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    val canCreateMore = groups.size < GroupsViewModel.MAX_FAV_GROUPS
    val dest = remember(node.nodeNum) { node.meshDestUInt() }
    var ignored by remember(node.nodeNum) { mutableStateOf(node.isIgnored) }
    var ignoreToggleInFlight by remember(node.nodeNum) { mutableStateOf(false) }
    LaunchedEffect(node.nodeNum, node.isIgnored) {
        ignored = node.isIgnored
    }
    val scope = rememberCoroutineScope()
    var remoteRefreshInFlight by remember(node.nodeNum) { mutableStateOf(false) }
    val isSelf = localNodeNum != null &&
        (node.nodeNum and 0xFFFF_FFFFL) == (localNodeNum.toLong() and 0xFFFF_FFFFL)
    var showDeleteAvatarConfirm by remember { mutableStateOf(false) }
    val pickProfileAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = ProfileLocalAvatarStore.copyFromUriAndPersist(context, uri)
        if (path == null) {
            Toast.makeText(context, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
        } else {
            onProfileAvatarPathChange(path)
        }
    }
    var identityQrForNodeNum by remember { mutableStateOf<UInt?>(null) }
    var identityQrIsSelf by remember { mutableStateOf(false) }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateGroupDialog = false
                newGroupName = ""
            },
            title = { Text("Новая группа") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    singleLine = true,
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFDDE8F0),
                        unfocusedTextColor = Color(0xFFDDE8F0),
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = Muted,
                        cursorColor = AccentGreen,
                        focusedLabelColor = ChromeMuted,
                        unfocusedLabelColor = ChromeMuted,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newGroupName.trim()
                        if (name.isEmpty()) {
                            Toast.makeText(context, "Введите название группы", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        groupsVm.createGroupAndAddNode(name, node.nodeNum) { ok ->
                            showCreateGroupDialog = false
                            newGroupName = ""
                            Toast.makeText(
                                context,
                                if (ok) "Узел добавлен в «$name»" else "Не удалось создать группу (лимит или ошибка)",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text("Создать", color = AccentGreen)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateGroupDialog = false
                        newGroupName = ""
                    },
                ) {
                    Text("Отмена", color = ChromeMuted)
                }
            },
        )
    }

    if (showDeleteAvatarConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAvatarConfirm = false },
            title = { Text("Удалить фото профиля?") },
            text = {
                Text(
                    "Фото будет удалено. Будет показана цветная метка по id узла — так же, как у узлов без своего фото.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAvatarConfirm = false
                        ProfileLocalAvatarStore.clear(context)
                        onProfileAvatarPathChange(null)
                    },
                ) {
                    Text("Удалить", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAvatarConfirm = false }) {
                    Text("Отмена")
                }
            },
        )
    }
    identityQrForNodeNum?.let { qrNode ->
        NodeIdentityQrDialog(
            nodeNum = qrNode,
            isSelf = identityQrIsSelf,
            onDismiss = {
                identityQrForNodeNum = null
                identityQrIsSelf = false
            },
        )
    }
    val nowSec = System.currentTimeMillis() / 1000L
    val presence = MeshNodeListRowFormatter.presenceLevel(node.lastSeenEpochSec, nowSec)
    var uptimeTicker by remember { mutableLongStateOf(AppUptimeTracker.uptimeMs()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            uptimeTicker = AppUptimeTracker.uptimeMs()
        }
    }
    val uptimeStr = remember(uptimeTicker) { formatUptimeVerbose(uptimeTicker) }
    fun requestRemoteNodeInfo(targetNodeNum: Int) {
        withLocalRadio(context, deviceAddress, bleConnected, localNodeNum) { addr, _ ->
            if (remoteRefreshInFlight) return@withLocalRadio
            remoteRefreshInFlight = true
            Toast.makeText(context, "Запрос отправлен, ожидаем ответ до 30 с", Toast.LENGTH_SHORT).show()
            scope.launch {
                val result = MeshNodeInfoRefreshCoordinator.requestRemoteNodeInfo(
                    context = context.applicationContext,
                    deviceAddress = addr,
                    targetNodeNum = targetNodeNum,
                    baseline = node,
                )
                remoteRefreshInFlight = false
                when (result) {
                    is NodeInfoRefreshResult.Success ->
                        Toast.makeText(context, "Данные обновлены", Toast.LENGTH_SHORT).show()
                    is NodeInfoRefreshResult.Cooldown ->
                        Toast.makeText(
                            context,
                            "Повтор запроса через ${result.secondsLeft} с",
                            Toast.LENGTH_SHORT,
                        ).show()
                    is NodeInfoRefreshResult.SendFailed ->
                        toastStub(context, result.message?.takeIf { it.isNotBlank() } ?: "Ошибка отправки запроса")
                    is NodeInfoRefreshResult.Timeout ->
                        Toast.makeText(context, "Нет ответа NODEINFO_APP за 30 с", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var hideCoordinatesTransmission by remember {
        mutableStateOf(MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext))
    }
    val avatarAlpha = when (presence) {
        NodePresenceLevel.ONLINE -> 1f
        NodePresenceLevel.RECENT -> 0.85f
        NodePresenceLevel.OFFLINE -> 0.45f
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val profilePhoto =
                    if (isSelf) profileAvatarPath?.trim()?.takeIf { it.isNotEmpty() } else null
                if (isSelf) {
                    VipAvatarFrame(
                        active = rememberLocalVipActive(),
                        avatarSize = 96.dp,
                        frameScaleMultiplier = VipAvatarFrameNodesTabScaleMultiplier,
                        useWindowPopupOverlay = false,
                        onAvatarClick = {
                            pickProfileAvatar.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onAvatarLongClick = if (profilePhoto != null) {
                            { showDeleteAvatarConfirm = true }
                        } else {
                            null
                        },
                        onAvatarHorizontalSwipe = {
                            identityQrForNodeNum = localNodeNum!!
                            identityQrIsSelf = true
                        },
                        nodeIdHex = node.nodeIdHex,
                    ) {
                        if (profilePhoto != null) {
                            LocalProfileAvatarCircle(
                                filePath = profilePhoto,
                                size = 96.dp,
                                placeholderBackground = Color(0xFF1A2535),
                                placeholderIconTint = Color(0xFF8FB8C8),
                                modifier = Modifier.graphicsLayer { alpha = avatarAlpha },
                                onClick = null,
                                onLongClick = null,
                                contentDescription = "Фото профиля, выбрать из галереи",
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { alpha = avatarAlpha }
                                    .clip(CircleShape),
                            ) {
                                MeshNodeAvatar(
                                    node = node,
                                    size = 96.dp,
                                    contentAlpha = 1f,
                                )
                            }
                        }
                    }
                } else {
                    VipAvatarFrame(
                        active = rememberPeerVipActive(node.nodeNum),
                        avatarSize = 96.dp,
                        frameScaleMultiplier = VipAvatarFrameNodesTabScaleMultiplier,
                        useWindowPopupOverlay = false,
                        onAvatarHorizontalSwipe = {
                            identityQrForNodeNum = node.meshDestUInt()
                            identityQrIsSelf = false
                        },
                        nodeIdHex = node.nodeIdHex,
                    ) {
                        MeshNodeAvatar(
                            node = node,
                            size = 96.dp,
                            contentAlpha = avatarAlpha,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        !isSelf -> "Свайп по аватару или рамке влево/вправо — QR узла"
                        profilePhoto != null ->
                            "Нажмите на аватар или рамку — замена фото · удержание — удаление · свайп влево/вправо — QR узла"
                        else ->
                            "Нажмите на аватар или рамку — выбор фото из галереи · свайп влево/вправо — QR узла"
                    },
                    color = ChromeMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        if (!isSelf) {
            item {
            SectionTitle("Действия", AccentGreen)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                OutlinedButton(
                    onClick = {
                        onOpenDirectMessage(node)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = AccentGreen)
                    Spacer(Modifier.size(8.dp))
                    Text("Прямое сообщение", color = Color(0xFFDDE8F0))
                }
                Box {
                    OutlinedButton(
                        onClick = { favoriteMenuExpanded = true },
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Добавить в группу",
                            tint = AccentGreen,
                        )
                    }
                    DropdownMenu(
                        expanded = favoriteMenuExpanded,
                        onDismissRequest = { favoriteMenuExpanded = false },
                    ) {
                        if (groups.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Создать группу и добавить узел",
                                        color = Color(0xFFDDE8F0),
                                    )
                                },
                                onClick = {
                                    favoriteMenuExpanded = false
                                    if (!canCreateMore) {
                                        Toast.makeText(
                                            context,
                                            "Достигнут лимит групп (${GroupsViewModel.MAX_FAV_GROUPS})",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        showCreateGroupDialog = true
                                    }
                                },
                            )
                        } else {
                            groups.forEach { g ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            g.name,
                                            color = Color(0xFFDDE8F0),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        favoriteMenuExpanded = false
                                        groupsVm.addMember(g.id, node.nodeNum)
                                        Toast.makeText(
                                            context,
                                            "Узел добавлен в «${g.name}»",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                            if (canCreateMore) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Новая группа…", color = AccentGreen) },
                                    onClick = {
                                        favoriteMenuExpanded = false
                                        showCreateGroupDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { requestRemoteNodeInfo(node.meshDestUInt().toInt()) },
                enabled = !remoteRefreshInFlight,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (remoteRefreshInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AccentGreen,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Ожидание ответа…", color = Color(0xFFDDE8F0))
                } else {
                    Icon(Icons.Default.Refresh, null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Обновить данные", color = Color(0xFFDDE8F0))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.SettingsInputAntenna, null, tint = TextDim)
                Text("Игнорировать", color = Color(0xFFDDE8F0), modifier = Modifier.weight(1f).padding(start = 8.dp))
                Switch(
                    checked = ignored,
                    enabled = !ignoreToggleInFlight,
                    onCheckedChange = { now ->
                        val previous = ignored
                        withLocalRadio(context, deviceAddress, bleConnected, localNodeNum) { addr, local ->
                            ignored = now
                            ignoreToggleInFlight = true
                            if (now) {
                                MeshNodeRemoteActions.sendAdminSetIgnored(context, addr, local, dest) { ok, err, detail ->
                                    if (ok) {
                                        ignored = true
                                        MeshNodeDbRepository.upsertNode(addr, node.copy(isIgnored = true))
                                        toastRemoteActionResult(
                                            context,
                                            true,
                                            null,
                                            detail,
                                            "Запрос игнорирования отправлен",
                                        )
                                    } else {
                                        ignored = previous
                                        toastStub(context, err ?: "Ошибка ToRadio")
                                    }
                                    ignoreToggleInFlight = false
                                }
                            } else {
                                MeshNodeRemoteActions.sendAdminRemoveIgnored(context, addr, local, dest) { ok, err, detail ->
                                    if (ok) {
                                        ignored = false
                                        MeshNodeDbRepository.upsertNode(addr, node.copy(isIgnored = false))
                                        toastRemoteActionResult(
                                            context,
                                            true,
                                            null,
                                            detail,
                                            "Снятие игнорирования отправлено",
                                        )
                                    } else {
                                        ignored = previous
                                        toastStub(context, err ?: "Ошибка ToRadio")
                                    }
                                    ignoreToggleInFlight = false
                                }
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF34C759),
                        checkedBorderColor = Color(0xFF34C759),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF3A3A3C),
                        uncheckedBorderColor = Color(0xFF3A3A3C),
                    ),
                )
            }
            Text(
                text = "Игнор блокирует все сообщения пользователя.",
                color = ChromeMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 32.dp, top = 4.dp),
            )
            }
            }
        }
        item {
            SectionTitle("Подробности", AccentGreen)
            DetailCard {
                DetailField(Icons.Default.Person, "Короткое имя", node.shortName)
                DetailField(Icons.Default.Work, "Роль устройства", node.roleLabel)
                if (isSelf) {
                    DetailField(Icons.Default.CheckCircle, "Аптайм", uptimeStr)
                } else {
                    DetailField(
                        Icons.Default.CheckCircle,
                        "Время в сети (Uptime)",
                        formatPeerNetworkUptimeUi(node, nowSec),
                    )
                }
                DetailField(Icons.Default.Schedule, "Последний раз слышен", node.lastHeardLabel)
                DetailField(Icons.Default.Numbers, "Номер узла", node.nodeNum.toString())
                DetailField(
                    icon = Icons.Default.Person,
                    label = "ID пользователя",
                    value = node.userId ?: node.nodeIdDisplay(),
                    copyValue = node.nodeIdDisplay(),
                )
            }
        }
        item {
            SectionTitle("Местоположение", AccentGreen)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D1A2E))
                    .padding(12.dp),
            ) {
                ProfileNodeLocationMap(
                    latitude = node.latitude,
                    longitude = node.longitude,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Обновление последнего местоположения", color = Color(0xFFDDE8F0), fontSize = 14.sp)
                Text(
                    text = if (node.latitude != null && node.longitude != null) {
                        "Координаты из mesh (NodeDB): " +
                            String.format(Locale.ROOT, "%.5f", node.latitude) + ", " +
                            String.format(Locale.ROOT, "%.5f", node.longitude)
                    } else {
                        "Координаты приходят из потока mesh — пока нет данных для этой ноды"
                    },
                    color = TextDim,
                    fontSize = 12.sp,
                )
                if (isSelf) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF152238))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Скрыть координаты",
                                    color = Color(0xFFDDE8F0),
                                    fontSize = 14.sp,
                                )
                                Text(
                                    "Включено — не передаём координаты в сеть, чат и настройки позиции",
                                    color = TextDim,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Switch(
                                checked = hideCoordinatesTransmission,
                                onCheckedChange = { now ->
                                    MeshLocationPreferences.setHideCoordinatesTransmission(context.applicationContext, now)
                                    hideCoordinatesTransmission = now
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentGreen,
                                    checkedTrackColor = Color(0xFF2A5C45),
                                ),
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Устройство", AccentGreen)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D1A2E))
                    .padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(Color(0xFF2D2640)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.SettingsInputAntenna, null, tint = Accent, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(12.dp))
                DetailField(Icons.Default.Map, "Оборудование", node.hardwareModel)
                Text("Поддерживается", color = AccentGreen, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(text, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DetailCard(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D1A2E))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun DetailField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    copyValue: String? = null,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier
            .padding(start = 10.dp)
            .weight(1f),
        ) {
            Text(label, color = TextDim, fontSize = 12.sp)
            Text(
                value,
                color = Color(0xFFDDE8F0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
            )
        }
        if (!copyValue.isNullOrBlank()) {
            IconButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(label, copyValue))
                    Toast.makeText(
                        context,
                        context.getString(com.example.aura.R.string.msg_menu_copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Копировать $label",
                    tint = TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
