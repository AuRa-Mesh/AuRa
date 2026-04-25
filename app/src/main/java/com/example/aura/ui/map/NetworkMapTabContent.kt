package com.example.aura.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aura.R
import com.example.aura.bluetooth.MeshLocationPreferences
import com.example.aura.bluetooth.MeshPhoneLocationToMeshSender
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.mesh.nodedb.MeshNodeDbRepository
import com.example.aura.meshwire.MeshStoredChannel
import com.example.aura.meshwire.isDefaultPskBlockedOnMapBeacons
import com.example.aura.meshwire.meshChannelDisplayTitle
import com.example.aura.meshwire.MeshWireModemPreset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.aura.meshwire.MeshWireNodeSummary
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.WellKnownTileServer
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleOpacity
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeColor
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeWidth
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconSize
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val STYLE_ASSET_PATH = "maplibre/style.json"
private const val STYLE_TILES_PLACEHOLDER = "__TILES_URL__"
private const val DEFAULT_ASSET_TILES_URL = "asset://maplibre/tiles/{z}/{x}/{y}.pbf"

/** Бесплатный векторный стиль OpenFreeMap (без ключа). */
private const val ONLINE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val NODE_SOURCE_ID = "aura-nodes-source"
private const val NODE_GLOW_LAYER_ID = "aura-nodes-glow"
private const val NODE_LAYER_ID = "aura-nodes"
private const val NODE_FEATURE_ID = "node_id"
private const val NODE_COLOR_GLOW = "#4AF263"
private const val NODE_COLOR_CORE = "#4AF263"
private const val NODE_COLOR_STROKE = "#B9FFC2"

private const val BEACON_SOURCE_ID = "beacons-source"
private const val BEACON_LAYER_ID = "beacons-layer"
private const val BEACON_ICON_MINE = "beacon-icon-mine"
private const val BEACON_ICON_OTHER = "beacon-icon-other"
private const val BEACON_FEATURE_ID = "beacon_id"
private const val BEACON_CHANNEL_IDX_PROP = "beacon_ch_idx"
private val BEACON_COLOR_PRESETS = listOf(
    "#39E7FF", "#4AF263", "#FF9E40", "#FF4444",
    "#B47AFF", "#FFD740", "#FF7EC4", "#E8F0FF",
)

private const val MY_LOCATION_SOURCE_ID = "aura-my-location-source"
private const val MY_LOCATION_GLOW_LAYER_ID = "aura-my-location-glow"
private const val MY_LOCATION_LAYER_ID = "aura-my-location"
private const val MAP_TOP_POPUP_OFFSET_DP = 72
private const val OFFLINE_MAP_REMOVE_HOLD_MS = 1500L
/** Удержание сегмента «Маяки» — запрос очистки всех меток активного канала. */
private const val MAP_BEACONS_SEGMENT_HOLD_MS = 1000L
private val ROSTOV_ON_DON_LATLNG = LatLng(47.2357, 39.7015)

private data class Node(
    val summary: MeshWireNodeSummary,
    val lat: Double,
    val lon: Double,
)

private data class PendingBeaconPoint(
    val lat: Double,
    val lon: Double,
)

private fun defaultRealChannelForMap(addr: String?): MapBeaconChannelSelection {
    val preset = addr?.let { MeshNodeSyncMemoryStore.getLora(it)?.modemPreset }
    val list = addr?.let { MeshNodeSyncMemoryStore.getChannels(it)?.channels }
        ?.filter { it.role != MeshStoredChannel.ROLE_DISABLED }
        .orEmpty()
    val first = list.firstOrNull { !it.isDefaultPskBlockedOnMapBeacons() }
    return if (first != null) {
        MapBeaconChannelSelection(
            channelId = mapChannelIdForIndex(first.index),
            channelIndex = first.index,
            channelTitle = meshChannelDisplayTitle(first, preset),
        )
    } else {
        MapBeaconChannelSelection(
            channelId = mapChannelIdForIndex(0),
            channelIndex = 0,
            channelTitle = "ch 0",
        )
    }
}

/** Каналы, которые можно выбрать для просмотра mesh-меток на карте. */
private fun mapBeaconSelectableChannels(
    channels: List<MeshStoredChannel>,
    preset: MeshWireModemPreset?,
): List<MeshStoredChannel> =
    channels.filter { !it.isDefaultPskBlockedOnMapBeacons() }

/** Канал, в который уйдёт mesh-метка при текущем выборе (учитывает «Локально» → первый реальный слот). */
private fun meshStoredChannelForMapPlacement(
    addr: String?,
    active: MapBeaconChannelSelection,
    channels: List<MeshStoredChannel>,
): MeshStoredChannel? {
    val idx = if (isLocalBeaconChannelSelection(active)) {
        defaultRealChannelForMap(addr).channelIndex
    } else {
        active.channelIndex
    }
    return channels.firstOrNull { it.index == idx }
        ?: addr?.let { MeshNodeSyncMemoryStore.getChannels(it)?.channels }
            ?.firstOrNull { it.index == idx }
}

/** Эффективное имя канала для установки маяка: при «Локально» — реальный слот под капотом. */
private fun meshTitleForBeaconPlacement(
    addr: String?,
    active: MapBeaconChannelSelection,
): String =
    if (isLocalBeaconChannelSelection(active)) {
        defaultRealChannelForMap(addr).channelTitle
    } else {
        active.channelTitle
    }

/** Синхронизация mesh после переноса / смены «только локально». */
/** Заголовок в диалоге импорта: для «Моя позиция» / «Моя метка» из чата — только longName отправителя. */
private fun importBeaconShareDialogTitle(
    payload: BeaconSharePayload,
    senderLongNameFromChat: String?,
): String {
    val raw = payload.title.trim()
    val sender = senderLongNameFromChat?.trim().orEmpty()
    val isPositionShare = raw.equals("Моя позиция", ignoreCase = true) ||
        raw.equals("Моя метка", ignoreCase = true)
    if (isPositionShare) {
        return sender.ifEmpty { payload.title.ifBlank { "Метка" } }
    }
    return payload.title.ifBlank { "Метка" }
}

private fun applyBeaconMeshSyncAfterMove(old: MapBeacon, new: MapBeacon) {
    when {
        !old.localMapInstall && !new.localMapInstall && old.channelIndex != new.channelIndex -> {
            MapBeaconSyncRepository.publishRemove(old)
            MapBeaconSyncRepository.publishAdd(new)
        }
        !old.localMapInstall && new.localMapInstall -> {
            MapBeaconSyncRepository.publishRemove(old)
        }
        old.localMapInstall && !new.localMapInstall -> {
            MapBeaconSyncRepository.publishAdd(new)
        }
        else -> Unit
    }
}

