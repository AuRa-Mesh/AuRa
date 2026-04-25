package com.example.aura.mesh.protobuf

import com.example.aura.mesh.PacketDispatcher
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.ParsedMeshDataPayload

/**
 * Фасад разбора wire-формата protobuf FromRadio / MeshPacket.
 *
 * Каждое чтение характеристики [FromRadio] в типичном mesh-клиенте обычно отдаёт **один** полный верхнеуровневый
 * кадр protobuf; несколько полей `packet` могут приходить в одном кадре — [extractAllFromFrame] разбирает все.
 * Склейка фрагментов на уровне ATT не реализована: при необходимости накапливайте байты до успешного
 * [parseFromRadioFrame] или расширяйте [com.example.aura.meshwire.MeshWireStreamFrameCodec].
 */
object MeshProtobufParser {

    fun extractAllFromFrame(fromRadioBytes: ByteArray): List<ParsedMeshDataPayload> =
        MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(fromRadioBytes)

    /**
     * Порядок обработки как в [PacketDispatcher]: сначала текст чата и сжатый текст, затем ROUTING, остальное.
     */
    fun prioritizeForUi(payloads: List<ParsedMeshDataPayload>): List<ParsedMeshDataPayload> =
        PacketDispatcher.prioritizeMeshPayloads(payloads)
}
