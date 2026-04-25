package com.example.aura.mesh.incoming

import com.example.aura.bluetooth.MeshChannelMessaging
import com.example.aura.bluetooth.MeshImageChunkCodec
import com.example.aura.chat.ChatMessageDeliveryStatus
import com.example.aura.data.local.ChannelChatMessageDao
import com.example.aura.data.local.ChannelChatMessageEntity
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireReadReceiptCodec
import com.example.aura.meshwire.ParsedMeshDataPayload
import com.example.aura.history.MessageHistoryRecorder
import com.example.aura.mesh.repository.MessageRepository
/**
 * Сохранение входящего текстового сообщения канала (portnum 1/7), без tapback и без чанков изображений.
 * Возвращает сущность с [ChannelChatMessageEntity.id] после вставки или null, если сообщение пропущено.
 */
suspend fun persistInboundChannelTextIfNew(
    dao: ChannelChatMessageDao,
    messageRepository: MessageRepository,
    deviceMacNorm: String,
    localNodeNum: UInt?,
    p: ParsedMeshDataPayload,
): ChannelChatMessageEntity? {
    if (!MeshChannelMessaging.isLikelyChannelMeshTraffic(p)) return null
    val channelIndex = (p.channel ?: 0u).toInt()
    if (messageRepository.isDuplicateInboundPacket(deviceMacNorm, channelIndex, p.packetId)) return null

    val from = p.logicalFrom() ?: return null
    val rawWithMarker = MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload) ?: return null
    if (MeshWireFromRadioMeshPacketParser.isTapbackPayload(p)) return null
    val (raw, _) = com.example.aura.vip.VipWireMarker.parseAndStrip(rawWithMarker)
    if (localNodeNum != null && from == localNodeNum && !raw.startsWith(MeshImageChunkCodec.PREFIX)) {
        return null
    }
    if (raw.startsWith(MeshImageChunkCodec.PREFIX)) return null

    // Канальная квитанция «Прочитано»: тело == MeshWireReadReceiptCodec.READ_RECEIPT_WIRE_BODY,
    // id прочитанного пакета — в Data.reply_id. У исходящей строки (канал, dmPeerNodeNum IS NULL,
    // совпал meshPacketId) переводим в READ_IN_PEER_APP; пузырь в ленту не пишем.
    val readTargetId = MeshWireReadReceiptCodec.parseReadReceiptTargetPacketId(
        raw,
        p.dataReplyId,
    )
    if (readTargetId != null) {
        dao.markChannelOutgoingReadByMeshPacketId(
            deviceMacNorm,
            channelIndex,
            readTargetId and 0xFFFF_FFFFL,
            ChatMessageDeliveryStatus.READ_IN_PEER_APP.code,
        )
        return null
    }
    if (raw.trim() == MeshWireReadReceiptCodec.READ_RECEIPT_WIRE_BODY) {
        return null
    }

    val pid = p.packetId
    val dedup = buildString {
        append("in_")
        append(from.toLong() and 0xFFFF_FFFFL)
        append("_")
        append(channelIndex)
        append("_")
        if (pid != null) append(pid.toLong() and 0xFFFF_FFFFL)
        else append(raw.hashCode())
    }
    if (dao.getByDedup(deviceMacNorm, channelIndex, dedup) != null) return null

    val relay = relayHops(p)
    val replyWireId = p.dataReplyId
    val replyTarget = replyWireId?.let { rid ->
        dao.getByMeshPacketIdChannelOnly(deviceMacNorm, channelIndex, rid.toLong() and 0xFFFF_FFFFL)
    }
    val replyPreview = replyTarget?.text?.let { meshReplyPreviewSnippet(it) }
    val replyFromStored = replyTarget?.fromNodeNum
    val entity = ChannelChatMessageEntity(
        deviceMac = deviceMacNorm,
        channelIndex = channelIndex,
        dedupKey = dedup,
        isOutgoing = false,
        text = raw,
        fromNodeNum = from.toLong() and 0xFFFF_FFFFL,
        toNodeNum = 0xFFFFFFFFL,
        meshPacketId = pid?.let { it.toLong() and 0xFFFF_FFFFL },
        deliveryStatus = ChatMessageDeliveryStatus.SENT_TO_NODE.code,
        createdAtMs = System.currentTimeMillis(),
        rxTimeSec = p.rxTimeSec?.toLong(),
        rxSnr = p.rxSnr,
        rxRssi = p.rxRssi,
        relayHops = relay,
        viaMqtt = p.viaMqtt,
        lastError = null,
        replyToPacketId = replyWireId?.let { it.toLong() and 0xFFFF_FFFFL },
        replyToFromNodeNum = if (replyWireId != null) replyFromStored else null,
        replyPreviewText = if (replyWireId != null) replyPreview else null,
        isRead = false,
    )
    val rowId = dao.insert(entity)
    if (rowId < 0) return null
    val inserted = entity.copy(id = rowId)
    MessageHistoryRecorder.repository?.recordTextMessage(deviceMacNorm, channelIndex, inserted)
    return inserted
}

private fun meshReplyPreviewSnippet(body: String): String {
    val oneLine = body.replace("\r\n", "\n").replace("\n", " ").trim()
    return if (oneLine.length > 200) oneLine.take(197) + "…" else oneLine
}

private fun relayHops(p: ParsedMeshDataPayload): Int? {
    val hs = p.hopStart ?: return null
    val hl = p.hopLimit ?: return null
    val d = hs.toInt() - hl.toInt()
    return if (d >= 0) d else null
}