@Composable
fun NetworkMapTabContent(
    padding: PaddingValues,
    deviceAddress: String?,
    bleConnected: Boolean,
    localNodeNum: UInt?,
    /** Режим «Маяки» / «Ноды» хранится в родителе ([ChatScreen]), чтобы не сбрасываться при уходе с вкладки «Карта». */
    beaconsOnlyMode: Boolean,
    onBeaconsOnlyModeChange: (Boolean) -> Unit,
    onWriteMessageToNode: (MeshWireNodeSummary) -> Unit = {},
    pendingBeaconShareImport: BeaconSharePayload? = null,
    /** LongName отправителя сообщения в чате (подставляется в «Название метки» для «Моя позиция» / «Моя метка»). */
    pendingBeaconShareSenderLabel: String? = null,
    onPendingBeaconShareConsumed: () -> Unit = {},
    /** Пользователь отказался от импорта («Нет» / закрытие) — например вернуться на вкладку чата. */
    onBeaconShareImportCancelled: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appCtx = context.applicationContext
    val geolocationEnabledForMap = remember(appCtx) {
        MeshLocationPreferences.isProvideLocationToMesh(appCtx) &&
            !MeshLocationPreferences.isHideCoordinatesTransmission(appCtx)
    }
    val scope = rememberCoroutineScope()
    val beaconVm: MapBeaconViewModel = viewModel()

    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val repoNodes by MeshNodeDbRepository.nodes.collectAsState()
    val beacons by beaconVm.beacons.collectAsState()
    val activeChannel by beaconVm.activeChannel.collectAsState()

    var offlineNodes by remember { mutableStateOf<List<MeshWireNodeSummary>>(emptyList()) }
    var selectedNode by remember { mutableStateOf<Node?>(null) }
    var selectedBeaconId by remember { mutableStateOf<Long?>(null) }
    /** В режиме «Локально» нужен канал маяка — id могут совпадать в разных каналах. */
    var selectedBeaconChannelIndex by remember { mutableStateOf<Int?>(null) }
    var highlightedBeaconId by remember { mutableStateOf<Long?>(null) }
    var highlightedBeaconUntilMs by remember { mutableLongStateOf(0L) }
    var beaconListExpanded by remember { mutableStateOf(false) }
    var beaconColorFilter by remember { mutableStateOf<String?>(null) }
    var channelDropdownExpanded by remember { mutableStateOf(false) }
    var availableChannels by remember { mutableStateOf<List<MeshStoredChannel>>(emptyList()) }
    var channelModemPreset by remember { mutableStateOf<MeshWireModemPreset?>(null) }
    var pendingBeacon by remember { mutableStateOf<PendingBeaconPoint?>(null) }
    var beaconTitleInput by remember { mutableStateOf("") }
    var beaconTitleInputError by remember { mutableStateOf<String?>(null) }
    var beaconTtlMinutesInput by remember { mutableStateOf("1440") }
    var beaconTtlMinutesInputError by remember { mutableStateOf<String?>(null) }
    var beaconColorInput by remember { mutableStateOf(BEACON_COLOR_PRESETS[0]) }
    /** Меню «Перенести метку» в карточке выбранного маяка. */
    var beaconTransferMenuExpanded by remember { mutableStateOf(false) }
    var developerMode by remember { mutableStateOf(false) }
    var onlineTapCount by remember { mutableStateOf(0) }
    var totalTapCount by remember { mutableStateOf(0) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var myLocation by remember { mutableStateOf<LatLng?>(null) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapRef by remember { mutableStateOf<MapboxMap?>(null) }
    var mapBearingDeg by remember { mutableFloatStateOf(0f) }
    val nodeLookup = remember { mutableStateMapOf<Long, Node>() }
    val beaconTransportHints = remember { mutableStateMapOf<Long, BeaconTransportLabel>() }
    // Beacon IDs received from the network recently (value = arrival epoch ms, cleared after 30 s).
    val newBeaconIds = remember { mutableStateMapOf<Long, Long>() }

    val mbtilesFile = remember { File(appCtx.filesDir, "offline/maps/aura.mbtiles") }
    var mbtilesReady by remember { mutableStateOf(mbtilesFile.isFile) }
    var useOfflineMap by remember { mutableStateOf(mbtilesReady) }

    /** Локальный маяк по ссылке из чата (без mesh) до «Да»; после «Нет» удаляется через 10 с. */
    var importStagingBeacon by remember { mutableStateOf<MapBeacon?>(null) }
    var importShareSig by remember { mutableStateOf<String?>(null) }
    var importShareTitle by remember { mutableStateOf("") }
    var importShareTtlMinutes by remember { mutableStateOf("1440") }
    var importShareColorHex by remember { mutableStateOf(BEACON_COLOR_PRESETS[0]) }
    var importShareTitleError by remember { mutableStateOf<String?>(null) }
    var importShareTtlError by remember { mutableStateOf<String?>(null) }
    var importPrimaryMenuExpanded by remember { mutableStateOf(false) }
    var importShowMoveChannelList by remember { mutableStateOf(false) }
    var mapBubbleMessage by remember { mutableStateOf<String?>(null) }
    val mapBeaconMaxMessage = stringResource(R.string.map_beacon_max_count_message)
    val mapBeaconBlockedPskMessage = stringResource(R.string.map_beacon_blocked_default_psk)
    val mapBeaconNoPskAllowedChannelsMessage = stringResource(R.string.map_beacon_no_psk_allowed_channels)
    var showBeaconLimitDialog by remember { mutableStateOf(false) }
    var showClearAllBeaconsBubble by remember { mutableStateOf(false) }
    val mapLongClickDeps = remember {
        object {
            var beaconsOnly: Boolean = false
            var localNode: Long = 0L
            var meshTargetTitle: String = ""
            /** Создание метки пойдёт в режим «Локально» — лимит mesh не применяется. */
            var localBeaconPlacement: Boolean = false
            /** Текущий слот для mesh-метки — PSK «по умолчанию» (AQ==); long-press блокируется. */
            var meshChannelBlockedPsk: Boolean = false
            var blockedPskBubbleText: String = ""
        }
    }

    // Incremented inside onStyleLoaded → triggers recomposition → update() fires with fresh data.
    val mapStyleState = remember { mutableStateOf(0) }
    val styleVersion = mapStyleState.value  // Read here so Compose tracks it.

    DisposableEffect(mapRef) {
        val map = mapRef ?: return@DisposableEffect onDispose {}
        val listener = object : MapboxMap.OnCameraMoveListener {
            override fun onCameraMove() {
                mapBearingDeg = map.cameraPosition.bearing.toFloat()
            }
        }
        map.addOnCameraMoveListener(listener)
        mapBearingDeg = map.cameraPosition.bearing.toFloat()
        onDispose { map.removeOnCameraMoveListener(listener) }
    }

    val mbtilesServer = remember(mbtilesFile, mbtilesReady) {
        if (mbtilesReady && mbtilesFile.isFile) MbtilesHttpServer(mbtilesFile) else null
    }
    DisposableEffect(mbtilesServer) {
        mbtilesServer?.start()
        onDispose { mbtilesServer?.stop() }
    }

    val tilesUrl = remember(mbtilesServer) {
        mbtilesServer?.tileTemplateUrl() ?: DEFAULT_ASSET_TILES_URL
    }
    val styleJson = remember(appCtx, tilesUrl, mbtilesReady) {
        if (mbtilesReady) loadOfflineStyleJson(appCtx, tilesUrl) else ""
    }

    val uploadMbtilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!uriLooksLikeMbtilesFile(appCtx, uri)) {
            Toast.makeText(context, "Неверный формат карты", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    mbtilesFile.parentFile?.mkdirs()
                    appCtx.contentResolver.openInputStream(uri)?.use { input ->
                        mbtilesFile.outputStream().use { output -> input.copyTo(output) }
                    } != null && mbtilesFile.length() > 0L
                }.getOrDefault(false)
            }
            if (ok) {
                mbtilesReady = true
                useOfflineMap = true
                Toast.makeText(context, "Оффлайн карта загружена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Не удалось загрузить .mbtiles", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val clearBeaconSelection: () -> Unit = {
        selectedBeaconId = null
        selectedBeaconChannelIndex = null
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (highlightedBeaconUntilMs > 0L && nowMs >= highlightedBeaconUntilMs) {
                highlightedBeaconId = null
                highlightedBeaconUntilMs = 0L
            }
            // Remove "NEW" badge after 30 s.
            newBeaconIds.keys
                .filter { id -> (nowMs - (newBeaconIds[id] ?: 0L)) > 30_000L }
                .forEach { id -> newBeaconIds.remove(id) }
            delay(1_000L)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            beaconVm.pruneExpired(System.currentTimeMillis())
            delay(60_000L)
        }
    }
    LaunchedEffect(Unit) {
        MapBeaconSyncRepository.events.collect { ev ->
            when (ev) {
                is BeaconSyncEvent.Add -> {
                    val sel = MapBeaconActiveChannelStore.selection.value
                    val sameMeshChannel = !isLocalBeaconChannelSelection(sel) &&
                        ev.beacon.channelIndex == sel.channelIndex
                    if (!sameMeshChannel) return@collect
                    highlightedBeaconId = ev.beacon.id
                    highlightedBeaconUntilMs = System.currentTimeMillis() + 8_000L
                    beaconTransportHints[ev.beacon.id] = ev.transport
                    newBeaconIds[ev.beacon.id] = System.currentTimeMillis()
                    onBeaconsOnlyModeChange(true)
                    beaconListExpanded = true
                }
                is BeaconSyncEvent.Remove -> {
                    if (selectedBeaconId == ev.id &&
                        (selectedBeaconChannelIndex == null || selectedBeaconChannelIndex == ev.channelIndex)
                    ) {
                        clearBeaconSelection()
                    }
                    if (highlightedBeaconId == ev.id) highlightedBeaconId = null
                    beaconTransportHints.remove(ev.id)
                    newBeaconIds.remove(ev.id)
                }
            }
        }
    }
    LaunchedEffect(mbtilesReady) {
        if (!mbtilesReady) useOfflineMap = false
    }
    LaunchedEffect(addr) {
        MeshNodeDbRepository.attachDevice(addr)
        selectedNode = null
        clearBeaconSelection()
        beaconListExpanded = false
        beaconColorFilter = null
        availableChannels = addr?.let {
            MeshNodeSyncMemoryStore.getChannels(it)?.channels
        }?.filter { it.role != MeshStoredChannel.ROLE_DISABLED } ?: emptyList()
        channelModemPreset = addr?.let { MeshNodeSyncMemoryStore.getLora(it)?.modemPreset }
    }
    LaunchedEffect(addr, availableChannels, channelModemPreset, activeChannel.channelIndex, activeChannel.channelId) {
        if (isLocalBeaconChannelSelection(activeChannel)) return@LaunchedEffect
        val current = availableChannels.firstOrNull { it.index == activeChannel.channelIndex }
        if (current != null && current.isDefaultPskBlockedOnMapBeacons()) {
            val next = mapBeaconSelectableChannels(availableChannels, channelModemPreset).firstOrNull()
            if (next != null) {
                MapBeaconActiveChannelStore.setChannel(
                    next.index,
                    meshChannelDisplayTitle(next, channelModemPreset),
                )
            } else {
                MapBeaconActiveChannelStore.setLocalVirtualChannel()
            }
        }
    }
    LaunchedEffect(addr, appCtx) {
        offlineNodes = if (addr == null) {
            emptyList()
        } else {
            MeshNodeListDiskCache.load(appCtx, addr).orEmpty()
        }
    }

    val sourceNodes = remember(bleConnected, repoNodes, offlineNodes) {
        if (bleConnected) repoNodes else offlineNodes
    }
    val localNodeNumLong = (localNodeNum?.toLong() ?: 0L) and 0xFFFF_FFFFL
    val localNodeLongName = remember(sourceNodes, localNodeNumLong) {
        sourceNodes.firstOrNull { it.nodeNum == localNodeNumLong }?.displayLongName()?.trim().orEmpty()
            .ifBlank { "Моя нода" }
    }

    LaunchedEffect(
        pendingBeaconShareImport,
        pendingBeaconShareSenderLabel,
        localNodeNumLong,
        localNodeLongName,
        addr,
        channelModemPreset,
    ) {
        val s = pendingBeaconShareImport
        if (s == null) {
            importStagingBeacon = null
            importShareSig = null
            return@LaunchedEffect
        }
        val presetForImport = channelModemPreset ?: addr?.let { MeshNodeSyncMemoryStore.getLora(it)?.modemPreset }
        val resolvedTitle = addr?.let { a ->
            MeshNodeSyncMemoryStore.getChannels(a)?.channels
                ?.firstOrNull { it.index == s.channelIndex }
                ?.let { meshChannelDisplayTitle(it, presetForImport) }
        }
        val importChannel = addr?.let { a ->
            MeshNodeSyncMemoryStore.getChannels(a)?.channels?.firstOrNull { it.index == s.channelIndex }
        }
        if (importChannel?.isDefaultPskBlockedOnMapBeacons() == true) {
            mapBubbleMessage = mapBeaconBlockedPskMessage
            onPendingBeaconShareConsumed()
            importStagingBeacon = null
            importShareSig = null
            return@LaunchedEffect
        }
        MapBeaconActiveChannelStore.setChannel(s.channelIndex, s.channelTitle)
        onBeaconsOnlyModeChange(true)
        val sig = "${s.lat}|${s.lon}|${s.channelIndex}|${s.channelId}"
        val alreadyStaged = importShareSig == sig && importStagingBeacon != null
        if (!alreadyStaged) {
            val dialogTitle = importBeaconShareDialogTitle(s, pendingBeaconShareSenderLabel)
            importShareTitle = dialogTitle
            importShareTtlMinutes = (s.ttlMs / 60_000L).coerceAtLeast(1L).toString()
            importShareColorHex = s.color.ifBlank { BEACON_COLOR_PRESETS[0] }
            importShareTitleError = null
            importShareTtlError = null
            val excludeFromLimit = importStagingBeacon?.id
            importStagingBeacon?.let { prev ->
                beaconVm.removeBeaconAny(prev.id, prev.channelIndex)
            }
            val created = beaconVm.addBeacon(
                title = dialogTitle,
                latitude = s.lat,
                longitude = s.lon,
                creatorNodeNum = localNodeNumLong,
                creatorLongName = localNodeLongName.ifBlank { "?" },
                channelId = s.channelId,
                channelIndex = s.channelIndex,
                ttlMs = s.ttlMs.coerceAtLeast(MapBeaconViewModel.MIN_BEACON_TTL_MS),
                color = importShareColorHex.ifBlank { "#39E7FF" },
                fromChatLink = true,
                excludeFromLimitCountId = excludeFromLimit,
            )
            if (created == null) {
                mapBubbleMessage = mapBeaconMaxMessage
                onPendingBeaconShareConsumed()
                importShareSig = null
                importStagingBeacon = null
                return@LaunchedEffect
            }
            importStagingBeacon = created
            importShareSig = sig
        }
        importStagingBeacon?.let { b ->
            selectedBeaconId = b.id
            selectedBeaconChannelIndex = b.channelIndex
        } ?: run {
            selectedBeaconId = null
            selectedBeaconChannelIndex = null
        }
        selectedNode = null
    }

    LaunchedEffect(mapBubbleMessage) {
        val msg = mapBubbleMessage ?: return@LaunchedEffect
        delay(3200L)
        if (mapBubbleMessage == msg) {
            mapBubbleMessage = null
        }
    }

    val isLocalChannelView = isLocalBeaconChannelSelection(activeChannel)
    /** Метки с эфира: только слот LoRa, выбранный в выпадающем меню слева сверху (тот же индекс, что в JSON `c` / ToRadio). */
    val beaconsForChannel = remember(beacons, activeChannel.channelIndex, activeChannel.channelId, isLocalChannelView) {
        if (isLocalChannelView) {
            beacons.filter { it.fromChatLink || it.localMapInstall }
        } else {
            // Индекс слота LoRa совпадает с «группой» на ноде; строка channel_id могла расходиться между версиями.
            beacons.filter { it.channelIndex == activeChannel.channelIndex && !it.localMapInstall }
        }
    }
    val activeBeaconCount = beaconsForChannel.size
    val filteredBeaconsForPanel = remember(beaconsForChannel, beaconColorFilter) {
        if (beaconColorFilter != null) beaconsForChannel.filter { it.color == beaconColorFilter }
        else beaconsForChannel
    }
    // Mesh-метки канала — только в режиме «Маяки»; в «Ноды» на карте только точки узлов.
    // Фильтр по цвету — при включённой панели пресетов в режиме «Маяки».
    val visibleBeacons = remember(beaconsForChannel, beaconColorFilter, beaconsOnlyMode) {
        if (!beaconsOnlyMode) {
            emptyList()
        } else if (beaconColorFilter != null) {
            beaconsForChannel.filter { it.color == beaconColorFilter }
        } else {
            beaconsForChannel
        }
    }
    val nodes = remember(sourceNodes) {
        sourceNodes.mapNotNull { summary ->
            val lat = summary.latitude ?: return@mapNotNull null
            val lon = summary.longitude ?: return@mapNotNull null
            Node(summary = summary, lat = lat, lon = lon)
        }
    }
    /** Точки нод на карте только в режиме «Ноды». */
    val mapNodes = remember(nodes, beaconsOnlyMode) {
        if (beaconsOnlyMode) emptyList() else nodes
    }
    val onlineCount = sourceNodes.count { it.isOnline(nowMs / 1000L) }
    val selectedBeacon = visibleBeacons.firstOrNull { b ->
        b.id == selectedBeaconId && (
            !isLocalChannelView ||
                selectedBeaconChannelIndex == null ||
                b.channelIndex == selectedBeaconChannelIndex
            )
    }
    val canDeleteSelectedBeacon = selectedBeacon?.let {
        developerMode || it.creatorNodeNum == localNodeNumLong
    } == true
    val onBeaconFromListClick: (MapBeacon) -> Unit = { beacon ->
        selectedNode = null
        beaconListExpanded = false
        selectedBeaconId = beacon.id
        selectedBeaconChannelIndex = beacon.channelIndex
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        mapRef?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(beacon.latitude, beacon.longitude), 14.7),
            850,
        )
    }
    val onDeleteBeaconFromList: (MapBeacon) -> Unit = { beacon ->
        val isOwner = beacon.creatorNodeNum == localNodeNumLong
        if (developerMode || isOwner) {
            if (developerMode) {
                beaconVm.removeBeaconAny(beacon.id, beacon.channelIndex)
            } else {
                beaconVm.removeBeacon(beacon.id, localNodeNumLong, beacon.channelId)
            }
            MapBeaconSyncRepository.publishRemoveIfMeshVisible(beacon)
            if (selectedBeaconId == beacon.id &&
                (selectedBeaconChannelIndex == null || selectedBeaconChannelIndex == beacon.channelIndex)
            ) {
                clearBeaconSelection()
            }
            if (highlightedBeaconId == beacon.id) highlightedBeaconId = null
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(activeChannel.channelId) {
        if (!isLocalBeaconChannelSelection(activeChannel)) {
            selectedBeaconChannelIndex = null
        }
    }
    LaunchedEffect(selectedBeaconId) {
        if (selectedBeaconId == null) {
            beaconTransferMenuExpanded = false
        } else {
            // Верхняя карточка маяка и выпадающая панель «Метки» не показываются одновременно.
            beaconListExpanded = false
        }
    }

    LaunchedEffect(beaconListExpanded) {
        if (beaconListExpanded) {
            clearBeaconSelection()
        }
    }

    // Fly camera to the selected beacon whenever selection changes (from list tap or map tap).
    LaunchedEffect(selectedBeaconId, selectedBeaconChannelIndex, visibleBeacons) {
        val id = selectedBeaconId ?: return@LaunchedEffect
        val beacon = visibleBeacons.firstOrNull { b ->
            b.id == id && (
                !isLocalChannelView ||
                    selectedBeaconChannelIndex == null ||
                    b.channelIndex == selectedBeaconChannelIndex
                )
        } ?: return@LaunchedEffect
        var waited = 0
        while (mapRef == null && waited < 10) { delay(100); waited++ }
        val map = mapRef ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(beacon.latitude, beacon.longitude), 14.7),
            850,
        )
    }

    // Keep nodeLookup in sync for click-handler queries.
    LaunchedEffect(nodes) {
        nodeLookup.clear()
        nodes.forEach { node -> nodeLookup[node.summary.nodeNum] = node }
    }
    // Reset style-readiness flag when map is recreated (online ↔ offline switch).
    LaunchedEffect(mapBubbleMessage) {
        val msg = mapBubbleMessage ?: return@LaunchedEffect
        delay(3_200L)
        if (mapBubbleMessage == msg) mapBubbleMessage = null
    }

    LaunchedEffect(useOfflineMap && mbtilesReady) {
        mapStyleState.value = 0
    }
    // Refresh beacon layers whenever the visible beacon list or style changes.
    // Uses mapView.post { } so MapLibre mutations run AFTER Compose's current frame,
    // avoiding conflicts with the Compose render pipeline.
    LaunchedEffect(
        beaconsOnlyMode,
        visibleBeacons,
        styleVersion,
        highlightedBeaconId,
        selectedBeaconId,
        selectedBeaconChannelIndex,
        activeChannel.channelId,
    ) {
        if (styleVersion == 0) return@LaunchedEffect
        val mv = mapViewRef ?: return@LaunchedEffect
        val snapshotBeacons = visibleBeacons.toList()
        val snapshotNodeNum = localNodeNumLong
        val snapshotHighlight = highlightedBeaconId
        val snapshotSelId = selectedBeaconId
        val snapshotSelCh = selectedBeaconChannelIndex
        val snapshotIsLocalAgg = activeChannel.channelId == LOCAL_BEACON_CHANNEL_ID

        // Post MapLibre style mutations outside Compose's frame.
        mv.post {
            val map = mapRef ?: return@post
            val style = map.style ?: return@post

            // Always remove existing beacon layers first (must come before removeSource).
            listOf(BEACON_LAYER_ID, "beacons-glow").forEach { id ->
                if (style.getLayer(id) != null) style.removeLayer(id)
            }
            if (style.getSource(BEACON_SOURCE_ID) != null) style.removeSource(BEACON_SOURCE_ID)

            // Nothing to draw — layers already removed above, so we're done.
            if (snapshotBeacons.isEmpty()) return@post

            // Build GeoJSON feature collection.
            val features = snapshotBeacons.map { beacon ->
                val isSelected = when {
                    snapshotSelId != null && beacon.id == snapshotSelId &&
                        (!snapshotIsLocalAgg || snapshotSelCh == null || beacon.channelIndex == snapshotSelCh) -> true
                    snapshotHighlight != null && beacon.id == snapshotHighlight -> true
                    else -> false
                }
                Feature.fromGeometry(Point.fromLngLat(beacon.longitude, beacon.latitude)).apply {
                    addStringProperty(BEACON_FEATURE_ID, beacon.id.toString())
                    addStringProperty(BEACON_CHANNEL_IDX_PROP, beacon.channelIndex.toString())
                    addStringProperty("title", beacon.title)
                    addStringProperty("color", beacon.color.ifBlank { "#39E7FF" })
                    addBooleanProperty("mine", beacon.creatorNodeNum == snapshotNodeNum)
                    addBooleanProperty("selected", isSelected)
                    // Glow radius: larger when selected so the beacon stands out.
                    addNumberProperty("glowR", if (isSelected) 16.0 else 10.0)
                    addNumberProperty("coreR", if (isSelected) 9.0 else 5.4)
                }
            }

            // Source created WITH data — avoids the setGeoJson-on-empty-source rendering gap.
            style.addSource(GeoJsonSource(BEACON_SOURCE_ID, FeatureCollection.fromFeatures(features)))
            style.addLayer(
                CircleLayer("beacons-glow", BEACON_SOURCE_ID).withProperties(
                    // Data-driven radius: selected beacons pulse larger.
                    circleRadius(Expression.get("glowR")),
                    circleColor(Expression.get("color")),
                    circleOpacity(0.30f),
                ),
            )
            style.addLayer(
                CircleLayer(BEACON_LAYER_ID, BEACON_SOURCE_ID).withProperties(
                    circleRadius(Expression.get("coreR")),
                    circleColor(Expression.get("color")),
                    circleStrokeColor("#FFFFFF"),
                    // Thicker stroke when selected.
                    circleStrokeWidth(
                        Expression.switchCase(
                            Expression.get("selected"),
                            Expression.literal(2.5f),
                            Expression.literal(1.2f),
                        ),
                    ),
                    circleOpacity(1f),
                ),
            )
        }
    }
    LaunchedEffect(beaconsOnlyMode) {
        if (!beaconsOnlyMode) {
            clearBeaconSelection()
            pendingBeacon = null
        }
    }
    LaunchedEffect(beaconsOnlyMode, appCtx) {
        if (!beaconsOnlyMode) {
            myLocation = null
            return@LaunchedEffect
        }
        while (true) {
            myLocation = MeshPhoneLocationToMeshSender.lastKnownLocationOrNull(appCtx)?.let {
                LatLng(it.latitude, it.longitude)
            }
            delay(5_000L)
        }
    }

    // When entering beacon mode, auto-center the camera:
    // 1) on current GPS position, or
    // 2) on the beacon that expires soonest, or
    // 3) stay put (last camera position before mode switch).
    LaunchedEffect(beaconsOnlyMode) {
        if (!beaconsOnlyMode) return@LaunchedEffect
        // Wait up to 1 s for the map to be ready after style load.
        var waited = 0
        while (mapRef == null && waited < 10) { delay(100); waited++ }
        val map = mapRef ?: return@LaunchedEffect

        val loc = MeshPhoneLocationToMeshSender.lastKnownLocationOrNull(appCtx)
        if (loc != null) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14.0),
                800,
            )
            return@LaunchedEffect
        }

        val soonest = visibleBeacons.minByOrNull { it.timestampCreated + it.ttlMs }
        if (soonest != null) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(soonest.latitude, soonest.longitude), 14.0),
                800,
            )
        }
        // else: keep current camera position (last known view before switching modes)
    }

    mapLongClickDeps.beaconsOnly = beaconsOnlyMode
    mapLongClickDeps.localNode = localNodeNumLong
    mapLongClickDeps.meshTargetTitle = meshTitleForBeaconPlacement(addr, activeChannel)
    mapLongClickDeps.localBeaconPlacement = isLocalBeaconChannelSelection(activeChannel)
    mapLongClickDeps.meshChannelBlockedPsk =
        meshStoredChannelForMapPlacement(addr, activeChannel, availableChannels)
            ?.isDefaultPskBlockedOnMapBeacons() == true
    mapLongClickDeps.blockedPskBubbleText = mapBeaconBlockedPskMessage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color(0xFF031022)),
    ) {
        // key() пересоздаёт View при переключении онлайн ↔ оффлайн, чтобы загрузить новый стиль.
        key(useOfflineMap && mbtilesReady) {
            val isOffline = useOfflineMap && mbtilesReady
            val mapView = rememberMapViewWithLifecycle()
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { _ ->
                    Mapbox.setConnected(!isOffline)
                    mapViewRef = mapView
                    mapView.addOnDidFailLoadingMapListener {
                        android.util.Log.e("MapLibre", "Map/style failed to load")
                    }
                    mapView.getMapAsync { mapboxMap ->
                        mapRef = mapboxMap
                        mapboxMap.uiSettings.apply {
                            isCompassEnabled = false
                            isRotateGesturesEnabled = true
                            isZoomGesturesEnabled = true
                            isScrollGesturesEnabled = true
                        }
                        val onStyleLoaded: (Style) -> Unit = { style ->
                            ensureNodeLayers(style)
                            ensureMyLocationLayers(style)
                            // Beacon layers are managed by LaunchedEffect(visibleBeacons, styleVersion)
                            // which posts a full remove+recreate via mapView.post { } after style loads.
                            val myLoc = MeshPhoneLocationToMeshSender.lastKnownLocationOrNull(appCtx)
                            if (myLoc != null) {
                                mapboxMap.moveCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(myLoc.latitude, myLoc.longitude), 14.0),
                                )
                            } else {
                                if (!geolocationEnabledForMap) {
                                    mapboxMap.moveCamera(
                                        CameraUpdateFactory.newLatLngZoom(ROSTOV_ON_DON_LATLNG, 11.5),
                                    )
                                } else {
                                    val firstNode = mapNodes.firstOrNull()
                                    if (firstNode != null) {
                                        mapboxMap.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(firstNode.lat, firstNode.lon), 11.2),
                                        )
                                    } else {
                                        val saved = MeshPhoneLocationToMeshSender.persistedLastUserLatLngOrNull(appCtx)
                                        if (saved != null) {
                                            mapboxMap.moveCamera(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(saved.first, saved.second), 12.0),
                                            )
                                        } else {
                                            mapboxMap.moveCamera(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 1.8),
                                            )
                                        }
                                    }
                                }
                            }
                            // Signal Compose that the style is ready.
                            // This triggers recomposition → update() fires with fresh data.
                            mapStyleState.value++
                        }
                        if (isOffline) {
                            mapboxMap.setStyle(Style.Builder().fromJson(styleJson), onStyleLoaded)
                        } else {
                            mapboxMap.setStyle(ONLINE_STYLE_URL, onStyleLoaded)
                        }
                        mapboxMap.addOnMapClickListener { latLng ->
                            val point = mapboxMap.projection.toScreenLocation(latLng)
                            val hit = mapboxMap.queryRenderedFeatures(point, BEACON_LAYER_ID, "beacons-glow")
                            val feat = hit.firstOrNull()
                            val beaconId = feat?.getStringProperty(BEACON_FEATURE_ID)?.toLongOrNull()
                            val beaconChIdx = feat?.getStringProperty(BEACON_CHANNEL_IDX_PROP)?.toIntOrNull()
                            if (beaconId != null) {
                                selectedBeaconId = beaconId
                                selectedBeaconChannelIndex = beaconChIdx
                                highlightedBeaconId = beaconId
                                highlightedBeaconUntilMs = System.currentTimeMillis() + 8_000L
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val vib = appCtx.getSystemService(android.os.Vibrator::class.java)
                                    vib?.vibrate(android.os.VibrationEffect.createOneShot(40L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    (appCtx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(40L)
                                }
                                selectedNode = null
                                return@addOnMapClickListener true
                            }
                            if (!beaconsOnlyMode) {
                                val nodeHit = mapboxMap.queryRenderedFeatures(point, NODE_LAYER_ID, NODE_GLOW_LAYER_ID)
                                val nodeId = nodeHit.firstOrNull()?.getStringProperty(NODE_FEATURE_ID)?.toLongOrNull()
                                val tappedNode = nodeId?.let { nodeLookup[it] }
                                if (tappedNode != null) {
                                    selectedNode = tappedNode
                                    clearBeaconSelection()
                                    return@addOnMapClickListener true
                                }
                            }
                            selectedNode = null
                            clearBeaconSelection()
                            pendingBeacon = null
                            true
                        }
                        mapboxMap.addOnMapLongClickListener { latLng ->
                            if (!mapLongClickDeps.beaconsOnly) return@addOnMapLongClickListener false
                            if (mapLongClickDeps.meshChannelBlockedPsk) {
                                mapBubbleMessage = mapLongClickDeps.blockedPskBubbleText
                                return@addOnMapLongClickListener true
                            }
                            if (!mapLongClickDeps.localBeaconPlacement &&
                                beaconVm.countActiveBeaconsForCreator(mapLongClickDeps.localNode) >=
                                MapBeaconViewModel.MAX_BEACONS_PER_CREATOR
                            ) {
                                showBeaconLimitDialog = true
                                return@addOnMapLongClickListener true
                            }
                            pendingBeacon = PendingBeaconPoint(latLng.latitude, latLng.longitude)
                            beaconTitleInput = ""
                            beaconTitleInputError = null
                            beaconTtlMinutesInput = "1440"
                            beaconTtlMinutesInputError = null
                            beaconColorInput = BEACON_COLOR_PRESETS[0]
                            true
                        }
                    }
                    mapView
                },
                update = { _ ->
                    val style = if (styleVersion > 0) mapRef?.style else null
                    if (style != null) {
                        ensureNodeLayers(style)
                        ensureMyLocationLayers(style)
                        updateNodeSource(style, mapNodes)
                        // Точка «я на карте» только в режиме «Маяки»; в «Ноды» — только ноды.
                        updateMyLocationSource(style, myLocation, visible = beaconsOnlyMode)
                    }
                },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-80).dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .border(BorderStroke(1.dp, Color(0x6642E6FF)), RoundedCornerShape(22.dp))
                .background(Color(0xC70A2036), RoundedCornerShape(22.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (beaconsOnlyMode) {
                    Box {
                        Row(
                            modifier = Modifier
                                .background(Color(0x1A42E6FF), RoundedCornerShape(12.dp))
                                .border(BorderStroke(0.5.dp, Color(0x4042E6FF)), RoundedCornerShape(12.dp))
                                .clickable {
                                    val freshChannels = addr?.let {
                                        MeshNodeSyncMemoryStore.getChannels(it)?.channels
                                    }?.filter { it.role != MeshStoredChannel.ROLE_DISABLED } ?: emptyList()
                                    availableChannels = freshChannels
                                    channelModemPreset = addr?.let { MeshNodeSyncMemoryStore.getLora(it)?.modemPreset }
                                    channelDropdownExpanded = !channelDropdownExpanded
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = activeChannel.channelTitle,
                                color = Color(0xFF8FE8FF),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать канал",
                                tint = Color(0xFF8FE8FF),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = channelDropdownExpanded,
                            onDismissRequest = { channelDropdownExpanded = false },
                            containerColor = Color(0xF2081B2F),
                        ) {
                            val localChannelActive = isLocalBeaconChannelSelection(activeChannel)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = LOCAL_BEACON_CHANNEL_TITLE,
                                        color = if (localChannelActive) Color(0xFF39E7FF) else Color(0xFFDAF7FF),
                                        fontWeight = if (localChannelActive) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.5.sp,
                                    )
                                },
                                onClick = {
                                    MapBeaconActiveChannelStore.setLocalVirtualChannel()
                                    channelDropdownExpanded = false
                                },
                            )
                            val selectableBeaconChannels =
                                mapBeaconSelectableChannels(availableChannels, channelModemPreset)
                            if (availableChannels.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Каналы не загружены",
                                            color = Color(0xFF8FE8FF).copy(alpha = 0.65f),
                                            fontSize = 12.sp,
                                        )
                                    },
                                    onClick = { channelDropdownExpanded = false },
                                )
                            } else if (selectableBeaconChannels.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            mapBeaconNoPskAllowedChannelsMessage,
                                            color = Color(0xFF8FE8FF).copy(alpha = 0.65f),
                                            fontSize = 12.sp,
                                        )
                                    },
                                    onClick = { channelDropdownExpanded = false },
                                )
                            } else {
                                selectableBeaconChannels.forEach { ch ->
                                    val chTitle = meshChannelDisplayTitle(ch, channelModemPreset)
                                    val isActive = !localChannelActive && ch.index == activeChannel.channelIndex
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = chTitle,
                                                color = if (isActive) Color(0xFF39E7FF) else Color(0xFFDAF7FF),
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.5.sp,
                                            )
                                        },
                                        onClick = {
                                            MapBeaconActiveChannelStore.setChannel(
                                                index = ch.index,
                                                title = chTitle,
                                            )
                                            channelDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Онлайн",
                            color = Color(0xFF4AF263),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(56.dp)
                                .clickable {
                                    if (developerMode) return@clickable
                                    val next = onlineTapCount + 1
                                    onlineTapCount = next
                                    if (next >= 5) {
                                        developerMode = true
                                        onlineTapCount = 0
                                        totalTapCount = 0
                                        Toast.makeText(context, "Режим разработчика включен", Toast.LENGTH_SHORT).show()
                                    }
                                },
                        )
                        Text("|", color = Color(0xFF4AF263), fontSize = 10.sp)
                        Text(
                            text = "Всего",
                            color = Color(0xFF4AF263),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(56.dp)
                                .clickable {
                                    if (!developerMode) return@clickable
                                    val next = totalTapCount + 1
                                    totalTapCount = next
                                    if (next >= 5) {
                                        developerMode = false
                                        totalTapCount = 0
                                        onlineTapCount = 0
                                        Toast.makeText(context, "Режим разработчика выключен", Toast.LENGTH_SHORT).show()
                                    }
                                },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(onlineCount.toString(), color = Color(0xFF4AF263), fontSize = 11.2.sp, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                        Text("|", color = Color(0xFF4AF263), fontSize = 11.2.sp)
                        Text(sourceNodes.size.toString(), color = Color(0xFF4AF263), fontSize = 11.2.sp, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xC70A2036),
                border = BorderStroke(1.dp, Color(0x6642E6FF)),
                modifier = Modifier.pointerInput(uploadMbtilesLauncher, mbtilesFile) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val releasedEarly = withTimeoutOrNull(OFFLINE_MAP_REMOVE_HOLD_MS) {
                            waitForUpOrCancellation()
                        }
                        if (releasedEarly != null) {
                            uploadMbtilesLauncher.launch("*/*")
                        } else {
                            val hadFile = mbtilesFile.exists()
                            if (hadFile) {
                                runCatching { mbtilesFile.delete() }
                            }
                            mbtilesReady = mbtilesFile.exists()
                            useOfflineMap = false
                            if (hadFile) {
                                Toast.makeText(context, "Оффлайн карта удалена", Toast.LENGTH_SHORT).show()
                            }
                            waitForUpOrCancellation()
                        }
                    }
                },
            ) {
                Text(
                    "Загрузить оффлайн карту .mbtiles",
                    color = Color(0xFFDAF7FF),
                    fontSize = 8.4.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapModeSwitch(
                offlineSelected = mbtilesReady && useOfflineMap,
                offlineAvailable = mbtilesReady,
                onSelectOnline = { useOfflineMap = false },
                onSelectOffline = {
                    if (mbtilesReady) useOfflineMap = true else Toast.makeText(context, "Сначала загрузите .mbtiles", Toast.LENGTH_SHORT).show()
                },
            )
            BottomMapCompass(
                bearingDeg = mapBearingDeg,
                onClick = {
                    mapRef?.let { m ->
                        val loc = MeshPhoneLocationToMeshSender.lastKnownLocationOrNull(appCtx)
                        if (loc != null) {
                            val pos = CameraPosition.Builder()
                                .target(LatLng(loc.latitude, loc.longitude))
                                .zoom(14.0)
                                .bearing(0.0)
                                .tilt(0.0)
                                .build()
                            m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 700)
                        } else {
                            val saved = MeshPhoneLocationToMeshSender.persistedLastUserLatLngOrNull(appCtx)
                            if (saved != null) {
                                val pos = CameraPosition.Builder()
                                    .target(LatLng(saved.first, saved.second))
                                    .zoom(12.0)
                                    .bearing(0.0)
                                    .tilt(0.0)
                                    .build()
                                m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 700)
                            } else {
                                val p = m.cameraPosition
                                val reset = CameraPosition.Builder(p).bearing(0.0).tilt(0.0).build()
                                m.animateCamera(CameraUpdateFactory.newCameraPosition(reset), 320)
                            }
                        }
                    }
                },
            )
            BeaconVisibilitySwitch(
                beaconsOnly = beaconsOnlyMode,
                onSetNodes = { onBeaconsOnlyModeChange(false) },
                onSetBeacons = { onBeaconsOnlyModeChange(true) },
                onHoldBeaconsSegment = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showClearAllBeaconsBubble = true
                },
            )
        }
        if (beaconsOnlyMode) {
            BeaconListToggleButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 10.dp),
                count = activeBeaconCount,
                expanded = beaconListExpanded,
                onToggle = { beaconListExpanded = !beaconListExpanded },
            )
        }
        if (beaconsOnlyMode && beaconListExpanded) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 46.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val uniqueColors = remember(beaconsForChannel) {
                    BEACON_COLOR_PRESETS.filter { preset ->
                        beaconsForChannel.any { it.color == preset }
                    }
                }
                if (uniqueColors.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xDD081B2F)),
                        border = BorderStroke(1.dp, Color(0x6652E9FF)),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 7.dp, vertical = 10.dp)
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E3A5F))
                                    .border(
                                        width = if (beaconColorFilter == null) 2.dp else 0.5.dp,
                                        color = if (beaconColorFilter == null) Color.White else Color(0x44FFFFFF),
                                        shape = CircleShape,
                                    )
                                    .clickable { beaconColorFilter = null },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✕", color = Color.White, fontSize = 10.sp)
                            }
                            uniqueColors.forEach { hex ->
                                val dotColor = try {
                                    Color(AndroidColor.parseColor(hex))
                                } catch (e: Exception) {
                                    Color(0xFF39E7FF)
                                }
                                val selected = beaconColorFilter == hex
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .border(
                                            width = if (selected) 2.5.dp else 0.dp,
                                            color = if (selected) Color.White else Color.Transparent,
                                            shape = CircleShape,
                                        )
                                        .clickable {
                                            beaconColorFilter = if (selected) null else hex
                                        },
                                )
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier
                        .width(292.dp)
                        .heightIn(max = 360.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD081B2F)),
                    border = BorderStroke(1.dp, Color(0x6652E9FF)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (filteredBeaconsForPanel.isEmpty()) {
                            Text(
                                if (beaconColorFilter != null) "Нет маячков этого цвета"
                                else "Нет активных маячков",
                                color = Color(0xFF8FE8FF),
                                fontSize = 12.sp,
                            )
                        } else {
                            filteredBeaconsForPanel.forEach { beacon ->
                                val leftMs = (beacon.ttlMs - (nowMs - beacon.timestampCreated)).coerceAtLeast(0L)
                                val distanceM = myLocation?.let {
                                    haversineMeters(it.latitude, it.longitude, beacon.latitude, beacon.longitude)
                                }
                                BeaconBubbleCard(
                                    beacon = beacon,
                                    remainMs = leftMs,
                                    distanceMeters = distanceM,
                                    transport = beaconTransportHints[beacon.id],
                                    isNew = newBeaconIds.containsKey(beacon.id),
                                    canDelete = developerMode || beacon.creatorNodeNum == localNodeNumLong,
                                    onClick = { onBeaconFromListClick(beacon) },
                                    onDelete = { onDeleteBeaconFromList(beacon) },
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedNode?.let { node ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = MAP_TOP_POPUP_OFFSET_DP.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xE6071B2E)),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x2239E7FF), RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, Color(0x6642E6FF)), RoundedCornerShape(10.dp))
                                .clickable { selectedNode = null }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("✕", color = Color(0xFFDAF7FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    Text(node.summary.displayLongName(), color = Color(0xFFDAF7FF))
                    Text("Батарея: ${node.summary.batteryPercent?.let { "$it%" } ?: "—"}", color = Color(0xFF8FE8FF))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { onWriteMessageToNode(node.summary) }) { Text("Написать сообщение") }
                    }
                }
            }
        }
        selectedBeacon?.let { beacon ->
            val leftMs = (beacon.ttlMs - (nowMs - beacon.timestampCreated)).coerceAtLeast(0L)
            val selectedBeaconTransport = beaconTransportHints[beacon.id]
            val maxBeaconPopupHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).dp
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = MAP_TOP_POPUP_OFFSET_DP.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xE6071B2E)),
            ) {
                val onDeleteThisBeacon: () -> Unit = {
                    val isOwner = beacon.creatorNodeNum == localNodeNumLong
                    if (developerMode || isOwner) {
                        if (developerMode) {
                            beaconVm.removeBeaconAny(beacon.id, beacon.channelIndex)
                        } else {
                            beaconVm.removeBeacon(beacon.id, localNodeNumLong, beacon.channelId)
                        }
                        if (selectedBeaconId == beacon.id &&
                            (selectedBeaconChannelIndex == null || selectedBeaconChannelIndex == beacon.channelIndex)
                        ) {
                            clearBeaconSelection()
                        }
                        MapBeaconSyncRepository.publishRemoveIfMeshVisible(beacon)
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .heightIn(max = maxBeaconPopupHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canDeleteSelectedBeacon) {
                            TextButton(
                                onClick = onDeleteThisBeacon,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            ) {
                                Text("Удалить маячок", color = Color(0xFFFF8A80), fontSize = 13.sp)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f, fill = true))
                        Box(
                            modifier = Modifier
                                .background(Color(0x2239E7FF), RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, Color(0x6642E6FF)), RoundedCornerShape(10.dp))
                                .clickable {
                                    val closingId = selectedBeaconId
                                    clearBeaconSelection()
                                    scope.launch {
                                        delay(1_000L)
                                        if (highlightedBeaconId == closingId) highlightedBeaconId = null
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("✕", color = Color(0xFFDAF7FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val selectedBeaconSwatch = remember(beacon.color) {
                            runCatching { Color(AndroidColor.parseColor(beacon.color.ifBlank { "#39E7FF" })) }
                                .getOrElse { Color(0xFF39E7FF) }
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(selectedBeaconSwatch),
                        )
                        Text(beacon.title, color = Color(0xFFDAF7FF), fontWeight = FontWeight.SemiBold)
                    }
                    Text("Автор: ${beaconCreatorLabel(beacon)}", color = Color(0xFF8FE8FF), fontSize = 12.sp)
                    val distanceToMeM = myLocation?.let { me ->
                        haversineMeters(me.latitude, me.longitude, beacon.latitude, beacon.longitude)
                    }
                    Text(
                        text = distanceToMeM?.let { d -> "До вас: ${formatDistance(d)}" }
                            ?: "До вас: — (нет данных о вашем местоположении)",
                        color = Color(0xFF8FE8FF),
                        fontSize = 12.sp,
                    )
                    val createdText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(beacon.timestampCreated))
                    Text(
                        "Установлено: $createdText\nИсчезнет через: ${formatRemaining(leftMs)}",
                        color = Color(0xFF8FE8FF),
                        fontSize = 12.sp,
                    )
                    val beaconShareUri = remember(beacon, activeChannel.channelId, isLocalChannelView) {
                        val chTitle = when {
                            isLocalChannelView -> "ch ${beacon.channelIndex}"
                            beacon.channelIndex == activeChannel.channelIndex -> activeChannel.channelTitle
                            else -> "ch ${beacon.channelIndex}"
                        }
                        BeaconShareLink.buildUri(
                            BeaconSharePayload(
                                lat = beacon.latitude,
                                lon = beacon.longitude,
                                title = beacon.title,
                                ttlMs = beacon.ttlMs,
                                color = beacon.color.ifBlank { "#39E7FF" },
                                channelId = beacon.channelId,
                                channelIndex = beacon.channelIndex,
                                channelTitle = chTitle,
                            ),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                String.format(Locale.US, "%.6f, %.6f", beacon.latitude, beacon.longitude),
                                color = Color(0xFF8FE8FF),
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("metka", beaconShareUri))
                                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Копировать ссылку для чата",
                                tint = Color(0xFF52E0FF),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    if (canDeleteSelectedBeacon) {
                        val movePresetForUi = channelModemPreset
                            ?: addr?.let { MeshNodeSyncMemoryStore.getLora(it)?.modemPreset }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { beaconTransferMenuExpanded = true }) {
                                Text("Перенести метку", color = Color(0xFF52E0FF))
                            }
                            DropdownMenu(
                                expanded = beaconTransferMenuExpanded,
                                onDismissRequest = { beaconTransferMenuExpanded = false },
                                containerColor = Color(0xF2081B2F),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            LOCAL_BEACON_CHANNEL_TITLE,
                                            color = Color(0xFFDAF7FF),
                                            fontSize = 14.sp,
                                        )
                                    },
                                    onClick = {
                                        beaconTransferMenuExpanded = false
                                        val b = beacon
                                        scope.launch {
                                            val back = defaultRealChannelForMap(addr)
                                            val moved = beaconVm.moveBeaconToChannelSuspend(
                                                b,
                                                back.channelId,
                                                back.channelIndex,
                                                localMapInstall = true,
                                            )
                                            applyBeaconMeshSyncAfterMove(b, moved)
                                            selectedBeaconId = moved.id
                                            selectedBeaconChannelIndex = moved.channelIndex
                                            MapBeaconActiveChannelStore.setLocalVirtualChannel()
                                            onBeaconsOnlyModeChange(true)
                                        }
                                    },
                                )
                                mapBeaconSelectableChannels(availableChannels, movePresetForUi).forEach { ch ->
                                    val t = meshChannelDisplayTitle(ch, movePresetForUi)
                                    DropdownMenuItem(
                                        text = { Text(t, color = Color(0xFFDAF7FF), fontSize = 14.sp) },
                                        onClick = {
                                            beaconTransferMenuExpanded = false
                                            val b = beacon
                                            scope.launch {
                                                val newChId = mapChannelIdForIndex(ch.index)
                                                val moved = beaconVm.moveBeaconToChannelSuspend(
                                                    b,
                                                    newChId,
                                                    ch.index,
                                                    localMapInstall = false,
                                                )
                                                applyBeaconMeshSyncAfterMove(b, moved)
                                                selectedBeaconId = moved.id
                                                selectedBeaconChannelIndex = moved.channelIndex
                                                MapBeaconActiveChannelStore.setChannel(ch.index, t)
                                                onBeaconsOnlyModeChange(true)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    selectedBeaconTransport?.let {
                        Text("Источник: ${it.asDebugLabel()}", color = Color(0xFFA8ECFF), fontSize = 11.sp)
                    }
                }
            }
        }
        mapBubbleMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 88.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xF0081B2F),
                    border = BorderStroke(1.dp, Color(0x6642E6FF)),
                    shadowElevation = 10.dp,
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        color = Color(0xFFDAF7FF),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (showClearAllBeaconsBubble) {
            val clearMsg = stringResource(R.string.map_clear_all_beacons_message)
            val clearYes = stringResource(R.string.map_clear_all_beacons_yes)
            val clearNo = stringResource(R.string.map_clear_all_beacons_no)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showClearAllBeaconsBubble = false },
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* поглощаем клик, чтобы не закрыть по фону */ },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xE20A2036)),
                    border = BorderStroke(1.dp, Color(0xFF39E7FF).copy(alpha = 0.35f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = clearMsg,
                            color = Color(0xFFE5FCFF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { showClearAllBeaconsBubble = false },
                            ) {
                                Text(clearNo, color = Color(0xFF8FE8FF), fontSize = 14.sp)
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val list = beaconsForChannel.toList()
                                        val myMask = localNodeNumLong
                                        list.forEach { b ->
                                            if (!b.localMapInstall &&
                                                (b.creatorNodeNum and 0xFFFF_FFFFL) == myMask
                                            ) {
                                                MapBeaconSyncRepository.publishRemoveIfMeshVisible(b)
                                            }
                                        }
                                        beaconVm.removeAllBeaconsSuspend(list)
                                        list.forEach { idBeacon ->
                                            beaconTransportHints.remove(idBeacon.id)
                                            newBeaconIds.remove(idBeacon.id)
                                        }
                                        clearBeaconSelection()
                                        highlightedBeaconId = null
                                        highlightedBeaconUntilMs = 0L
                                        showClearAllBeaconsBubble = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                            ) {
                                Text(clearYes, color = Color(0xFF52E0FF), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBeaconLimitDialog) {
        AlertDialog(
            onDismissRequest = { showBeaconLimitDialog = false },
            containerColor = Color(0xFF0E1F33),
            titleContentColor = Color(0xFFDAF7FF),
            textContentColor = Color(0xFF8CB0BF),
            title = {
                Text(
                    stringResource(R.string.map_beacon_limit_dialog_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(stringResource(R.string.map_beacon_max_count_message))
            },
            confirmButton = {
                TextButton(onClick = { showBeaconLimitDialog = false }) {
                    Text("OK", color = Color(0xFF52E0FF))
                }
            },
        )
    }

    if (pendingBeacon != null) {
        AlertDialog(
            onDismissRequest = {
                pendingBeacon = null
            },
            title = { Text("Создать маячок") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = beaconTitleInput,
                        onValueChange = {
                            beaconTitleInput = it
                            beaconTitleInputError = null
                        },
                        singleLine = true,
                        isError = beaconTitleInputError != null,
                        label = { Text("Название объекта") },
                        supportingText = {
                            beaconTitleInputError?.let {
                                Text(it, color = Color(0xFFFF8A80))
                            }
                        },
                    )
                    OutlinedTextField(
                        value = beaconTtlMinutesInput,
                        onValueChange = {
                            if (it.all(Char::isDigit)) {
                                beaconTtlMinutesInput = it
                                beaconTtlMinutesInputError = null
                            }
                        },
                        singleLine = true,
                        isError = beaconTtlMinutesInputError != null,
                        label = { Text("Время жизни (минуты)") },
                        supportingText = {
                            beaconTtlMinutesInputError?.let {
                                Text(it, color = Color(0xFFFF8A80))
                            }
                        },
                    )
                    Text(
                        "Установите время жизни вручную (минимум 1 минута).",
                        color = Color(0xFF8FE8FF),
                        fontSize = 12.sp,
                    )
                    if (isLocalBeaconChannelSelection(activeChannel)) {
                        Text(
                            "Выбран канал карты «${LOCAL_BEACON_CHANNEL_TITLE}»: метка будет только на этом устройстве, без отправки в сеть.",
                            color = Color(0xFF8FE8FF).copy(alpha = 0.72f),
                            fontSize = 11.sp,
                        )
                    } else {
                        Text(
                            "Чтобы метка была только локально: в списке каналов карты выберите «${LOCAL_BEACON_CHANNEL_TITLE}» или после создания — «Перенести метку».",
                            color = Color(0xFF8FE8FF).copy(alpha = 0.72f),
                            fontSize = 11.sp,
                        )
                    }
                    Text("Цвет метки", color = Color(0xFF8FE8FF), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BEACON_COLOR_PRESETS.take(4).forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (beaconColorInput == hex) 2.5.dp else 0.dp,
                                        color = if (beaconColorInput == hex) Color.White else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable { beaconColorInput = hex },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BEACON_COLOR_PRESETS.drop(4).forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (beaconColorInput == hex) 2.5.dp else 0.dp,
                                        color = if (beaconColorInput == hex) Color.White else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable { beaconColorInput = hex },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = pendingBeacon ?: return@TextButton
                        val title = beaconTitleInput.trim()
                        if (title.isEmpty()) {
                            beaconTitleInputError = "Введите название маячка"
                            return@TextButton
                        }
                        val ttlMinutes = beaconTtlMinutesInput.toLongOrNull()
                        if (ttlMinutes == null || ttlMinutes < 1L) {
                            beaconTtlMinutesInputError = "Введите число минут (не меньше 1)"
                            return@TextButton
                        }
                        val createLocalOnly = isLocalBeaconChannelSelection(activeChannel)
                        val meshCh = if (createLocalOnly) {
                            defaultRealChannelForMap(addr)
                        } else {
                            activeChannel
                        }
                        val chForBeacon = meshStoredChannelForMapPlacement(addr, activeChannel, availableChannels)
                        if (chForBeacon?.isDefaultPskBlockedOnMapBeacons() == true) {
                            mapBubbleMessage = mapBeaconBlockedPskMessage
                            return@TextButton
                        }
                        val created = beaconVm.addBeacon(
                            title = title,
                            latitude = p.lat,
                            longitude = p.lon,
                            creatorNodeNum = localNodeNumLong,
                            creatorLongName = localNodeLongName,
                            channelId = meshCh.channelId,
                            channelIndex = meshCh.channelIndex,
                            ttlMs = ttlMinutes * 60_000L,
                            color = beaconColorInput,
                            localMapInstall = createLocalOnly,
                        )
                        if (created == null) {
                            mapBubbleMessage = mapBeaconMaxMessage
                            return@TextButton
                        }
                        if (createLocalOnly) {
                            MapBeaconActiveChannelStore.setLocalVirtualChannel()
                            onBeaconsOnlyModeChange(true)
                        } else {
                            MapBeaconSyncRepository.publishAdd(created)
                        }
                        selectedBeaconId = created.id
                        selectedBeaconChannelIndex = created.channelIndex
                        selectedNode = null
                        pendingBeacon = null
                    },
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingBeacon = null
                }) {
                    Text("Отмена")
                }
            },
        )
    }

    pendingBeaconShareImport?.let { share ->
        fun declineShareImportFromLink(returnToChat: Boolean) {
            importPrimaryMenuExpanded = false
            importShowMoveChannelList = false
            val toRemove = importStagingBeacon
            clearBeaconSelection()
            importShareSig = null
            importStagingBeacon = null
            onPendingBeaconShareConsumed()
            if (toRemove != null) {
                beaconVm.removeBeaconAny(toRemove.id, toRemove.channelIndex)
            }
            if (returnToChat) {
                onBeaconShareImportCancelled()
            }
        }
        AlertDialog(
            onDismissRequest = { declineShareImportFromLink(returnToChat = true) },
            containerColor = Color(0xFF0E1F33),
            title = {
                Text(
                    "Хотите добавить эту метку?",
                    color = Color(0xFFDAF7FF),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize / 2f * 1.5f,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        String.format(Locale.US, "%.6f, %.6f", share.lat, share.lon),
                        color = Color(0xFF8FE8FF),
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Text(
                        "Канал: ${share.channelTitle}",
                        color = Color(0xFF8FE8FF),
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = importShareTitle,
                        onValueChange = {
                            importShareTitle = it
                            importShareTitleError = null
                        },
                        singleLine = true,
                        isError = importShareTitleError != null,
                        label = { Text("Название метки") },
                        supportingText = {
                            importShareTitleError?.let { Text(it, color = Color(0xFFFF8A80)) }
                        },
                    )
                    OutlinedTextField(
                        value = importShareTtlMinutes,
                        onValueChange = {
                            if (it.all(Char::isDigit)) {
                                importShareTtlMinutes = it
                                importShareTtlError = null
                            }
                        },
                        singleLine = true,
                        isError = importShareTtlError != null,
                        label = { Text("Время жизни (минуты)") },
                        supportingText = {
                            importShareTtlError?.let { Text(it, color = Color(0xFFFF8A80)) }
                        },
                    )
                    Text(
                        "Минимум 1 минута. Метка временно на карте до подтверждения. «Нет» — без сохранения и возврат в чат.",
                        color = Color(0xFF8FE8FF).copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                    Text("Цвет метки", color = Color(0xFF8FE8FF), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BEACON_COLOR_PRESETS.take(4).forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (importShareColorHex == hex) 2.5.dp else 0.dp,
                                        color = if (importShareColorHex == hex) Color.White else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        importShareColorHex = hex
                                        importStagingBeacon?.let { ob ->
                                            val n = ob.copy(color = hex)
                                            beaconVm.upsertBeacon(n)
                                            importStagingBeacon = n
                                        }
                                    },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BEACON_COLOR_PRESETS.drop(4).forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (importShareColorHex == hex) 2.5.dp else 0.dp,
                                        color = if (importShareColorHex == hex) Color.White else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        importShareColorHex = hex
                                        importStagingBeacon?.let { ob ->
                                            val n = ob.copy(color = hex)
                                            beaconVm.upsertBeacon(n)
                                            importStagingBeacon = n
                                        }
                                    },
                            )
                        }
                    }
                    if (importShowMoveChannelList) {
                        val movePreset = channelModemPreset
                            ?: addr?.let { MeshNodeSyncMemoryStore.getLora(it)?.modemPreset }
                        val moveChannels = addr?.let { MeshNodeSyncMemoryStore.getChannels(it)?.channels }
                            ?.filter { it.role != MeshStoredChannel.ROLE_DISABLED }.orEmpty()
                        val moveSelectableChannels = mapBeaconSelectableChannels(moveChannels, movePreset)
                        Text(
                            "Выберите канал для переноса:",
                            color = Color(0xFF8FE8FF),
                            fontSize = 12.sp,
                        )
                        if (moveSelectableChannels.isEmpty()) {
                            Text(
                                if (moveChannels.isEmpty()) {
                                    "Нет загруженных каналов — привяжите ноду."
                                } else {
                                    mapBeaconNoPskAllowedChannelsMessage
                                },
                                color = Color(0xFFFF8A80).copy(alpha = 0.9f),
                                fontSize = 12.sp,
                            )
                        } else {
                        Column(
                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            moveSelectableChannels.forEach { ch ->
                                val t = meshChannelDisplayTitle(ch, movePreset)
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val base = importStagingBeacon ?: return@launch
                                            val title = importShareTitle.trim()
                                            if (title.isEmpty()) {
                                                importShareTitleError = "Введите название метки"
                                                return@launch
                                            }
                                            val ttlMinutes = importShareTtlMinutes.toLongOrNull()
                                            if (ttlMinutes == null || ttlMinutes < 1L) {
                                                importShareTtlError = "Введите число минут (не меньше 1)"
                                                return@launch
                                            }
                                            val ttlMs = ttlMinutes * 60_000L
                                            val edited = base.copy(
                                                title = title,
                                                ttlMs = ttlMs.coerceAtLeast(MapBeaconViewModel.MIN_BEACON_TTL_MS),
                                                color = importShareColorHex.ifBlank { "#39E7FF" },
                                            )
                                            val newChId = mapChannelIdForIndex(ch.index)
                                            val moved = beaconVm.moveBeaconToChannelSuspend(
                                                edited,
                                                newChId,
                                                ch.index,
                                                localMapInstall = false,
                                            )
                                            MapBeaconSyncRepository.publishAdd(moved)
                                            MapBeaconActiveChannelStore.setChannel(ch.index, t)
                                            onBeaconsOnlyModeChange(true)
                                            selectedBeaconId = moved.id
                                            selectedBeaconChannelIndex = moved.channelIndex
                                            selectedNode = null
                                            onPendingBeaconShareConsumed()
                                            importStagingBeacon = null
                                            importShareSig = null
                                            importShowMoveChannelList = false
                                            importPrimaryMenuExpanded = false
                                        }
                                    },
                                ) {
                                    Text(t, color = Color(0xFF52E0FF), fontSize = 13.sp)
                                }
                            }
                        }
                        }
                    }
                }
            },
            confirmButton = {
                Box {
                    TextButton(
                        onClick = { importPrimaryMenuExpanded = true },
                    ) {
                        Text("Действие ▼", color = Color(0xFF52E0FF))
                    }
                    DropdownMenu(
                        expanded = importPrimaryMenuExpanded,
                        onDismissRequest = { importPrimaryMenuExpanded = false },
                        containerColor = Color(0xF2081B2F),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Оставить локально", color = Color(0xFFDAF7FF), fontSize = 14.sp) },
                            onClick = {
                                importPrimaryMenuExpanded = false
                                val base = importStagingBeacon
                                if (base == null) {
                                    onPendingBeaconShareConsumed()
                                    importShareSig = null
                                    return@DropdownMenuItem
                                }
                                val title = importShareTitle.trim()
                                if (title.isEmpty()) {
                                    importShareTitleError = "Введите название метки"
                                    return@DropdownMenuItem
                                }
                                val ttlMinutes = importShareTtlMinutes.toLongOrNull()
                                if (ttlMinutes == null || ttlMinutes < 1L) {
                                    importShareTtlError = "Введите число минут (не меньше 1)"
                                    return@DropdownMenuItem
                                }
                                val ttlMs = ttlMinutes * 60_000L
                                val updated = base.copy(
                                    title = title,
                                    ttlMs = ttlMs.coerceAtLeast(MapBeaconViewModel.MIN_BEACON_TTL_MS),
                                    color = importShareColorHex.ifBlank { "#39E7FF" },
                                )
                                scope.launch {
                                    val back = defaultRealChannelForMap(addr)
                                    val moved = beaconVm.moveBeaconToChannelSuspend(
                                        updated,
                                        back.channelId,
                                        back.channelIndex,
                                        localMapInstall = true,
                                    )
                                    applyBeaconMeshSyncAfterMove(updated, moved)
                                    MapBeaconActiveChannelStore.setLocalVirtualChannel()
                                    onBeaconsOnlyModeChange(true)
                                    selectedBeaconId = moved.id
                                    selectedBeaconChannelIndex = moved.channelIndex
                                    selectedNode = null
                                    onPendingBeaconShareConsumed()
                                    importStagingBeacon = null
                                    importShareSig = null
                                    importShowMoveChannelList = false
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Перенести в канал", color = Color(0xFFDAF7FF), fontSize = 14.sp) },
                            onClick = {
                                importPrimaryMenuExpanded = false
                                importShowMoveChannelList = true
                            },
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { declineShareImportFromLink(returnToChat = true) }) {
                    Text("Нет", color = Color(0xFF8FE8FF))
                }
            },
        )
    }

}

