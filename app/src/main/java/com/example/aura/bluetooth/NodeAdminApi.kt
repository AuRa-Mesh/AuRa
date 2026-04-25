package com.example.aura.bluetooth

import android.util.Log
import com.example.aura.meshwire.MeshWireChannelsSyncAccumulator
import com.example.aura.meshwire.MeshWireChannelsSyncResult
import com.example.aura.meshwire.MeshWireDevicePushState
import com.example.aura.meshwire.MeshWireDeviceSyncAccumulator
import com.example.aura.meshwire.MeshWireExternalNotificationPushState
import com.example.aura.meshwire.MeshWireExternalNotificationSyncAccumulator
import com.example.aura.meshwire.MeshWireFromRadioProfileAccumulator
import com.example.aura.meshwire.MeshWireLoRaPushState
import com.example.aura.meshwire.MeshWireLoRaSyncAccumulator
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireMqttPushState
import com.example.aura.meshwire.MeshWireMqttSyncAccumulator
import com.example.aura.meshwire.MeshWireNodeUserProfile
import com.example.aura.meshwire.MeshWirePositionPushState
import com.example.aura.meshwire.MeshWirePositionSyncAccumulator
import com.example.aura.meshwire.MeshWireSecurityPushState
import com.example.aura.meshwire.MeshWireSecuritySyncAccumulator
import com.example.aura.meshwire.MeshWireTelemetryPushState
import com.example.aura.meshwire.MeshWireTelemetrySyncAccumulator
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireWantConfigHandshake
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "NodeAdminApi"
private const val WAIT_READY_MS = 12_000L
/** Жёсткий лимит ответа на любой Admin GET / want_config (каналы) после sendToRadio. */
private const val ADMIN_GET_TIMEOUT_MS = 5_000L
/** Mutex + ожидание READY + один GET (очередь UI не блокируется бесконечно). */
private const val SETTINGS_MUTEX_AND_RESPONSE_MAX_MS = 25_000L

/**
 * Coroutine API для чтения настроек через постоянное [NodeGattConnection].
 *
 * Каждая функция:
 * 1. При необходимости ждёт перехода в READY (до [WAIT_READY_MS]).
 * 2. Отправляет admin-пакет через [NodeGattConnection.sendToRadio].
 * 3. Регистрирует слушатель входящих кадров и ждёт нужного ответа.
 * 4. Возвращает результат или null при таймауте.
 *
 * [requestMutex] — один поток настроек за раз: want_config ([getChannels]) и admin get не должны
 * идти параллельно — иначе FromRadio и нода дают нестабильный ответ.
 *
 * Внешний [withTimeoutOrNull](SETTINGS_MUTEX_AND_RESPONSE_MAX_MS) ограничивает ожидание mutex + работу;
 * внутри — [ADMIN_GET_TIMEOUT_MS] на ответ. Слушатель FromRadio снимается в `finally`, чтобы Mutex не залипал.
 */
object NodeAdminApi {

    private val requestMutex = Mutex()

    private fun hexPrefix(bytes: ByteArray, max: Int = 28): String =
        bytes.take(minOf(max, bytes.size)).joinToString("") { b ->
            (b.toInt() and 0xFF).toString(16).padStart(2, '0')
        }

    private fun portnumsSummary(bytes: ByteArray): String {
        val ps = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(bytes)
        if (ps.isEmpty()) return "—"
        return ps.distinctBy { it.portnum }.joinToString(",") { it.portnum.toString() }
    }

    private fun logIncomingFrame(logLabel: String, bytes: ByteArray) {
        Log.d(TAG, "← $logLabel FromRadio ${bytes.size}B portnum=[${portnumsSummary(bytes)}]")
    }

    // ── Вспомогательная функция ───────────────────────────────────────────────

    private suspend fun awaitReady(): Boolean {
        if (NodeGattConnection.isReady) return true
        return withTimeoutOrNull(WAIT_READY_MS) {
            NodeGattConnection.connectionState
                .filter { it == NodeConnectionState.READY }
                .first()
            true
        } ?: false
    }

