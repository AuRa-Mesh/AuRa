package com.example.aura.ui.components

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import android.view.WindowManager
import com.example.aura.bluetooth.MeshBleScanner
import com.example.aura.bluetooth.MeshDevice
import com.example.aura.bluetooth.MeshDeviceTransport
import com.example.aura.bluetooth.lastSavedBleMacForAutoConnect
import com.example.aura.bluetooth.meshDeviceFromBleMac
import com.example.aura.bluetooth.tryAutoConnectSavedBleNode
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.MeshStreamToRadio
import com.example.aura.bluetooth.NodeConnectionState
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.mesh.MeshService
import com.example.aura.security.NodeAuthStore
import com.example.aura.bluetooth.NodeSyncStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val USB_PERMISSION_ACTION = "com.example.aura.USB_PERMISSION"

/** Повторное BLE-подключение к ноде после «Отключиться» (MAC в [NodeAuthStore.peekBleMacAfterUserDisconnect]). */
private fun performBleQuickReconnectFromDisconnect(
    context: Context,
    onSessionMeshAddressChange: (String?) -> Unit,
    onDeviceLinked: ((String, String) -> Unit)?,
) {
    val app = context.applicationContext
    val mac = NodeAuthStore.peekBleMacAfterUserDisconnect(app) ?: return
    val auth = NodeAuthStore.load(app) ?: return
    NodeAuthStore.save(app, auth.nodeId, auth.password, mac)
    NodeAuthStore.clearBleMacAfterUserDisconnect(app)
    onSessionMeshAddressChange(mac)
    onDeviceLinked?.invoke(auth.nodeId, mac)
    MeshService.resumeAfterUserBleConnect(app)
    tryAutoConnectSavedBleNode(app)
}

// ── Palette (текущая цветовая схема приложения) ────────────────────────────────
private val BG = Color(0xFF0D1A2E)
private val CARD = Color(0xFF0D2535)
private val CARD2 = Color(0xFF071320)
private val ACCENT = Color(0xFF00D4FF)
private val TEXT = Color(0xFFDDE8F0)
private val TEXT_SEC = Color(0xFF8A9AAA)
private val TEXT_DIM = Color(0xFF3A4A5A)
private val DIVIDER = Color(0xFF1A2A3A)
private val GREEN = Color(0xFF4CAF50)
private val GREEN_BG = Color(0xFF0D2A1A)
private val RED = Color(0xFFFF5252)
private val RED_BG = Color(0xFF2A0A0A)
private val YELLOW = Color(0xFFFFAA00)

private enum class ScanState { Idle, Scanning, Done }
private enum class ConnTab { Bluetooth, Network, Usb }

/** Вкладка при открытии диалога «Соединения» (аналог перехода Serial в типичном mesh-клиенте). */
enum class MeshConnectionInitialTab {
    Bluetooth,
    Network,
    Usb,
}

/** Один раз за процесс: при первом открытии «Соединения» на вкладке BLE — автоскан и попытка GATT к последней ноде. */
private object MeshConnectionsDialogColdStartAuto {
    var consumed: Boolean = false
}

/**
 * Диалог «Соединения» — объединяет Bluetooth, Wi-Fi (TCP) и USB в одном окне.
 * Стиль и структура как в приложении mesh, текущая цветовая схема приложения.
 */
