package com.example.aura.mesh

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service as AndroidService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.aura.MainActivity
import com.example.aura.R
import com.example.aura.bluetooth.MeshDevice
import com.example.aura.bluetooth.MeshDeviceTransport
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.NodeConnectionState
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.bluetooth.NodeSyncStep
import com.example.aura.bluetooth.tryAutoScanAndConnectSavedBleNode
import com.example.aura.bluetooth.tryAutoConnectSavedBleNode
import com.example.aura.security.NodeAuthStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MessageResilientService"
private const val NOTIF_ID = 70001
private const val CHANNEL_ID = "mesh_fg_service"
internal const val RESTART_ALARM_REQUEST = 0x4D52 // "MR"
private const val RESTART_DELAY_MS = 60_000L

/**
 * Foreground-сервис: постоянное уведомление «Связь с узлом активна».
 * BLE — [NodeGattConnection]; TCP (Wi‑Fi) и USB-serial — [MeshStreamTransportCoordinator].
 *
 * Точка входа для входящих данных — [com.example.aura.mesh.repository.MeshIncomingChatRepository]
 * (глобальный слушатель FromRadio); при получении кадров сообщения пишутся в Room и в системные уведомления.
 *
 * При остановке процесса (кроме явного выхода) планируется перезапуск через [AlarmManager] и
 * периодическая проверка через [ResilientMeshWorker].
 */
class MessageResilientService : AndroidService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = LocalBinder()
    private var meshWakeLock: PowerManager.WakeLock? = null
    private var notifJob: Job? = null
    private var watchdogJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): MessageResilientService = this@MessageResilientService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        ensureChannel(applicationContext)
        runningInstance = this
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        meshWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Aura:MessageResilient").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(applicationContext)
        startForegroundWithPlaceholder()
        if (!shouldRunForCurrentAuth()) {
            stopForeground(AndroidService.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startNodeConnection()
        startStateWatchers()
        cancelMeshRestartAlarm(applicationContext)
        return START_STICKY
    }

    private fun shouldRunForCurrentAuth(): Boolean {
        val auth = NodeAuthStore.load(applicationContext) ?: return false
        val addr = auth.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return true
    }

    private fun foregroundServiceMask(): Int {
        var mask = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        if (Build.VERSION.SDK_INT >= 34) {
            mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return mask
    }

    private fun startForegroundWithPlaceholder() {
        val notif = buildNotification(
            getString(R.string.message_resilient_fg_stable_title),
            getString(R.string.mesh_service_notif_searching),
        )
        val fgType = foregroundServiceMask()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notif,
                fgType,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(stableTitle: String, detailText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(stableTitle)
            .setContentText(detailText)
            .setSmallIcon(R.drawable.ic_launcher_photo)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private fun startNodeConnection() {
        val auth = NodeAuthStore.load(applicationContext) ?: return
        val addr = auth.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val normAddr = MeshNodeSyncMemoryStore.normalizeKey(addr)
        if (MeshDeviceTransport.isTcpAddress(normAddr) || MeshDeviceTransport.isUsbAddress(normAddr)) {
            MeshStreamTransportCoordinator.start(this)
            return
        }
        MeshStreamTransportCoordinator.stop(this)
        val bm = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val btDevice = try {
            bm?.adapter?.getRemoteDevice(addr)
        } catch (_: Exception) {
            null
        }
        if (btDevice == null) {
            Log.w(TAG, "BluetoothDevice недоступен для $addr")
            return
        }
        val name = try {
            btDevice.name?.takeIf { it.isNotBlank() } ?: "mesh"
        } catch (_: SecurityException) {
            "mesh"
        }
        val meshDevice = MeshDevice(name, addr, btDevice)
        Log.d(TAG, "NodeGattConnection.connect(${meshDevice.address})")
        NodeGattConnection.connect(meshDevice, applicationContext)
    }

    private fun startStateWatchers() {
        notifJob?.cancel()
        notifJob = scope.launch {
            val auth = NodeAuthStore.load(applicationContext)
            val norm = auth?.deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { MeshNodeSyncMemoryStore.normalizeKey(it) }
            val stream = norm != null &&
                (MeshDeviceTransport.isTcpAddress(norm) || MeshDeviceTransport.isUsbAddress(norm))
            if (stream) {
                MeshStreamTransportState.ui.collect { ui ->
                    val nm = getSystemService(NotificationManager::class.java) ?: return@collect
                    val stableTitle = getString(R.string.message_resilient_fg_stable_title)
                    nm.notify(NOTIF_ID, buildNotification(stableTitle, ui.detail))
                }
            } else {
                combine(
                    NodeGattConnection.connectionState,
                    NodeGattConnection.syncStep,
                    NodeGattConnection.myNodeNum,
                ) { state, step, _ -> state to step }
                    .collect { (state, step) ->
                        updateNotificationUi(state, step)
                    }
            }
        }
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(22_000)
                if (!maintainInternal) continue
                if (!shouldRunForCurrentAuth()) continue
                val a = NodeAuthStore.load(applicationContext)
                val n = a?.deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { MeshNodeSyncMemoryStore.normalizeKey(it) }
                if (n != null && (MeshDeviceTransport.isTcpAddress(n) || MeshDeviceTransport.isUsbAddress(n))) {
                    continue
                }
                if (NodeGattConnection.isAlive || NodeGattConnection.isReady) continue
                tryAutoConnectSavedBleNode(applicationContext)
                tryAutoScanAndConnectSavedBleNode(applicationContext)
            }
        }
    }

    private fun updateNotificationUi(state: NodeConnectionState, step: NodeSyncStep) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val stableTitle = getString(R.string.message_resilient_fg_stable_title)
        val text = when (state) {
            NodeConnectionState.READY -> {
                val auth = NodeAuthStore.load(applicationContext)
                val addr = auth?.deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
                val norm = addr?.let { MeshNodeSyncMemoryStore.normalizeKey(it) }
                val fromMesh = norm?.let { MeshNodeSyncMemoryStore.getUser(it) }
                val longN = fromMesh?.longName?.trim()?.takeIf { it.isNotEmpty() }
                val shortN = fromMesh?.shortName?.trim()?.takeIf { it.isNotEmpty() }
                val btName = try {
                    NodeGattConnection.targetDevice?.name?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: SecurityException) {
                    null
                }
                val label = longN ?: shortN ?: btName ?: getString(R.string.mesh_service_notif_node_fallback)
                getString(R.string.mesh_service_notif_connected, label)
            }
            NodeConnectionState.CONNECTING,
            NodeConnectionState.HANDSHAKING,
            NodeConnectionState.RECONNECTING,
            -> getString(R.string.mesh_service_notif_connecting_detail, step.label)
            NodeConnectionState.DISCONNECTED ->
                getString(R.string.mesh_service_notif_searching)
        }
        nm.notify(NOTIF_ID, buildNotification(stableTitle, text))
    }

    override fun onDestroy() {
        stopForeground(AndroidService.STOP_FOREGROUND_REMOVE)
        notifJob?.cancel()
        watchdogJob?.cancel()
        scope.cancel()
        NodeGattConnection.disconnect()
        MeshStreamTransportCoordinator.stop(this)
        try {
            meshWakeLock?.let { wl -> if (wl.isHeld) wl.release() }
        } catch (_: RuntimeException) {
        }
        meshWakeLock = null
        runningInstance = null
        super.onDestroy()
        if (maintainInternal && shouldScheduleRestart()) {
            scheduleMeshRestartAlarm(applicationContext)
        }
    }

    private fun shouldScheduleRestart(): Boolean {
        val auth = NodeAuthStore.load(applicationContext) ?: return false
        val addr = auth.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return true
    }

    companion object {
        @Volatile
        var runningInstance: MessageResilientService? = null
            private set

        @Volatile
        private var maintainInternal: Boolean = true

        fun maintainConnection(): Boolean = maintainInternal

        fun setMaintainConnection(value: Boolean) {
            maintainInternal = value
        }

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val ch = android.app.NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.notif_channel_service_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = ctx.getString(R.string.notif_channel_service_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        fun startIfNeeded(context: Context) {
            val app = context.applicationContext
            if (!maintainInternal) return
            val auth = NodeAuthStore.load(app) ?: return
            val addr = auth.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                val ok = ContextCompat.checkSelfPermission(
                    app,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!ok) {
                    Log.w(TAG, "POST_NOTIFICATIONS не выдан — foreground mesh не стартует")
                    return
                }
            }
            ensureChannel(app)
            try {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, MessageResilientService::class.java),
                )
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService", e)
            }
        }

        fun stopForLogout(context: Context) {
            maintainInternal = false
            cancelMeshRestartAlarm(context.applicationContext)
            NodeGattConnection.disconnect()
            MeshStreamTransportCoordinator.stop(context.applicationContext)
            val app = context.applicationContext
            app.stopService(Intent(app, MessageResilientService::class.java))
        }

        fun stopForUserBleDisconnect(context: Context) {
            maintainInternal = false
            cancelMeshRestartAlarm(context.applicationContext)
            NodeGattConnection.disconnect()
            MeshStreamTransportCoordinator.stop(context.applicationContext)
            val app = context.applicationContext
            app.stopService(Intent(app, MessageResilientService::class.java))
        }

        fun resumeAfterUserBleConnect(context: Context) {
            maintainInternal = true
            startIfNeeded(context)
        }
    }
}

