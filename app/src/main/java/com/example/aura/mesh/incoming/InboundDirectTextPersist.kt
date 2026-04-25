package com.example.aura.mesh.incoming

import android.content.Context
import com.example.aura.bluetooth.MeshChannelMessaging
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.bluetooth.MeshImageChunkCodec
import com.example.aura.chat.ChatMessageDeliveryStatus
import com.example.aura.data.local.ChannelChatMessageDao
import com.example.aura.data.local.ChannelChatMessageEntity
import com.example.aura.meshwire.MeshWireReadReceiptCodec
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.ParsedMeshDataPayload
import com.example.aura.mesh.repository.MessageRepository
/**
 * Входящее личное текстовое сообщение: [ParsedMeshDataPayload.to] = наш nodenum, не широковещание.
 */
suspend fun persistInboundDirectTextIfNew(
    dao: ChannelChatMessageDao,
    messageRepository: MessageRepository,
    deviceMacNorm: String,
    appContext: Context,
    localNodeNum: UInt?,
    p: ParsedMeshDataPayload,
): ChannelChatMessageEntity? {
    val mine = localNodeNum ?: return null
    val to = p.to ?: return null
    if (to != mine) return null
    if (MeshChannelMessaging.isLikelyChannelMeshTraffic(p)) return null

    val fromWire = p.logicalFrom() ?: return null
    if (fromWire == mine) return null
    val from = resolveInboundDmPeerUInt(deviceMacNorm, appContext, mine, p).takeIf { it != 0u } ?: fromWire
    if (from == mine) return null
    val peerLong = from.toLong() and 0xFFFF_FFFFL
    if (MeshNodeListDiskCache.isPeerIgnoredInCache(appContext, deviceMacNorm, peerLong)) return null

    val channelIndex = (p.channel ?: 0u).toInt()
    if (messageRepository.isDuplicateInboundDirectPacket(deviceMacNorm, from, p.packetId)) {
        return null
    }

    val rawWithMarker = MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload) ?: return null
    if (MeshWireFromRadioMeshPacketParser.isTapbackPayload(p)) return null
    val (raw, _) = com.example.aura.vip.VipWireMarker.parseAndStrip(rawWithMarker)
    if (raw.startsWith(MeshImageChunkCodec.PREFIX)) return null

    val readTargetId = MeshWireReadReceiptCodec.parseReadReceiptTargetPacketId(
        raw,
        p.dataReplyId,
    )
    if (readTargetId != null) {
        dao.markDirectOutgoingReadByMeshPacketId(
            deviceMacNorm,
            peerLong,
            readTargetId and 0xFFFF_FFFFL,
            ChatMessageDeliveryStatus.READ_IN_PEER_APP.code,
        )
        // n == 0: нет исходящей строки с этим mesh id (старый id/миграция) — тихо игнорируем.
        return null
    }
    // «Прочитано» без dataReplyId в пакете (нода/эфир) — квитанция, пузырь в чат не пишем.
    if (raw.trim() == MeshWireReadReceiptCodec.READ_RECEIPT_WIRE_BODY) {
        return null
    }

    val pid = p.packetId
    val dedup = buildString {
        append("dm_in_")
        append(peerLong)
        append("_")
        append(channelIndex)
        append("_")
        if (pid != null) append(pid.toLong() and 0xFFFF_FFFFL)
        else append(raw.hashCode())
    }
    if (dao.getByDedup(deviceMacNorm, channelIndex, dedup) != null) return null

    val relay = relayHopsDm(p)
    val replyWireId = p.dataReplyId
    val replyTarget = replyWireId?.let { rid ->
        dao.getByMeshPacketIdDirectThread(
            deviceMacNorm,
            peerLong,
            rid.toLong() and 0xFFFF_FFFFL,
        )
    }
    val replyPreview = replyTarget?.text?.let { meshReplyPreviewSnippetDm(it) }
    val replyFromStored = replyTarget?.fromNodeNum
    val toLong = mine.toLong() and 0xFFFF_FFFFL
    val entity = ChannelChatMessageEntity(
        deviceMac = deviceMacNorm,
        channelIndex = channelIndex,
        dmPeerNodeNum = peerLong,
        dedupKey = dedup,
        isOutgoing = false,
        text = raw,
        fromNodeNum = peerLong,
        toNodeNum = toLong,
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
    return entity.copy(id = rowId)
}

private fun meshReplyPreviewSnippetDm(body: String): String {
    val oneLine = body.replace("\r\n", "\n").replace("\n", " ").trim()
    return if (oneLine.length > 200) oneLine.take(197) + "…" else oneLine
}

private fun relayHopsDm(p: ParsedMeshDataPayload): Int? {
    val hs = p.hopStart ?: return null
    val hl = p.hopLimit ?: return null
    val d = hs.toInt() - hl.toInt()
    return if (d >= 0) d else null
}