    /**
     * Отправляет [adminPkt] и ждёт ответа, который распознаёт [accumulate]+[checkDone].
     */
    private suspend fun <T : Any> adminRequest(
        logLabel: String,
        adminPkt: ByteArray,
        timeoutMs: Long = ADMIN_GET_TIMEOUT_MS,
        accumulate: (ByteArray) -> Unit,
        checkDone: () -> T?,
    ): T? = withTimeoutOrNull(SETTINGS_MUTEX_AND_RESPONSE_MAX_MS) {
        requestMutex.withLock {
            var frameListener: ((ByteArray) -> Unit)? = null
            try {
                if (!awaitReady()) {
                    Log.w(TAG, "→ $logLabel: нода не READY")
                    return@withLock null
                }
                Log.d(TAG, "→ $logLabel admin ${adminPkt.size}B hex=${hexPrefix(adminPkt)}")
                val result = withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine { cont ->
                        fun unregister() {
                            frameListener?.let { NodeGattConnection.removeFrameListener(it) }
                            frameListener = null
                        }

                        val l: (ByteArray) -> Unit = inner@{ bytes ->
                            logIncomingFrame(logLabel, bytes)
                            accumulate(bytes)
                            val done = checkDone() ?: return@inner
                            if (!cont.isActive) return@inner
                            unregister()
                            cont.resume(done)
                        }
                        frameListener = l
                        cont.invokeOnCancellation { unregister() }
                        NodeGattConnection.addFrameListener(l)

                        NodeGattConnection.sendToRadio(adminPkt) { ok, err ->
                            if (!ok && cont.isActive) {
                                Log.w(TAG, "→ $logLabel sendToRadio failed: $err")
                                unregister()
                                cont.resume(null)
                            }
                        }
                    }
                }
                if (result == null) {
                    Log.w(TAG, "← $logLabel: таймаут ${timeoutMs}ms или null")
                } else {
                    Log.d(TAG, "← $logLabel: ответ получен")
                }
                result
            } finally {
                frameListener?.let { NodeGattConnection.removeFrameListener(it) }
            }
        }
    } ?: run {
        Log.w(TAG, "← $logLabel: лимит ${SETTINGS_MUTEX_AND_RESPONSE_MAX_MS}ms (mutex/очередь)")
        null
    }

    // ── Settings reads ────────────────────────────────────────────────────────

    suspend fun getLoRaConfig(localNodeNum: UInt): MeshWireLoRaPushState? {
        val pkt = MeshWireLoRaToRadioEncoder.encodeAdminGetConfigRequest(
            MeshWireLoRaToRadioEncoder.ADMIN_CONFIG_LORA,
            MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = localNodeNum),
        )
        val acc = MeshWireLoRaSyncAccumulator()
        return adminRequest(
            "getLoRaConfig",
            pkt,
            accumulate = { acc.consumeFromRadio(it) },
            checkDone = { if (acc.sawLoRa) acc.toPushState() else null },
        )
    }

    suspend fun getDeviceConfig(localNodeNum: UInt): MeshWireDevicePushState? {
        val pkt = MeshWireLoRaToRadioEncoder.encodeAdminGetConfigRequest(
            MeshWireLoRaToRadioEncoder.ADMIN_CONFIG_DEVICE,
            MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = localNodeNum),
        )
        val acc = MeshWireDeviceSyncAccumulator()
        return adminRequest(
            "getDeviceConfig",
            pkt,
            accumulate = { acc.consumeFromRadio(it) },
            checkDone = { if (acc.sawDevice) acc.toPushState() else null },
        )
    }

    suspend fun getPositionConfig(localNodeNum: UInt): MeshWirePositionPushState? {
        val pkt = MeshWireLoRaToRadioEncoder.encodeAdminGetConfigRequest(
            MeshWireLoRaToRadioEncoder.ADMIN_CONFIG_POSITION,
            MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = localNodeNum),
        )
        val acc = MeshWirePositionSyncAccumulator()
        return adminRequest(
            "getPositionConfig",
            pkt,
            accumulate = { acc.consumeFromRadio(it) },
            checkDone = { if (acc.sawPosition) acc.toPushState() else null },
        )
    }

    suspend fun getSecurityConfig(localNodeNum: UInt): MeshWireSecurityPushState? {
        val pkt = MeshWireLoRaToRadioEncoder.encodeAdminGetConfigRequest(
            MeshWireLoRaToRadioEncoder.ADMIN_CONFIG_SECURITY,
            MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = localNodeNum),
        )
        val acc = MeshWireSecuritySyncAccumulator()
        return adminRequest(
            "getSecurityConfig",
            pkt,
            accumulate = { acc.consumeFromRadio(it) },
            checkDone = { if (acc.sawSecurity) acc.toPushState() else null },
        )
    }

    suspend fun getMqttConfig(localNodeNum: UInt): MeshWireMqttPushState? {
        val pkt = MeshWireLoRaToRadioEncoder.encodeAdminGetModuleConfigRequest(
            MeshWireLoRaToRadioEncoder.ADMIN_MODULE_MQTT,
            MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = localNodeNum),
        )
        val acc = MeshWireMqttSyncAccumulator()
        return adminRequest(
            "getMqttConfig",
            pkt,
            accumulate = { acc.consumeFromRadio(it) },
            checkDone = { if (acc.sawMqtt) acc.toPushState() else null },
        )
    }

    suspend fun getExternalNotificationConfig(localNodeNum: UInt): MeshWireExternalNotificationPushState? {
        val pkt = MeshWireLoRaToRadioEncoder.encodeAdminGetModuleConfigRequest(
            MeshWireLoRaToRadioEncoder.ADMIN_MODULE_EXTNOTIF,
            MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = localNodeNum),
        )
        val acc = MeshWireExternalNotificationSyncAccumulator()
        return adminRequest(
            "getExternalNotificationConfig",
            pkt,
            accumulate = { acc.consumeFromRadio(it) },
            checkDone = { if (acc.sawExternal) acc.toPushState() else null },
        )
    }

    suspend fun getTelemetryConfig(localNodeNum: UInt): MeshWireTelemetryPushState? {
        val pkt = MeshWireLoRaToRadioEncoder.encodeAdminGetModuleConfigRequest(
            MeshWireLoRaToRadioEncoder.ADMIN_MODULE_TELEMETRY,
            MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = localNodeNum),
        )
        val acc = MeshWireTelemetrySyncAccumulator()
        return adminRequest(
            "getTelemetryConfig",
            pkt,
            accumulate = { acc.consumeFromRadio(it) },
            checkDone = { if (acc.sawTelemetry) acc.toPushState() else null },
        )
    }

    suspend fun getUserProfile(localNodeNum: UInt, nodeNumHint: UInt? = null): MeshWireNodeUserProfile? {
        val pkt = MeshWireLoRaToRadioEncoder.encodeAdminGetOwnerToRadio(
            MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = localNodeNum),
        )
        val acc = MeshWireFromRadioProfileAccumulator().apply {
            if (nodeNumHint != null) expectedNodeNum = nodeNumHint.toLong() and 0xFFFFFFFFL
        }
        return adminRequest(
            "getUserProfile",
            pkt,
            accumulate = { acc.consumeFromRadio(it) },
            checkDone = {
                when {
                    acc.hasMinimumUserFields() -> acc.toProfileOrNull() ?: acc.toProfileMaximal()
                    acc.shouldFinish() -> acc.toProfileMaximal()
                    else -> null
                }
            },
        )
    }

    /**
     * Читает каналы через поток want_config.
     * Отправляет want_config через постоянное соединение и накапливает каналы до config_complete.
     */
    suspend fun getChannels(): MeshWireChannelsSyncResult? = withTimeoutOrNull(SETTINGS_MUTEX_AND_RESPONSE_MAX_MS) {
        requestMutex.withLock {
            var frameListener: ((ByteArray) -> Unit)? = null
            try {
                if (!awaitReady()) {
                    Log.w(TAG, "→ getChannels: нода не READY")
                    return@withLock null
                }
                val wantConfigPkt = MeshWireWantConfigHandshake.encodeToRadioWantConfigId(
                    MeshWireWantConfigHandshake.CONFIG_NONCE,
                )
                Log.d(TAG, "→ getChannels want_config_id ${wantConfigPkt.size}B hex=${hexPrefix(wantConfigPkt)}")
                val acc = MeshWireChannelsSyncAccumulator()
                var frames = 0

                val out = withTimeoutOrNull(ADMIN_GET_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        fun unregister() {
                            frameListener?.let { NodeGattConnection.removeFrameListener(it) }
                            frameListener = null
                        }

                        val l: (ByteArray) -> Unit = inner@{ bytes ->
                            frames++
                            when {
                                frames <= 25 -> logIncomingFrame("getChannels#$frames", bytes)
                                frames % 100 == 0 -> Log.d(
                                    TAG,
                                    "← getChannels#$frames FromRadio ${bytes.size}B portnum=[${portnumsSummary(bytes)}]",
                                )
                            }
                            acc.consumeFromRadio(bytes)
                            if (!acc.shouldFinish() && frames < 420) return@inner
                            if (!cont.isActive) return@inner
                            unregister()
                            val r = acc.toResult()
                            cont.resume(if (r.channels.isEmpty()) null else r)
                        }
                        frameListener = l
                        cont.invokeOnCancellation { unregister() }
                        NodeGattConnection.addFrameListener(l)

                        NodeGattConnection.sendToRadio(wantConfigPkt) { ok, err ->
                            if (!ok && cont.isActive) {
                                Log.w(TAG, "→ getChannels sendToRadio failed: $err")
                                unregister()
                                cont.resume(null)
                            }
                        }
                    }
                }
                if (out == null) {
                    Log.w(TAG, "← getChannels: таймаут ${ADMIN_GET_TIMEOUT_MS}ms (принято кадров=$frames)")
                } else {
                    Log.d(TAG, "← getChannels: ok (кадров=$frames)")
                }
                out
            } finally {
                frameListener?.let { NodeGattConnection.removeFrameListener(it) }
            }
        }
    } ?: run {
        Log.w(TAG, "← getChannels: лимит ${SETTINGS_MUTEX_AND_RESPONSE_MAX_MS}ms (mutex/очередь)")
        null
    }
}
