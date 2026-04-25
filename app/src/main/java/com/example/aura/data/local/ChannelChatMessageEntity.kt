package com.example.aura.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сообщение канала (входящее/исходящее) для [ChannelChatManager].
 *
 * Шифрование на LoRa: выполняется прошивкой по PSK канала; клиент отдаёт в ToRadio уже decoded Data.
 */
@Entity(
    tableName = "channel_chat_messages",
    indices = [
        Index(value = ["deviceMac", "channelIndex", "dedupKey"], unique = true),
        Index(value = ["deviceMac", "channelIndex", "createdAtMs"]),
        Index(value = ["deviceMac", "channelIndex", "dmPeerNodeNum", "createdAtMs"]),
    ],
)
data class ChannelChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceMac: String,
    val channelIndex: Int,
    /**
     * Личная переписка с узлом [dmPeerNodeNum] (nodenum собеседника); null — сообщение общего канала.
     * Индекс LoRa-канала по-прежнему в [channelIndex] (обычно 0 = primary).
     */
    val dmPeerNodeNum: Long? = null,
    /** Уникальный ключ: для входящих стабильный от пакета, для исходящих uuid. */
    val dedupKey: String,
    val isOutgoing: Boolean,
    val text: String,
    val fromNodeNum: Long,
    /** 0xFFFFFFFF_FFFFFFFFL как -1 при сериализации для широковещания. */
    val toNodeNum: Long,
    /** MeshPacket.id (uint32) для отслеживания ROUTING / ACK. */
    val meshPacketId: Long?,
    /** [ChatMessageDeliveryStatus]. */
    val deliveryStatus: Int,
    val createdAtMs: Long,
    val rxTimeSec: Long?,
    val rxSnr: Float?,
    val rxRssi: Int?,
    val relayHops: Int?,
    val viaMqtt: Boolean,
    val lastError: String?,
    /** Ответ на сообщение (mesh.proto Data.reply_id): id пакета цитируемого сообщения на эфире. */
    val replyToPacketId: Long? = null,
    /** Автор цитируемого сообщения (для превью в UI). */
    val replyToFromNodeNum: Long? = null,
    /** Снимок текста цитаты для отображения без повторного поиска. */
    val replyPreviewText: String? = null,
    /** Входящее: false до открытия чата канала; исходящие — всегда true. */
    val isRead: Boolean = true,
    /** JSON-массив [ReactionItem]; см. [ChatMessageReactionsJson]. */
    val reactionsJson: String = ChatMessageReactionsJson.EMPTY_ARRAY,
)