@Composable
private fun BottomMapCompass(
    bearingDeg: Float,
    onClick: () -> Unit,
) {
    val dimmed = abs(bearingDeg) < 1f
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (dimmed) 0.5f else 1f)
            .clip(CircleShape)
            .background(Color(0xDD071B2E))
            .border(BorderStroke(1.dp, Color(0x5539E7FF)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = "Моя позиция",
            tint = Color(0xFF39E7FF),
            modifier = Modifier
                .size(22.dp)
                .rotate(-bearingDeg),
        )
    }
}

@Composable
private fun MapModeSwitch(
    modifier: Modifier = Modifier,
    offlineSelected: Boolean,
    offlineAvailable: Boolean,
    onSelectOnline: () -> Unit,
    onSelectOffline: () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD0071B2E)),
        border = BorderStroke(1.dp, Color(0x6642E6FF)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentChip("OSM", active = !offlineSelected, enabled = true, onClick = onSelectOnline)
            SegmentChip("Offline", active = offlineSelected && offlineAvailable, enabled = offlineAvailable, onClick = onSelectOffline)
        }
    }
}

@Composable
private fun BeaconVisibilitySwitch(
    modifier: Modifier = Modifier,
    beaconsOnly: Boolean,
    onSetNodes: () -> Unit,
    onSetBeacons: () -> Unit,
    onHoldBeaconsSegment: () -> Unit = {},
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD0071B2E)),
        border = BorderStroke(1.dp, Color(0x6642E6FF)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentChip("Ноды", active = !beaconsOnly, enabled = true, onClick = onSetNodes)
            MaikiLongHoldSegmentChip(
                active = beaconsOnly,
                enabled = true,
                onClick = onSetBeacons,
                onHoldOneSecond = onHoldBeaconsSegment,
            )
        }
    }
}

