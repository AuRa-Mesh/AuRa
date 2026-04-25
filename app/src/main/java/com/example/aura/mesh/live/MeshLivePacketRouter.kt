package com.example.aura.mesh.live

import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.ParsedMeshDataPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Наблюдатель входящих пакетов после завершения синхронизации (фаза ONLINE).
 * Текст чата по-прежнему обрабатывается в [com.example.aura.bluetooth.ChannelChatManager];
 * здесь — побочные каналы для карты и телеметрии без блокировки UI.
 */
object MeshLivePacketRouter {

    private val _lastPositionRevision = MutableStateFlow(0)
    val lastPositionRevision: StateFlow<Int> = _lastPositionRevision.asStateFlow()

    private val _lastTelemetryRevision = MutableStateFlow(0)
    val lastTelemetryRevision: StateFlow<Int> = _lastTelemetryRevision.asStateFlow()

    fun onInboundPayloads(payloads: List<ParsedMeshDataPayload>) {
        for (p in payloads) {
            when (p.portnum) {
                MeshWireLoRaToRadioEncoder.PORTNUM_POSITION_APP ->
                    _lastPositionRevision.update { it + 1 }
                MeshWireFromRadioMeshPacketParser.PORTNUM_TELEMETRY ->
                    _lastTelemetryRevision.update { it + 1 }
            }
        }
    }
}
