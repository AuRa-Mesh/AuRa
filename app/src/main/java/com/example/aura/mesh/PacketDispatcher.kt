package com.example.aura.mesh

import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.ParsedMeshDataPayload
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Приоритетная обработка полезной нагрузки из одного кадра [FromRadio]:
 * сначала текст чата и сжатый текст, затем ROUTING (ACK исходящих), затем остальное
 * (NodeInfo, Telemetry, Position и т.д.), чтобы UI сообщений не ждал разбора тяжёлых пакетов.
 *
 * Порядок **между** отдельными BLE-кадрами задаёт стек GATT; здесь — только reorder внутри кадра.
 */
object PacketDispatcher {

    /**
     * Пока идёт тяжёлая первичная синхронизация (много NodeInfo), всё равно отдаём текст вперёд —
     * флаг можно выставить из UI/синхронизации, чтобы не блокировать отображение чата.
     */
    val initialSyncHeavy: AtomicBoolean = AtomicBoolean(false)

    private val textPorts = setOf(
        MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_APP,
        MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_COMPRESSED_APP,
        MeshWireFromRadioMeshPacketParser.PORTNUM_PRIVATE_APP,
    )
    private val routingPort = MeshWireFromRadioMeshPacketParser.PORTNUM_ROUTING_APP

    fun prioritizeMeshPayloads(payloads: List<ParsedMeshDataPayload>): List<ParsedMeshDataPayload> {
        if (payloads.size <= 1) return payloads
        return payloads.sortedWith(
            compareBy(
                { p ->
                    when (p.portnum) {
                        in textPorts -> 0
                        routingPort -> 1
                        else -> 2
                    }
                },
                { it.packetId?.toLong() ?: 0L },
            ),
        )
    }

    /**
     * Быстрый проход по сырому кадру FromRadio: извлекает portnum из первых декодированных
     * MeshPacket **без** полного разбора полей (для логов / метрик fast path).
     */
    fun peekDecodedPortnums(raw: ByteArray): List<Int> {
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<Int>(4)
        val payloads = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(raw)
        for (p in payloads) out.add(p.portnum)
        return out
    }
}