@Composable
private fun MaikiLongHoldSegmentChip(
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onHoldOneSecond: () -> Unit,
) {
    val latestClick by rememberUpdatedState(onClick)
    val latestHold by rememberUpdatedState(onHoldOneSecond)
    val gestureScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .background(
                color = when {
                    !enabled -> Color(0x222A3A46)
                    active -> Color(0xFF39E7FF).copy(alpha = 0.22f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(16.dp),
            )
            .pointerInput(enabled, gestureScope) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val longFired = AtomicBoolean(false)
                    val holdJob = gestureScope.launch {
                        delay(MAP_BEACONS_SEGMENT_HOLD_MS)
                        longFired.set(true)
                        latestHold()
                    }
                    waitForUpOrCancellation()
                    holdJob.cancel()
                    if (!longFired.get()) latestClick()
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Маяки",
            color = when {
                !enabled -> Color(0xFF607D8B)
                active -> Color(0xFFDAF7FF)
                else -> Color(0xFF9ED6E8)
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BeaconListToggleButton(
    modifier: Modifier = Modifier,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xD0071B2E)),
            border = BorderStroke(1.dp, Color(0x6642E6FF)),
            modifier = Modifier.clickable(onClick = onToggle),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Список маячков",
                    tint = Color(0xFFDAF7FF),
                )
                Text(if (expanded) "Скрыть" else "Метки", color = Color(0xFFDAF7FF), fontSize = 12.sp)
            }
        }
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .background(Color(0xFF39E7FF), RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, Color(0xFFB5F7FF)), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), color = Color(0xFF042133), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BeaconBubbleCard(
    beacon: MapBeacon,
    remainMs: Long,
    distanceMeters: Double?,
    transport: BeaconTransportLabel?,
    isNew: Boolean = false,
    canDelete: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val beaconColor = remember(beacon.color) {
        runCatching { Color(AndroidColor.parseColor(beacon.color)) }.getOrElse { Color(0xFF39E7FF) }
    }
    val borderColor = if (isNew) beaconColor.copy(alpha = 0.9f) else beaconColor.copy(alpha = 0.35f)
    val borderWidth = if (isNew) 1.5.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE20A2036)),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Color dot indicator.
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(beaconColor),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        beacon.title,
                        color = Color(0xFFE5FCFF),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isNew) {
                        Text(
                            "НОВОЕ",
                            color = beaconColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(beaconColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    if (canDelete) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FF5252))
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onDelete()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✕", color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Автор: ${beaconCreatorLabel(beacon)}", color = Color(0xFF8FE8FF), fontSize = 11.sp)
                val createdText = remember(beacon.timestampCreated) {
                    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(beacon.timestampCreated))
                }
                Text("Установлено: $createdText", color = Color(0xFF8FE8FF), fontSize = 11.sp)
                Text(
                    "Удаление через: ${formatRemaining(remainMs)}",
                    color = Color(0xFF8FE8FF),
                    fontSize = 11.sp,
                )
                val dist = distanceMeters?.let { formatDistance(it) } ?: "—"
                Text("Расстояние: $dist", color = Color(0xFFB8F4FF), fontSize = 11.sp)
                transport?.let {
                    Text("Источник: ${it.asDebugLabel()}", color = Color(0xFFA8ECFF), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SegmentChip(
    text: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = when {
                    !enabled -> Color(0x222A3A46)
                    active -> Color(0xFF39E7FF).copy(alpha = 0.22f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> Color(0xFF607D8B)
                active -> Color(0xFFDAF7FF)
                else -> Color(0xFF9ED6E8)
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun ensureNodeLayers(style: Style) {
    if (style.getSource(NODE_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(NODE_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer(NODE_GLOW_LAYER_ID) == null) {
        style.addLayer(
            CircleLayer(NODE_GLOW_LAYER_ID, NODE_SOURCE_ID).withProperties(
                circleRadius(10f),
                circleColor(NODE_COLOR_GLOW),
                circleOpacity(0.28f),
            ),
        )
    }
    if (style.getLayer(NODE_LAYER_ID) == null) {
        style.addLayer(
            CircleLayer(NODE_LAYER_ID, NODE_SOURCE_ID).withProperties(
                circleRadius(5.4f),
                circleColor(NODE_COLOR_CORE),
                circleStrokeColor(NODE_COLOR_STROKE),
                circleStrokeWidth(1.2f),
                circleOpacity(0.95f),
            ),
        )
    }
}

/**
 * Removes any existing beacon source/layers and recreates them from scratch with [beacons] as
 * initial data. Initialising GeoJsonSource with the FeatureCollection directly (rather than
 * creating an empty source and calling setGeoJson later) guarantees MapLibre registers the
 * features at the same moment the source is added — eliminating the async-update race condition.
 */

private fun ensureMyLocationLayers(style: Style) {
    if (style.getSource(MY_LOCATION_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(MY_LOCATION_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer(MY_LOCATION_GLOW_LAYER_ID) == null) {
        style.addLayer(
            CircleLayer(MY_LOCATION_GLOW_LAYER_ID, MY_LOCATION_SOURCE_ID).withProperties(
                circleRadius(13f),
                circleColor("#4FA3FF"),
                circleOpacity(0.30f),
            ),
        )
    }
    if (style.getLayer(MY_LOCATION_LAYER_ID) == null) {
        style.addLayer(
            CircleLayer(MY_LOCATION_LAYER_ID, MY_LOCATION_SOURCE_ID).withProperties(
                circleRadius(6f),
                circleColor("#5EB1FF"),
                circleStrokeColor("#E6F3FF"),
                circleStrokeWidth(1.4f),
                circleOpacity(1f),
            ),
        )
    }
}

private fun updateNodeSource(style: Style, nodes: List<Node>) {
    val source = style.getSourceAs<GeoJsonSource>(NODE_SOURCE_ID) ?: return
    val features = nodes.map { node ->
        Feature.fromGeometry(Point.fromLngLat(node.lon, node.lat)).apply {
            addStringProperty(NODE_FEATURE_ID, node.summary.nodeNum.toString())
            addStringProperty("name", node.summary.displayLongName())
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}


private fun updateMyLocationSource(style: Style, myLocation: LatLng?, visible: Boolean) {
    val source = style.getSourceAs<GeoJsonSource>(MY_LOCATION_SOURCE_ID) ?: return
    if (!visible || myLocation == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        return
    }
    val me = Feature.fromGeometry(Point.fromLngLat(myLocation.longitude, myLocation.latitude))
    source.setGeoJson(FeatureCollection.fromFeature(me))
}

/** Проверка по имени файла (как в проводнике); без суффикса `.mbtiles` не принимаем. */
private fun uriLooksLikeMbtilesFile(context: Context, uri: Uri): Boolean {
    val name = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
    } ?: uri.lastPathSegment
    return name?.endsWith(".mbtiles", ignoreCase = true) == true
}

/**
 * Creates a round beacon icon bitmap in the given CSS hex color.
 * Programmatic creation avoids XML-drawable inflation issues when producing MapLibre image assets.
 */
private fun createBeaconBitmap(colorHex: String): Bitmap {
    val color = AndroidColor.parseColor(colorHex)
    val size = 56
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 4f
    // Outer glow
    paint.color = (color and 0x00FFFFFF) or 0x55000000
    canvas.drawCircle(cx, cy, r + 5f, paint)
    // Main fill
    paint.color = color
    canvas.drawCircle(cx, cy, r, paint)
    // White border
    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawCircle(cx, cy, r - 1.5f, paint)
    // Center dot
    paint.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy, r * 0.28f, paint)
    return bmp
}

private fun loadOfflineStyleJson(context: Context, tilesUrl: String): String {
    val template = context.assets.open(STYLE_ASSET_PATH).bufferedReader().use { it.readText() }
    return template.replace(STYLE_TILES_PLACEHOLDER, tilesUrl)
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSeconds / 3600L
    val m = (totalSeconds % 3600L) / 60L
    val s = totalSeconds % 60L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
}

private fun formatDistance(meters: Double): String {
    val m = meters.coerceAtLeast(0.0)
    return if (m < 1000.0) {
        "${m.toInt()} м"
    } else {
        String.format(Locale.ROOT, "%.2f км", m / 1000.0)
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun BeaconTransportLabel.asDebugLabel(): String = when (this) {
    BeaconTransportLabel.BLE -> "Bluetooth"
    BeaconTransportLabel.WIFI -> "Wi-Fi/TCP"
    BeaconTransportLabel.USB -> "USB"
    BeaconTransportLabel.UNKNOWN -> "Неизвестно"
}

private fun beaconCreatorLabel(beacon: MapBeacon): String {
    val fromName = beacon.creatorLongName.trim()
    if (fromName.isNotEmpty()) return fromName
    val fallbackHex = beacon.creatorNodeNum.toULong().toString(16).uppercase(Locale.ROOT).padStart(8, '0')
    return "Node $fallbackHex"
}

private class MbtilesHttpServer(
    private val mbtilesFile: File,
) : NanoHTTPD("127.0.0.1", 0) {
    private val db: SQLiteDatabase by lazy {
        SQLiteDatabase.openDatabase(mbtilesFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    override fun start() {
        super.start(SOCKET_READ_TIMEOUT, true)
    }

    override fun stop() {
        runCatching { db.close() }
        super.stop()
    }

    fun tileTemplateUrl(): String = "http://127.0.0.1:${listeningPort}/tiles/{z}/{x}/{y}.pbf"

    override fun serve(session: IHTTPSession): Response {
        val parts = session.uri.trim('/').split('/')
        if (parts.size != 4 || parts[0] != "tiles") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        val z = parts[1].toIntOrNull() ?: return badRequest()
        val x = parts[2].toIntOrNull() ?: return badRequest()
        val yRaw = parts[3].removeSuffix(".pbf").toIntOrNull() ?: return badRequest()
        val y = ((1 shl z) - 1 - yRaw).coerceAtLeast(0)

        val cursor = db.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",
            arrayOf(z.toString(), x.toString(), y.toString()),
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Tile missing")
            }
            val blob = it.getBlob(0)
            val res = newFixedLengthResponse(
                Response.Status.OK,
                "application/x-protobuf",
                ByteArrayInputStream(blob),
                blob.size.toLong(),
            )
            res.addHeader("Cache-Control", "public, max-age=31536000")
            return res
        }
    }

    private fun badRequest(): Response =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad tile request")
}

private fun String.trim(char: Char): String = trim { it == char }