@Composable
fun MeshBluetoothScanDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    sessionNodeId: String,
    sessionMeshAddress: String? = null,
    onSessionNodeIdChange: (String) -> Unit,
    onSessionMeshAddressChange: (String?) -> Unit,
    onDeviceLinked: ((nodeId: String, meshAddress: String) -> Unit)? = null,
    onDeviceUnbound: (() -> Unit)? = null,
    /**
     * Уже есть GATT к одной ноде, а пользователь выбирает другую (BLE / TCP / USB) —
     * переход на экран авторизации для новой сессии.
     */
    onNavigateToPassword: (() -> Unit)? = null,
    /** При каждом открытии (например с экрана пароля по NODE ID) — сразу запускать BLE-сканирование. */
    autoBleScanOnOpen: Boolean = false,
    /** Главный экран чата: тап по списку сопряжённых — вопрос о новой ноде. */
    bondedUnbindHeaderEnabled: Boolean = false,
    initialConnectionTab: MeshConnectionInitialTab = MeshConnectionInitialTab.Bluetooth,
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val connState by NodeGattConnection.connectionState.collectAsState()
    val myNodeNum by NodeGattConnection.myNodeNum.collectAsState()
    val syncStep by NodeGattConnection.syncStep.collectAsState()

    var selectedTab by remember { mutableStateOf(ConnTab.Bluetooth) }

    LaunchedEffect(visible, initialConnectionTab) {
        if (visible) {
            selectedTab = when (initialConnectionTab) {
                MeshConnectionInitialTab.Bluetooth -> ConnTab.Bluetooth
                MeshConnectionInitialTab.Network -> ConnTab.Network
                MeshConnectionInitialTab.Usb -> ConnTab.Usb
            }
        }
    }

    // ── Bluetooth ──────────────────────────────────────────────────────────────
    var scanState by remember { mutableStateOf(ScanState.Idle) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var connectingAddress by remember { mutableStateOf<String?>(null) }
    /** После запроса BLE-разрешений из авто-запуска — дополнительно подключить к сохранённой ноде. */
    var pendingAutoConnectAfterBlePerm by remember { mutableStateOf(false) }
    /** Главный чат: тап по списку найденных/сопряжённых устройств — вопрос о новой ноде. */
    var showNewNodeConfirmDialog by remember { mutableStateOf(false) }
    val scanner = remember { MeshBleScanner(context) }
    val bondedDevices = remember { mutableStateListOf<MeshDevice>() }
    val scannedDevices = remember { mutableStateListOf<MeshDevice>() }

    // ── Wi-Fi / TCP ────────────────────────────────────────────────────────────
    var wifiHost by remember { mutableStateOf(MeshDeviceTransport.DEFAULT_TCP_HOST) }
    var wifiPort by remember { mutableStateOf(MeshDeviceTransport.DEFAULT_TCP_PORT.toString()) }
    var wifiBusy by remember { mutableStateOf(false) }
    var wifiError by remember { mutableStateOf<String?>(null) }

    // ── USB ────────────────────────────────────────────────────────────────────
    var usbAutoBaud by remember { mutableStateOf(true) }
    var usbBaud by remember { mutableStateOf(MeshDeviceTransport.DEFAULT_USB_BAUD.toString()) }
    var usbBusy by remember { mutableStateOf(false) }
    var usbError by remember { mutableStateOf<String?>(null) }
    var pendingUsbDevice by remember { mutableStateOf<UsbDevice?>(null) }
    val usbAutoBaudRef = rememberUpdatedState(usbAutoBaud)
    val usbBaudRef = rememberUpdatedState(usbBaud)

    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    val usbDevices = remember(visible, selectedTab) {
        if (selectedTab == ConnTab.Usb) usbManager.deviceList.values.toList() else emptyList()
    }

    // ── USB BroadcastReceiver для разрешений ───────────────────────────────────
    DisposableEffect(visible) {
        if (!visible) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != USB_PERMISSION_ACTION) return
                val dev: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    usbError = "Разрешение USB не выдано"
                    return
                }
                dev ?: return
                val baudArg = if (usbAutoBaudRef.value) null
                else usbBaudRef.value.toIntOrNull().also { if (it == null) { usbError = "Неверный baud"; return } }
                usbBusy = true
                usbError = null
                MeshStreamToRadio.fetchNodeIdUsb(context.applicationContext, dev, baudArg) { hex, err, baudUsed ->
                    usbBusy = false
                    if (hex != null && baudUsed != null) {
                        val addr = MeshDeviceTransport.formatUsb(dev.deviceName, baudUsed)
                        onSessionNodeIdChange(hex)
                        onSessionMeshAddressChange(addr)
                        onDeviceLinked?.invoke(hex, addr)
                    } else {
                        usbError = err ?: "Ошибка USB"
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(USB_PERMISSION_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun checkPermission(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val bleMacForAutoConnect = remember(sessionMeshAddress) {
        lastSavedBleMacForAutoConnect(context.applicationContext, sessionMeshAddress)
    }

    fun doConnectBle(device: MeshDevice) {
        if (NodeGattConnection.isAlive) {
            val tgt = NodeGattConnection.targetDevice
            val same = tgt != null &&
                    MeshNodeSyncMemoryStore.normalizeKey(tgt.address) ==
                    MeshNodeSyncMemoryStore.normalizeKey(device.address)
            if (!same) {
                onNavigateToPassword?.invoke()
                return
            }
        }
        onSessionMeshAddressChange(device.address)
        connectingAddress = device.address
        scope.launch {
            MeshService.resumeAfterUserBleConnect(context.applicationContext)
            NodeGattConnection.connect(device, context)
            // Ждём полной синхронизации (READY), только потом передаём node id на экран авторизации
            val ready = withTimeoutOrNull(20_000L) {
                NodeGattConnection.connectionState
                    .filter { it == NodeConnectionState.READY }
                    .first()
            }
            connectingAddress = null
            if (ready == null) {
                scanError = "Не удалось завершить синхронизацию"
                return@launch
            }
            val num = NodeGattConnection.myNodeNum.value
            if (num == null || num == 0u) {
                scanError = "Не удалось получить Node ID"
                return@launch
            }
            val nid = num.toString(16).padStart(8, '0').uppercase()
            // Заполняем поле node id и сохраняем адрес — диалог не закрываем,
            // пользователь сам нажмёт крестик когда захочет.
            onSessionNodeIdChange(nid)
            onDeviceLinked?.invoke(nid, device.address)
        }
    }

    fun attemptAutoConnectLastSavedBle(mac: String) {
        val meshDev = meshDeviceFromBleMac(context.applicationContext, mac) ?: return
        val normSaved = MeshNodeSyncMemoryStore.normalizeKey(mac)
        if (connState == NodeConnectionState.READY) {
            val tgt = NodeGattConnection.targetDevice ?: return
            if (MeshNodeSyncMemoryStore.normalizeKey(tgt.address) == normSaved) return
        }
        if (NodeGattConnection.isAlive) {
            val tgt = NodeGattConnection.targetDevice ?: return
            if (MeshNodeSyncMemoryStore.normalizeKey(tgt.address) != normSaved) return
        }
        doConnectBle(meshDev)
    }

    fun runBleDiscoveryScan() {
        scannedDevices.clear()
        scanError = null
        bondedDevices.clear()
        bondedDevices.addAll(scanner.getBondedMeshDevices())
        doScan(
            scanner,
            scope,
            scannedDevices,
            onStateChange = { scanState = it },
            onError = { scanError = it; scanState = ScanState.Done },
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) {
            runBleDiscoveryScan()
            if (pendingAutoConnectAfterBlePerm) {
                pendingAutoConnectAfterBlePerm = false
                val mac = lastSavedBleMacForAutoConnect(context.applicationContext, sessionMeshAddress)
                if (mac != null && !bondedUnbindHeaderEnabled) {
                    scope.launch {
                        delay(500)
                        attemptAutoConnectLastSavedBle(mac)
                    }
                }
                MeshConnectionsDialogColdStartAuto.consumed = true
            }
        } else {
            pendingAutoConnectAfterBlePerm = false
            scanError = "Разрешено частично — перейдите в Настройки → Приложения → Aura"
            scanState = ScanState.Done
        }
    }

    fun scheduleBleScanAfterDisconnect() {
        scope.launch {
            delay(150)
            if (!permissions.all { checkPermission(it) }) {
                permLauncher.launch(permissions)
                return@launch
            }
            runBleDiscoveryScan()
        }
    }

    DisposableEffect(Unit) { onDispose { scanner.stopScan() } }

    LaunchedEffect(visible) {
        if (!visible) {
            scanner.stopScan()
            scanState = ScanState.Idle
            connectingAddress = null
            return@LaunchedEffect
        }
        bondedDevices.clear()
        bondedDevices.addAll(scanner.getBondedMeshDevices())
    }

    LaunchedEffect(visible, selectedTab, autoBleScanOnOpen, bondedUnbindHeaderEnabled) {
        if (!visible) return@LaunchedEffect
        if (selectedTab != ConnTab.Bluetooth) return@LaunchedEffect
        if (autoBleScanOnOpen) return@LaunchedEffect
        /** Главный чат: синхронизацию при открытии делает отдельный [LaunchedEffect] ниже. */
        if (bondedUnbindHeaderEnabled) return@LaunchedEffect
        if (MeshConnectionsDialogColdStartAuto.consumed) return@LaunchedEffect
        delay(300)
        if (!permissions.all { checkPermission(it) }) {
            pendingAutoConnectAfterBlePerm = true
            permLauncher.launch(permissions)
            return@LaunchedEffect
        }
        runBleDiscoveryScan()
        val mac = bleMacForAutoConnect
        if (mac != null) {
            scope.launch {
                delay(500)
                attemptAutoConnectLastSavedBle(mac)
            }
        }
        MeshConnectionsDialogColdStartAuto.consumed = true
    }

    /**
     * Экран чата (иконка ноды): при открытии «Соединения» на вкладке Bluetooth — только BLE-скан,
     * без автоматического GATT-подключения.
     */
    LaunchedEffect(visible, selectedTab, bondedUnbindHeaderEnabled, sessionMeshAddress) {
        if (!visible || !bondedUnbindHeaderEnabled) return@LaunchedEffect
        if (selectedTab != ConnTab.Bluetooth) return@LaunchedEffect
        delay(300)
        if (!permissions.all { checkPermission(it) }) {
            pendingAutoConnectAfterBlePerm = true
            permLauncher.launch(permissions)
            return@LaunchedEffect
        }
        runBleDiscoveryScan()
    }

    LaunchedEffect(visible, selectedTab, autoBleScanOnOpen) {
        if (!visible || !autoBleScanOnOpen) return@LaunchedEffect
        if (selectedTab != ConnTab.Bluetooth) return@LaunchedEffect
        delay(300)
        if (!permissions.all { checkPermission(it) }) {
            permLauncher.launch(permissions)
            return@LaunchedEffect
        }
        runBleDiscoveryScan()
    }

    fun doConnectWifi() {
        if (NodeGattConnection.isAlive) {
            onNavigateToPassword?.invoke()
            return
        }
        val port = wifiPort.trim().toIntOrNull() ?: run { wifiError = "Укажите корректный порт"; return }
        val host = wifiHost.trim().takeIf { it.isNotEmpty() } ?: run { wifiError = "Укажите хост или IP"; return }
        wifiBusy = true; wifiError = null
        MeshStreamToRadio.fetchNodeIdTcp(host, port) { hex, err ->
            wifiBusy = false
            if (hex != null) {
                val addr = MeshDeviceTransport.formatTcp(host, port)
                onSessionNodeIdChange(hex)
                onSessionMeshAddressChange(addr)
                onDeviceLinked?.invoke(hex, addr)
            } else {
                wifiError = err ?: "Ошибка подключения"
            }
        }
    }

    fun doConnectUsb(dev: UsbDevice) {
        if (NodeGattConnection.isAlive) {
            onNavigateToPassword?.invoke()
            return
        }
        val baudArg = if (usbAutoBaud) null
        else usbBaud.toIntOrNull() ?: run { usbError = "Неверный baud"; return }
        usbError = null
        if (usbManager.hasPermission(dev)) {
            usbBusy = true
            MeshStreamToRadio.fetchNodeIdUsb(context.applicationContext, dev, baudArg) { hex, err, baudUsed ->
                usbBusy = false
                if (hex != null && baudUsed != null) {
                    val addr = MeshDeviceTransport.formatUsb(dev.deviceName, baudUsed)
                    onSessionNodeIdChange(hex)
                    onSessionMeshAddressChange(addr)
                    onDeviceLinked?.invoke(hex, addr)
                } else {
                    usbError = err ?: "Ошибка USB"
                }
            }
        } else {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(USB_PERMISSION_ACTION).setPackage(context.packageName), flags,
            )
            usbManager.requestPermission(dev, pi)
        }
    }

    // Всегда включаем активно подключённое устройство первым — даже если оно
    // не попало в bonded-список (например, не спарено системным BT).
    val allBleDevices = remember(connState, bondedDevices.toList(), scannedDevices.toList()) {
        val active = NodeGattConnection.targetDevice?.let { listOf(it) } ?: emptyList()
        (active + bondedDevices + scannedDevices)
            .distinctBy { it.address }
            .filter { it.isMeshWire }
    }
    val connectedDevice = if (connState == NodeConnectionState.READY) NodeGattConnection.targetDevice else null
    val quickReconnectBleMac =
        if (connectedDevice == null) {
            NodeAuthStore.peekBleMacAfterUserDisconnect(context.applicationContext)
        } else {
            null
        }
    val statusLabel = syncStep.label

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Блюр заднего фона — нативный API Android 12+, fallback — усиленный dim
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            shape = RoundedCornerShape(20.dp),
            color = BG,
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                // ── Заголовок ────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val busy = connState == NodeConnectionState.CONNECTING ||
                                connState == NodeConnectionState.HANDSHAKING ||
                                scanState == ScanState.Scanning || wifiBusy || usbBusy
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ACCENT, strokeWidth = 2.5.dp)
                        }
                        Text("Соединения", color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val badge = connectedDevice?.name?.take(12)
                            ?: if (sessionNodeId.isNotBlank()) sessionNodeId.take(8) else null
                        if (badge != null) {
                            val canTapReconnect = quickReconnectBleMac != null
                            Text(
                                text = badge,
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E4D2A))
                                    .border(
                                        1.dp,
                                        if (canTapReconnect) ACCENT.copy(alpha = 0.75f) else GREEN.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .then(
                                        if (canTapReconnect) {
                                            Modifier.clickable(
                                                remember { MutableInteractionSource() },
                                                null,
                                            ) {
                                                performBleQuickReconnectFromDisconnect(
                                                    context,
                                                    onSessionMeshAddressChange,
                                                    onDeviceLinked,
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(CARD)
                                .clickable(remember { MutableInteractionSource() }, null) { onDismissRequest() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, null, tint = TEXT_SEC, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (quickReconnectBleMac != null && connectedDevice == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D2535))
                            .border(1.dp, ACCENT.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .clickable(remember { MutableInteractionSource() }, null) {
                                performBleQuickReconnectFromDisconnect(
                                    context,
                                    onSessionMeshAddressChange,
                                    onDeviceLinked,
                                )
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Подключение",
                            color = ACCENT,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // ── Карточка подключённого устройства ─────────────────────────
                if (connectedDevice != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(14.dp)).background(CARD).padding(14.dp),
                    ) {
                        Text("Подключённые устройства", color = TEXT_SEC, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(GREEN, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(connectedDevice.name, color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (myNodeNum != null && myNodeNum != 0u) {
                                    Text("!${myNodeNum!!.toString(16).padStart(8, '0').uppercase()}", color = ACCENT, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(RED_BG).border(1.dp, RED.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable(remember { MutableInteractionSource() }, null) {
                                    MeshService.stopForUserBleDisconnect(context.applicationContext)
                                    onDeviceUnbound?.invoke()
                                    scheduleBleScanAfterDisconnect()
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Отключиться", color = RED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Вкладки ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp)).background(CARD2),
                ) {
                    ConnTabItem("Bluetooth", Icons.Default.Bluetooth, selectedTab == ConnTab.Bluetooth, true, Modifier.weight(1f).clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))) { selectedTab = ConnTab.Bluetooth }
                    ConnTabItem("Сеть", Icons.Default.NetworkCheck, selectedTab == ConnTab.Network, true, Modifier.weight(1f)) { selectedTab = ConnTab.Network; wifiError = null }
                    ConnTabItem("COM-порт", Icons.Default.Usb, selectedTab == ConnTab.Usb, true, Modifier.weight(1f).clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))) { selectedTab = ConnTab.Usb; usbError = null }
                }

                Spacer(Modifier.height(14.dp))

                // ── Контент вкладки ───────────────────────────────────────────
                // AnimatedContent помещает содержимое в Box — нужен ровно один корневой элемент.
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "tab",
                    modifier = Modifier.fillMaxWidth(),
                ) { tab ->
                    // Каждая функция-вкладка эмитирует ровно один Column.
                    when (tab) {
                        ConnTab.Bluetooth -> BleTabContent(
                            allDevices = allBleDevices,
                            connState = connState,
                            connectingAddress = connectingAddress,
                            sessionMeshAddress = sessionMeshAddress,
                            scanner = scanner,
                            scanState = scanState,
                            scanError = scanError,
                            bondedUnbindHeaderEnabled = bondedUnbindHeaderEnabled,
                            onPairedDevicesListClick = if (bondedUnbindHeaderEnabled) {
                                { showNewNodeConfirmDialog = true }
                            } else {
                                null
                            },
                            onConnect = ::doConnectBle,
                            onUserBleScanClick = {
                                scanError = null
                                if (permissions.all { checkPermission(it) }) {
                                    runBleDiscoveryScan()
                                } else {
                                    permLauncher.launch(permissions)
                                }
                            },
                        )
                        ConnTab.Network -> NetworkTabContent(
                            host = wifiHost,
                            port = wifiPort,
                            busy = wifiBusy,
                            error = wifiError,
                            onHostChange = { wifiHost = it; wifiError = null },
                            onPortChange = { wifiPort = it; wifiError = null },
                            onConnect = ::doConnectWifi,
                        )
                        ConnTab.Usb -> UsbTabContent(
                            usbDevices = usbDevices,
                            autoBaud = usbAutoBaud,
                            baudInput = usbBaud,
                            busy = usbBusy,
                            error = usbError,
                            onAutoBaudChange = { usbAutoBaud = it; usbError = null },
                            onBaudChange = { usbBaud = it; usbError = null },
                            onConnect = ::doConnectUsb,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Статусная плашка с этапами синхронизации ─────────────────
                if (statusLabel.isNotBlank()) {
                    GattConnectionSyncProgressSection(
                        syncStep = syncStep,
                        connState = connState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CARD2)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (bondedUnbindHeaderEnabled && showNewNodeConfirmDialog) {
        Dialog(
            onDismissRequest = { showNewNodeConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = BG,
                border = BorderStroke(1.dp, DIVIDER),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Подключить новую ноду ?",
                        color = TEXT,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    showNewNodeConfirmDialog = false
                                    onDismissRequest()
                                    onNavigateToPassword?.invoke()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = GREEN_BG,
                            border = BorderStroke(1.dp, GREEN.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                "ДА",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                color = GREEN,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    showNewNodeConfirmDialog = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = CARD2,
                            border = BorderStroke(1.dp, DIVIDER),
                        ) {
                            Text(
                                "НЕТ",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                color = TEXT,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

}

// ── Bluetooth-вкладка ──────────────────────────────────────────────────────────

@Composable
private fun BleTabContent(
    allDevices: List<MeshDevice>,
    connState: NodeConnectionState,
    connectingAddress: String?,
    sessionMeshAddress: String?,
    scanner: MeshBleScanner,
    scanState: ScanState,
    scanError: String?,
    bondedUnbindHeaderEnabled: Boolean,
    /** Главный чат: тап по строке ноды в списке — диалог «новая нода». */
    onPairedDevicesListClick: (() -> Unit)? = null,
    onConnect: (MeshDevice) -> Unit,
    onUserBleScanClick: () -> Unit,
) {
    // Единственный корневой Column — AnimatedContent внутри Box, поэтому нельзя
    // эмитировать несколько элементов верхнего уровня (иначе все накладываются).
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Список устройств
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Сопряжённые устройства",
                    color = TEXT_SEC,
                    fontSize = 12.sp,
                )
            }
            if (allDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            !scanner.isBluetoothEnabled -> "Включите Bluetooth"
                            scanState == ScanState.Scanning -> "Поиск устройств..."
                            scanError != null -> scanError
                            else -> "Устройств не найдено"
                        },
                        color = if (scanError != null) RED else TEXT_SEC, fontSize = 13.sp,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CARD),
                ) {
                    allDevices.forEachIndexed { idx, device ->
                        val normDev = MeshNodeSyncMemoryStore.normalizeKey(device.address)
                        val normSess = sessionMeshAddress?.let { MeshNodeSyncMemoryStore.normalizeKey(it) }
                        val idDev = MeshNodeSyncMemoryStore.bleHardwareIdentityKey(device.address)
                        val idSess = sessionMeshAddress?.let { MeshNodeSyncMemoryStore.bleHardwareIdentityKey(it) }
                        val idTgt = NodeGattConnection.targetDevice?.address?.let {
                            MeshNodeSyncMemoryStore.bleHardwareIdentityKey(it)
                        }
                        val isConn = connState == NodeConnectionState.READY && idTgt != null && idTgt == idDev
                        // Крутим кружок только у той ноды, на которую явно нажали в текущей
                        // сессии диалога. Убираем ложное вращение у ранее подключавшейся ноды.
                        val isConnecting = connectingAddress != null &&
                            MeshNodeSyncMemoryStore.bleHardwareIdentityKey(connectingAddress) == idDev
                        PairedDeviceRow(
                            device = device,
                            isConnected = isConn,
                            isConnecting = isConnecting,
                            isSelected = idSess != null && idSess == idDev,
                            onNewNodePromptClick = onPairedDevicesListClick,
                            onClick = {
                                if (!isConn && connectingAddress == null) onConnect(device)
                            },
                        )
                        if (idx < allDevices.size - 1)
                            HorizontalDivider(
                                color = DIVIDER, thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Кнопка «Сканирования»
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CARD)
                .border(1.dp, DIVIDER, RoundedCornerShape(24.dp))
                .clickable(
                    remember { MutableInteractionSource() }, null,
                    enabled = scanState != ScanState.Scanning,
                ) {
                    onUserBleScanClick()
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (scanState == ScanState.Scanning)
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), color = ACCENT, strokeWidth = 2.dp)
                else
                    Icon(Icons.Default.Search, null, tint = ACCENT, modifier = Modifier.size(16.dp))
                Text(
                    if (scanState == ScanState.Scanning) "Сканирование..." else "Сканирования",
                    color = TEXT_SEC, fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ── Wi-Fi / TCP вкладка ────────────────────────────────────────────────────────

@Composable
private fun NetworkTabContent(
    host: String,
    port: String,
    busy: Boolean,
    error: String?,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wi-Fi / TCP подключение", color = TEXT_SEC, fontSize = 12.sp)
        Text(
            "Порт: ${MeshDeviceTransport.DEFAULT_TCP_PORT}  ·  mDNS: ${MeshDeviceTransport.MDNS_SERVICE_TYPE}",
            color = TEXT_DIM, fontSize = 10.sp,
        )

        MeshTextField(value = host, onValueChange = onHostChange, label = "Хост или IP", placeholder = MeshDeviceTransport.DEFAULT_TCP_HOST)
        MeshTextField(value = port, onValueChange = onPortChange, label = "Порт", placeholder = MeshDeviceTransport.DEFAULT_TCP_PORT.toString(), keyboardType = KeyboardType.Number)

        error?.let { Text(it, color = RED, fontSize = 12.sp) }

        ConnectButton(busy = busy, label = "Подключить по Wi-Fi", onClick = onConnect)

        Spacer(Modifier.height(4.dp))
    }
}

// ── USB / COM-порт вкладка ─────────────────────────────────────────────────────

@Composable
private fun UsbTabContent(
    usbDevices: List<UsbDevice>,
    autoBaud: Boolean,
    baudInput: String,
    busy: Boolean,
    error: String?,
    onAutoBaudChange: (Boolean) -> Unit,
    onBaudChange: (String) -> Unit,
    onConnect: (UsbDevice) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("USB / COM-порт", color = TEXT_SEC, fontSize = 12.sp)
        Text("Подключите ноду по OTG-кабелю к телефону.", color = TEXT_DIM, fontSize = 11.sp)

        // Auto-baud toggle
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            ) {
                Text("Автоподбор скорости", color = TEXT, fontSize = 13.sp)
                Text(
                    text = if (autoBaud) MeshDeviceTransport.USB_BAUD_CANDIDATES.joinToString(" → ") else "Ввести вручную",
                    color = TEXT_DIM, fontSize = 10.sp,
                    maxLines = 2,
                )
            }
            Switch(checked = autoBaud, onCheckedChange = onAutoBaudChange)
        }

        if (!autoBaud) {
            MeshTextField(value = baudInput, onValueChange = onBaudChange, label = "Baud (скорость)", placeholder = "115200", keyboardType = KeyboardType.Number)
        }

        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ACCENT, strokeWidth = 2.dp)
                Text("Подключение...", color = ACCENT, fontSize = 12.sp)
            }
        }
        error?.let { Text(it, color = RED, fontSize = 12.sp) }

        // USB device list
        if (usbDevices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD)
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("USB-устройства не найдены", color = YELLOW, fontSize = 12.sp)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CARD),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                usbDevices.take(8).forEachIndexed { idx, dev ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(remember { MutableInteractionSource() }, null, enabled = !busy) { onConnect(dev) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(Icons.Default.Usb, null, tint = ACCENT, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dev.deviceName, color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("VID: 0x${dev.vendorId.toString(16).uppercase()}  PID: 0x${dev.productId.toString(16).uppercase()}", color = TEXT_DIM, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(2.dp, TEXT_DIM, CircleShape))
                    }
                    if (idx < (usbDevices.size - 1).coerceAtMost(7))
                        HorizontalDivider(color = DIVIDER, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ── Общие UI-компоненты ───────────────────────────────────────────────────────

@Composable
private fun ConnTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(if (selected) Color(0xFF112A40) else Color.Transparent)
            .clickable(remember { MutableInteractionSource() }, null, enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val tint = when { selected -> ACCENT; enabled -> TEXT_SEC; else -> TEXT_DIM }
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PairedDeviceRow(
    device: MeshDevice,
    isConnected: Boolean,
    isConnecting: Boolean,
    isSelected: Boolean,
    /** Главный чат: тап по строке — «новая нода»; иначе обычное подключение. */
    onNewNodePromptClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "spin")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin",
    )
    val rowEnabled = if (onNewNodePromptClick != null) !isConnecting else !isConnected
    val rowOnClick = onNewNodePromptClick ?: onClick
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(
                remember { MutableInteractionSource() },
                null,
                enabled = rowEnabled,
                onClick = rowOnClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            if (isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
            null, tint = if (isConnected) ACCENT else TEXT_DIM, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            device.name, color = if (isConnected || isSelected) TEXT else TEXT_SEC,
            fontSize = 14.sp, fontWeight = if (isConnected || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        when {
            isConnected -> Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(GREEN),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check, null,
                    tint = Color(0xFF0D1A2E),
                    modifier = Modifier.size(14.dp),
                )
            }
            isConnecting -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp).rotate(rotation), color = ACCENT, strokeWidth = 2.dp, trackColor = DIVIDER,
            )
            else -> Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(2.dp, TEXT_DIM, CircleShape))
        }
    }
}

@Composable
private fun MeshTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TEXT_DIM) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ACCENT,
            unfocusedBorderColor = DIVIDER,
            focusedTextColor = TEXT,
            unfocusedTextColor = TEXT_SEC,
            focusedLabelColor = ACCENT,
            unfocusedLabelColor = TEXT_DIM,
            cursorColor = ACCENT,
            focusedContainerColor = CARD2,
            unfocusedContainerColor = CARD2,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun ConnectButton(busy: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (busy) CARD else Color(0xFF0A2A3A))
            .border(1.dp, if (busy) DIVIDER else ACCENT.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(remember { MutableInteractionSource() }, null, enabled = !busy, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ACCENT, strokeWidth = 2.dp)
                Text("Подключение...", color = ACCENT, fontSize = 13.sp)
            }
        } else {
            Text(label, color = ACCENT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Сканирование BLE ──────────────────────────────────────────────────────────

private fun doScan(
    scanner: MeshBleScanner,
    scope: kotlinx.coroutines.CoroutineScope,
    devices: androidx.compose.runtime.snapshots.SnapshotStateList<MeshDevice>,
    onStateChange: (ScanState) -> Unit,
    onError: (String) -> Unit,
) {
    onStateChange(ScanState.Scanning)
    scanner.startScan(
        onDeviceFound = { dev -> if (devices.none { it.address == dev.address }) devices.add(dev) },
        onError = { msg -> onError(msg) },
        meshOnly = true,
    )
    scope.launch {
        delay(10_000)
        scanner.stopScan()
        onStateChange(ScanState.Done)
    }
}