private fun scheduleMeshRestartAlarm(ctx: Context) {
    val app = ctx.applicationContext
    val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pi = PendingIntent.getBroadcast(
        app,
        RESTART_ALARM_REQUEST,
        Intent(app, MessageResilientRestartReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val triggerAt = System.currentTimeMillis() + RESTART_DELAY_MS
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                Log.w(
                    "MessageResilientService",
                    "scheduleMeshRestartAlarm: exact alarms not allowed; skipping",
                )
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    } catch (e: SecurityException) {
        Log.w("MessageResilientService", "scheduleMeshRestartAlarm", e)
    } catch (e: Exception) {
        Log.w("MessageResilientService", "scheduleMeshRestartAlarm", e)
    }
}

private fun cancelMeshRestartAlarm(ctx: Context) {
    val app = ctx.applicationContext
    val pi = PendingIntent.getBroadcast(
        app,
        RESTART_ALARM_REQUEST,
        Intent(app, MessageResilientRestartReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    am.cancel(pi)
}

/**
 * Совместимость с существующим кодом: [MeshService] → [MessageResilientService].
 */
object MeshService {
    fun maintainConnection(): Boolean = MessageResilientService.maintainConnection()
    fun setMaintainConnection(value: Boolean) = MessageResilientService.setMaintainConnection(value)
    fun ensureChannel(ctx: Context) = MessageResilientService.ensureChannel(ctx)
    fun startIfNeeded(context: Context) = MessageResilientService.startIfNeeded(context)
    fun stopForLogout(context: Context) = MessageResilientService.stopForLogout(context)
    fun stopForUserBleDisconnect(context: Context) = MessageResilientService.stopForUserBleDisconnect(context)
    fun resumeAfterUserBleConnect(context: Context) = MessageResilientService.resumeAfterUserBleConnect(context)
}
