package com.example.aura.bluetooth

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Одно-пакетные записи в ToRadio ([MeshGattToRadioWriter]) для экрана узла,
 * в стиле mesh-каналов (traceroute, neighborinfo, admin, telemetry probes).
 */
object MeshNodeRemoteActions {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun deviceParams(destinationNodeNum: UInt) =
        MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = destinationNodeNum)

    private fun send(
        context: Context,
        deviceAddress: String,
        payload: ByteArray,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        MeshGattToRadioWriter(context.applicationContext).writeToradioWithMeshDrain(deviceAddress, payload) { ok, err, summary ->
            mainHandler.post { onDone(ok, err, summary) }
        }
    }

    fun sendTraceroute(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeTracerouteRequestToRadio(targetMeshNodeNum)
        send(context, deviceAddress, payload, onDone)
    }

    fun sendNodeInfoRequest(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        requestRemoteNodeInfo(
            context = context,
            deviceAddress = deviceAddress,
            targetNodeNum = targetMeshNodeNum.toInt(),
            onDone = onDone,
        )
    }

    /**
     * Принудительный запрос карточки удалённого узла (On-Demand NodeInfo Request).
     * [targetNodeNum] передаётся как `Int` (битовая репрезентация uint32 node num), затем кодируется в `UInt`.
     */
    fun requestRemoteNodeInfo(
        context: Context,
        deviceAddress: String,
        targetNodeNum: Int,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeNodeInfoRequestToRadio(targetNodeNum.toUInt())
        send(context, deviceAddress, payload, onDone)
    }

    fun sendPositionRequest(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodePositionRequestToRadio(targetMeshNodeNum)
        send(context, deviceAddress, payload, onDone)
    }

    fun sendNeighborInfoRequest(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeNeighborInfoRequestToRadio(targetMeshNodeNum)
        send(context, deviceAddress, payload, onDone)
    }

    /** Admin на удалённый узел: get_owner_request. */
    fun sendAdminGetOwner(
        context: Context,
        deviceAddress: String,
        adminDestNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminGetOwnerToRadio(deviceParams(adminDestNodeNum))
        send(context, deviceAddress, payload, onDone)
    }

    fun sendAdminGetDeviceMetadata(
        context: Context,
        deviceAddress: String,
        adminDestNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminGetDeviceMetadataToRadio(deviceParams(adminDestNodeNum))
        send(context, deviceAddress, payload, onDone)
    }

    fun sendTelemetryDeviceMetricsProbe(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeTelemetryDeviceMetricsProbeToRadio(targetMeshNodeNum)
        send(context, deviceAddress, payload, onDone)
    }

    fun sendTelemetryEnvironmentProbe(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeTelemetryEnvironmentProbeToRadio(targetMeshNodeNum)
        send(context, deviceAddress, payload, onDone)
    }

    fun sendTelemetryAirQualityProbe(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeTelemetryAirQualityProbeToRadio(targetMeshNodeNum)
        send(context, deviceAddress, payload, onDone)
    }

    fun sendTelemetryPowerProbe(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeTelemetryPowerProbeToRadio(targetMeshNodeNum)
        send(context, deviceAddress, payload, onDone)
    }

    fun sendTelemetryLocalStatsProbe(
        context: Context,
        deviceAddress: String,
        targetMeshNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeTelemetryLocalStatsProbeToRadio(targetMeshNodeNum)
        send(context, deviceAddress, payload, onDone)
    }

    /** Admin на **свою** привязанную ноду (владелец NodeDB). */
    fun sendAdminRemoveNode(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        removeNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminRemoveNodeByNum(removeNodeNum, deviceParams(localRadioNodeNum))
        send(context, deviceAddress, payload, onDone)
    }

    fun sendAdminSetFavorite(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        favoriteNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminSetFavoriteNode(favoriteNodeNum, deviceParams(localRadioNodeNum))
        send(context, deviceAddress, payload, onDone)
    }

    /**
     * Сброс NodeDB на привязанной ноде (как в типичном mesh-клиенте: AdminMessage.nodedb_reset).
     * Без drain FromRadio: после команды нода часто перезагружается, чтение FromRadio даёт GATT 133,
     * хотя ToRadio уже принят — иначе UI показывает ложную ошибку.
     */
    fun sendAdminNodedbReset(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        preserveFavorites: Boolean,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminNodedbReset(
            preserveFavorites,
            deviceParams(localRadioNodeNum),
        )
        val finished = AtomicBoolean(false)
        fun finishOnce(ok: Boolean, err: String?) {
            if (!finished.compareAndSet(false, true)) return
            onDone(ok, err, null)
        }
        val watchdog = Runnable {
            finishOnce(false, "Таймаут: нода могла перезагрузиться после сброса NodeDB")
        }
        mainHandler.postDelayed(watchdog, 12_000L)
        MeshGattToRadioWriter(context.applicationContext).writeToradio(deviceAddress, payload) { ok, err ->
            mainHandler.post {
                mainHandler.removeCallbacks(watchdog)
                finishOnce(ok, err)
            }
        }
    }

    fun sendAdminSetIgnored(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        ignoredNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminSetIgnoredNode(ignoredNodeNum, deviceParams(localRadioNodeNum))
        send(context, deviceAddress, payload, onDone)
    }

    fun sendAdminRemoveIgnored(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        ignoredNodeNum: UInt,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminRemoveIgnoredNode(ignoredNodeNum, deviceParams(localRadioNodeNum))
        send(context, deviceAddress, payload, onDone)
    }

    /** Как в типичном mesh-клиенте Android: перезагрузка привязанной ноды через [AdminMessage.reboot_seconds]. */
    fun sendAdminReboot(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        delaySeconds: Int = 5,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminRebootSeconds(delaySeconds, deviceParams(localRadioNodeNum))
        MeshGattToRadioWriter(context.applicationContext).writeToradio(deviceAddress, payload) { ok, err ->
            mainHandler.post { onDone(ok, err, null) }
        }
    }

    fun sendAdminShutdown(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        delaySeconds: Int = 5,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminShutdownSeconds(delaySeconds, deviceParams(localRadioNodeNum))
        MeshGattToRadioWriter(context.applicationContext).writeToradio(deviceAddress, payload) { ok, err ->
            mainHandler.post { onDone(ok, err, null) }
        }
    }

    fun sendAdminFactoryResetConfig(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        arg: Int = 0,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminFactoryResetConfig(arg, deviceParams(localRadioNodeNum))
        MeshGattToRadioWriter(context.applicationContext).writeToradio(deviceAddress, payload) { ok, err ->
            mainHandler.post { onDone(ok, err, null) }
        }
    }

    fun sendAdminFactoryResetDevice(
        context: Context,
        deviceAddress: String,
        localRadioNodeNum: UInt,
        arg: Int = 0,
        onDone: (ok: Boolean, err: String?, meshSummary: String?) -> Unit,
    ) {
        val payload = MeshWireLoRaToRadioEncoder.encodeAdminFactoryResetDevice(arg, deviceParams(localRadioNodeNum))
        MeshGattToRadioWriter(context.applicationContext).writeToradio(deviceAddress, payload) { ok, err ->
            mainHandler.post { onDone(ok, err, null) }
        }
    }
}
