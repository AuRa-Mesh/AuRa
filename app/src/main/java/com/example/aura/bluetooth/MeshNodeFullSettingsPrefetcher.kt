package com.example.aura.bluetooth

import android.content.Context
import android.util.Log
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.security.NodeAuthStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "MeshNodePrefetch"

/**
 * **Phase 3 (fallback):** после [NodeGattConnection.initialWantConfigAcknowledged] и READY дозаполняет
 * [MeshNodeSyncMemoryStore] (RAM + SharedPreferences), пропуская секции, уже заполненные
 * [MeshNodePassiveFromRadioSink] / кэшем. Каналы не запрашиваются повторно, если дамп want_config
 * завершён и список непустой ([MeshNodePassiveFromRadioSink.passiveChannelStreamComplete]).
 *
 * Старт при живом GATT и MAC (**prefs** или [NodeGattConnection.targetDevice]). Подсказка nodenum —
 * prefs или [NodeGattConnection.myNodeNum]. Каждая секция — свой try/catch.
 */
object MeshNodeFullSettingsPrefetcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null

    fun start(appContext: Context) {
        val app = appContext.applicationContext
        observeJob?.cancel()
        observeJob = scope.launch {
            var previousAck = false
            NodeGattConnection.initialWantConfigAcknowledged.collect { ack ->
                if (ack && !previousAck) {
                    val fromPrefs = NodeAuthStore.loadDeviceAddressForPrefetch(app)?.trim()?.takeIf { it.isNotEmpty() }
                    // MAC в prefs попадает из onDeviceLinked после READY — до этого берём адрес активного GATT.
                    val addr = fromPrefs ?: NodeGattConnection.targetDevice?.address?.trim()?.takeIf { it.isNotEmpty() }
                    // Не требуем nodeId в prefs: при первом BLE save выполняется после READY в UI-корутине.
                    if (addr != null && NodeGattConnection.isAlive) {
                        try {
                            MeshNodeSyncMemoryStore.warmFromDisk(addr)
                            runFullPrefetch(app, addr)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "runFullPrefetch: ${e.message}", e)
                        }
                    }
                }
                previousAck = ack
            }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
    }

    private suspend fun runFullPrefetch(app: Context, addr: String) {
        val fromPrefs = NodeAuthStore.loadNodeIdForPrefetch(app).trim()
        val hint = MeshWireNodeNum.parseToUInt(fromPrefs)
            ?: NodeGattConnection.myNodeNum.value?.takeIf { it != 0u }
        val k = MeshNodeSyncMemoryStore.normalizeKey(addr)
        Log.i(TAG, "Проверка кэша / резервная выгрузка ($addr)")
        if (!cacheHasUsableUser(k)) {
            safePrefetch("user") { prefetchUser(app, addr, hint) }
        } else {
            Log.d(TAG, "skip user — уже в кэше")
        }
        currentCoroutineContext().ensureActive()
        if (!cacheHasLora(k)) {
            safePrefetch("lora") { prefetchLoRa(app, addr, hint) }
        } else {
            Log.d(TAG, "skip lora — уже в кэше")
        }
        currentCoroutineContext().ensureActive()
        if (!cacheHasSecurity(k)) {
            safePrefetch("security") { prefetchSecurity(app, addr, hint) }
        } else {
            Log.d(TAG, "skip security — уже в кэше")
        }
        currentCoroutineContext().ensureActive()
        if (!cacheHasDevice(k)) {
            safePrefetch("device") { prefetchDevice(app, addr, hint) }
        } else {
            Log.d(TAG, "skip device — уже в кэше")
        }
        currentCoroutineContext().ensureActive()
        if (!cacheHasMqtt(k)) {
            safePrefetch("mqtt") { prefetchMqtt(app, addr, hint) }
        } else {
            Log.d(TAG, "skip mqtt — уже в кэше")
        }
        currentCoroutineContext().ensureActive()
        if (!cacheHasExtNotif(k)) {
            safePrefetch("extNotif") { prefetchExtNotif(app, addr, hint) }
        } else {
            Log.d(TAG, "skip extNotif — уже в кэше")
        }
        currentCoroutineContext().ensureActive()
        if (!cacheHasTelemetry(k)) {
            safePrefetch("telemetry") { prefetchTelemetry(app, addr, hint) }
        } else {
            Log.d(TAG, "skip telemetry — уже в кэше")
        }
        currentCoroutineContext().ensureActive()
        if (!cacheHasChannelsComplete(k)) {
            safePrefetch("channels") { prefetchChannels(app, addr, hint) }
        } else {
            Log.d(TAG, "skip channels — дамп want_config в кэше")
        }
        Log.i(TAG, "Кэш настроек обновлён ($addr)")
    }

    private fun cacheHasUsableUser(k: String): Boolean {
        val u = MeshNodeSyncMemoryStore.getUser(k) ?: return false
        return u.longName.isNotBlank() || u.shortName.isNotBlank()
    }

    private fun cacheHasLora(k: String): Boolean = MeshNodeSyncMemoryStore.getLora(k) != null

    private fun cacheHasSecurity(k: String): Boolean = MeshNodeSyncMemoryStore.getSecurity(k) != null

    private fun cacheHasDevice(k: String): Boolean = MeshNodeSyncMemoryStore.getDevice(k) != null

    private fun cacheHasMqtt(k: String): Boolean = MeshNodeSyncMemoryStore.getMqtt(k) != null

    private fun cacheHasExtNotif(k: String): Boolean =
        MeshNodeSyncMemoryStore.getExternalNotification(k) != null

    private fun cacheHasTelemetry(k: String): Boolean =
        MeshNodeSyncMemoryStore.getTelemetry(k) != null

    /** Каналы приходят по одному в FromRadio — не дергаем want_config повторно, если дамп завершён. */
    private fun cacheHasChannelsComplete(k: String): Boolean {
        val ch = MeshNodeSyncMemoryStore.getChannels(k) ?: return false
        if (ch.channels.isEmpty()) return false
        return MeshNodePassiveFromRadioSink.passiveChannelStreamComplete
    }

    private suspend fun safePrefetch(step: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch $step: ${e.message}", e)
        }
    }

    private suspend fun prefetchUser(app: Context, addr: String, hint: UInt?) = suspendCancellableCoroutine { cont ->
        try {
            fetchMeshWireUserProfile(
                app, addr,
                expectedNodeNum = hint,
                onSyncProgress = null,
                localNodeNum = hint,
            ) { p, _ ->
                runCatching {
                    if (p != null) MeshNodeSyncMemoryStore.putUser(addr, p)
                }.onFailure { Log.w(TAG, "prefetch user put: ${it.message}", it) }
                cont.resume(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch user fetch: ${e.message}", e)
            cont.resume(Unit)
        }
    }

    private suspend fun prefetchLoRa(app: Context, addr: String, hint: UInt?) = suspendCancellableCoroutine { cont ->
        try {
            fetchMeshWireLoRaConfig(app, addr, onSyncProgress = null, localNodeNum = hint) { s, _ ->
                runCatching {
                    if (s != null) MeshNodeSyncMemoryStore.putLora(addr, s)
                }.onFailure { Log.w(TAG, "prefetch lora put: ${it.message}", it) }
                cont.resume(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch lora fetch: ${e.message}", e)
            cont.resume(Unit)
        }
    }

    private suspend fun prefetchSecurity(app: Context, addr: String, hint: UInt?) = suspendCancellableCoroutine { cont ->
        try {
            fetchMeshWireSecurityConfig(app, addr, onSyncProgress = null, localNodeNum = hint) { s, _ ->
                runCatching {
                    if (s != null) MeshNodeSyncMemoryStore.putSecurity(addr, s)
                }.onFailure { Log.w(TAG, "prefetch security put: ${it.message}", it) }
                cont.resume(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch security fetch: ${e.message}", e)
            cont.resume(Unit)
        }
    }

    private suspend fun prefetchDevice(app: Context, addr: String, hint: UInt?) = suspendCancellableCoroutine { cont ->
        try {
            fetchMeshWireDeviceConfig(app, addr, onSyncProgress = null, localNodeNum = hint) { s, _ ->
                runCatching {
                    if (s != null) MeshNodeSyncMemoryStore.putDevice(addr, s)
                }.onFailure { Log.w(TAG, "prefetch device put: ${it.message}", it) }
                cont.resume(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch device fetch: ${e.message}", e)
            cont.resume(Unit)
        }
    }

    private suspend fun prefetchMqtt(app: Context, addr: String, hint: UInt?) = suspendCancellableCoroutine { cont ->
        try {
            fetchMeshWireMqttConfig(app, addr, onSyncProgress = null, localNodeNum = hint) { s, _ ->
                runCatching {
                    if (s != null) MeshNodeSyncMemoryStore.putMqtt(addr, s)
                }.onFailure { Log.w(TAG, "prefetch mqtt put: ${it.message}", it) }
                cont.resume(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch mqtt fetch: ${e.message}", e)
            cont.resume(Unit)
        }
    }

    private suspend fun prefetchExtNotif(app: Context, addr: String, hint: UInt?) = suspendCancellableCoroutine { cont ->
        try {
            fetchMeshWireExternalNotificationConfig(app, addr, onSyncProgress = null, localNodeNum = hint) { s, _ ->
                runCatching {
                    if (s != null) MeshNodeSyncMemoryStore.putExternalNotification(addr, s)
                }.onFailure { Log.w(TAG, "prefetch extNotif put: ${it.message}", it) }
                cont.resume(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch extNotif fetch: ${e.message}", e)
            cont.resume(Unit)
        }
    }

    private suspend fun prefetchTelemetry(app: Context, addr: String, hint: UInt?) = suspendCancellableCoroutine { cont ->
        try {
            fetchMeshWireTelemetryConfig(app, addr, onSyncProgress = null, localNodeNum = hint) { s, _ ->
                runCatching {
                    if (s != null) MeshNodeSyncMemoryStore.putTelemetry(addr, s)
                }.onFailure { Log.w(TAG, "prefetch telemetry put: ${it.message}", it) }
                cont.resume(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch telemetry fetch: ${e.message}", e)
            cont.resume(Unit)
        }
    }

    private suspend fun prefetchChannels(app: Context, addr: String, hint: UInt?) = suspendCancellableCoroutine { cont ->
        try {
            fetchMeshWireChannels(app, addr, onSyncProgress = null, localNodeNum = hint) { r, _ ->
                runCatching {
                    if (r != null) MeshNodeSyncMemoryStore.putChannels(addr, r)
                }.onFailure { Log.w(TAG, "prefetch channels put: ${it.message}", it) }
                cont.resume(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prefetch channels fetch: ${e.message}", e)
            cont.resume(Unit)
        }
    }
}
