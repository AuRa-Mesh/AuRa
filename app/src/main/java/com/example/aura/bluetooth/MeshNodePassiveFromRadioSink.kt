package com.example.aura.bluetooth

import com.example.aura.meshwire.MeshWireChannelsSyncAccumulator
import com.example.aura.meshwire.MeshWireDeviceSyncAccumulator
import com.example.aura.meshwire.MeshWireExternalNotificationSyncAccumulator
import com.example.aura.meshwire.MeshWireFromRadioProfileAccumulator
import com.example.aura.meshwire.MeshWireLoRaSyncAccumulator
import com.example.aura.meshwire.MeshWireMqttSyncAccumulator
import com.example.aura.meshwire.MeshWireSecuritySyncAccumulator
import com.example.aura.meshwire.MeshWireTelemetrySyncAccumulator

/**
 * Пассивное наполнение [MeshNodeSyncMemoryStore] из потока [FromRadio] (Config / ModuleConfig / Channel),
 * как в официальном mesh после want_config — без ожидания READY и без Admin GET.
 *
 * Вызывается с [com.example.aura.bluetooth.NodeGattConnection.fromRadioParseExecutor] до доставки
 * кадра слушателям на main.
 */
object MeshNodePassiveFromRadioSink {

    /** В текущей BLE-сессии пришёл config_complete_id вместе с потоком каналов (want_config dump). */
    @Volatile
    var passiveChannelStreamComplete: Boolean = false
        private set

    private val sessionLock = Any()

    private var profileAcc = MeshWireFromRadioProfileAccumulator()
    private var loraAcc = MeshWireLoRaSyncAccumulator()
    private var deviceAcc = MeshWireDeviceSyncAccumulator()
    private var securityAcc = MeshWireSecuritySyncAccumulator()
    private var mqttAcc = MeshWireMqttSyncAccumulator()
    private var extAcc = MeshWireExternalNotificationSyncAccumulator()
    private var telemetryAcc = MeshWireTelemetrySyncAccumulator()
    private var channelsAcc = MeshWireChannelsSyncAccumulator()

    fun resetSession() {
        synchronized(sessionLock) {
            passiveChannelStreamComplete = false
            profileAcc = MeshWireFromRadioProfileAccumulator()
            loraAcc = MeshWireLoRaSyncAccumulator()
            deviceAcc = MeshWireDeviceSyncAccumulator()
            securityAcc = MeshWireSecuritySyncAccumulator()
            mqttAcc = MeshWireMqttSyncAccumulator()
            extAcc = MeshWireExternalNotificationSyncAccumulator()
            telemetryAcc = MeshWireTelemetrySyncAccumulator()
            channelsAcc = MeshWireChannelsSyncAccumulator()
        }
    }

    /**
     * Разбор одного непустого кадра FromRadio и обновление RAM+disk кэша.
     */
    fun consumeFrame(rawDeviceAddress: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val mac = MeshNodeSyncMemoryStore.normalizeKey(rawDeviceAddress)
        synchronized(sessionLock) {
            profileAcc.consumeFromRadio(bytes)
            loraAcc.consumeFromRadio(bytes)
            deviceAcc.consumeFromRadio(bytes)
            securityAcc.consumeFromRadio(bytes)
            mqttAcc.consumeFromRadio(bytes)
            extAcc.consumeFromRadio(bytes)
            telemetryAcc.consumeFromRadio(bytes)
            channelsAcc.consumeFromRadio(bytes)

            if (loraAcc.sawLoRa) {
                runCatching { MeshNodeSyncMemoryStore.putLora(mac, loraAcc.toPushState()) }
            }
            if (deviceAcc.sawDevice) {
                runCatching { MeshNodeSyncMemoryStore.putDevice(mac, deviceAcc.toPushState()) }
            }
            if (securityAcc.sawSecurity) {
                runCatching { MeshNodeSyncMemoryStore.putSecurity(mac, securityAcc.toPushState()) }
            }
            if (mqttAcc.sawMqtt) {
                runCatching { MeshNodeSyncMemoryStore.putMqtt(mac, mqttAcc.toPushState()) }
            }
            if (extAcc.sawExternal) {
                runCatching { MeshNodeSyncMemoryStore.putExternalNotification(mac, extAcc.toPushState()) }
            }
            if (telemetryAcc.sawTelemetry) {
                runCatching { MeshNodeSyncMemoryStore.putTelemetry(mac, telemetryAcc.toPushState()) }
            }

            when {
                profileAcc.hasMinimumUserFields() -> {
                    profileAcc.toProfileOrNull()?.let {
                        runCatching { MeshNodeSyncMemoryStore.putUser(mac, it) }
                    }
                }
                profileAcc.configComplete -> {
                    runCatching { MeshNodeSyncMemoryStore.putUser(mac, profileAcc.toProfileMaximal()) }
                }
            }

            val chResult = channelsAcc.toResult()
            if (chResult.channels.isNotEmpty() ||
                channelsAcc.loraFrequencyMhz != null ||
                channelsAcc.loraChannelNum != null
            ) {
                runCatching { MeshNodeSyncMemoryStore.putChannelsMerged(mac, chResult) }
            }
            if (channelsAcc.shouldFinish()) {
                passiveChannelStreamComplete = true
            }
        }
    }
}
