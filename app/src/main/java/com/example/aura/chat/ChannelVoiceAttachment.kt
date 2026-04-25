package com.example.aura.chat

import com.example.aura.data.local.ChatMessageReactionsJson

/**
 * Собранное голосовое (Codec2-байты) для UI; дублируется во внутренние файлы канала и в историю сообщений (Room).
 * [deliveryStatus] — [ChatMessageDeliveryStatus].code для исходящих ([mine]).
 * [meshPacketId] — [MeshPacket.id] последнего фрагмента при отправке / пакета при приёме (для tapback).
 * [voiceRecordId] — id серии фрагментов в эфире; для ⚡ACK подтверждения прослушивания (`voice:…` в канале).
 */
data class ChannelVoiceAttachment(
    val stableId: String,
    val from: UInt?,
    val codecPayload: ByteArray,
    val mine: Boolean,
    val timeMs: Long,
    val durationMs: Long,
    val deliveryStatus: Int = ChatMessageDeliveryStatus.SENT_TO_NODE.code,
    val meshPacketId: UInt? = null,
    val reactionsJson: String = ChatMessageReactionsJson.EMPTY_ARRAY,
    val voiceRecordId: UInt = 0u,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChannelVoiceAttachment) return false
        return stableId == other.stableId &&
            from == other.from &&
            mine == other.mine &&
            timeMs == other.timeMs &&
            durationMs == other.durationMs &&
            deliveryStatus == other.deliveryStatus &&
            meshPacketId == other.meshPacketId &&
            reactionsJson == other.reactionsJson &&
            voiceRecordId == other.voiceRecordId &&
            codecPayload.contentEquals(other.codecPayload)
    }

    override fun hashCode(): Int {
        var r = stableId.hashCode()
        r = 31 * r + (from?.hashCode() ?: 0)
        r = 31 * r + mine.hashCode()
        r = 31 * r + timeMs.hashCode()
        r = 31 * r + durationMs.hashCode()
        r = 31 * r + deliveryStatus
        r = 31 * r + (meshPacketId?.hashCode() ?: 0)
        r = 31 * r + reactionsJson.hashCode()
        r = 31 * r + voiceRecordId.hashCode()
        r = 31 * r + codecPayload.contentHashCode()
        return r
    }
}
