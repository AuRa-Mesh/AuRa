package com.example.aura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelChatMessageDao {

    @Query(
        """
        SELECT * FROM channel_chat_messages 
        WHERE deviceMac = :mac AND channelIndex = :channelIndex AND dmPeerNodeNum IS NULL
        ORDER BY createdAtMs ASC, id ASC
        """,
    )
    fun observe(mac: String, channelIndex: Int): Flow<List<ChannelChatMessageEntity>>

    /**
     * Все сообщения личного диалога с [peerNodeNum] по [mac], на любом LoRa-индексе канала:
     * исходящие часто пишутся с primary (0), входящие — с тем индексом, что в пакете на эфире.
     */
    @Query(
        """
        SELECT * FROM channel_chat_messages 
        WHERE deviceMac = :mac AND dmPeerNodeNum = :peerNodeNum
        ORDER BY createdAtMs ASC, id ASC
        """,
    )
    fun observeDirectThread(mac: String, peerNodeNum: Long): Flow<List<ChannelChatMessageEntity>>

    /**
     * Список личных диалогов с устройством [mac]: свежие сверху, превью текста **последнего** сообщения (и входящие, и исходящие), счётчик непрочитанных **входящих**.
     */
    @Query(
        """
        SELECT 
          c1.dmPeerNodeNum AS dmPeerNodeNum,
          MAX(c1.createdAtMs) AS lastMsgAt,
          (SELECT c2.text FROM channel_chat_messages AS c2 
           WHERE c2.deviceMac = :mac AND c2.dmPeerNodeNum IS NOT NULL 
             AND c2.dmPeerNodeNum = c1.dmPeerNodeNum 
             AND NOT (c2.isOutgoing = 1 AND TRIM(c2.text) = 'Прочитано' AND c2.replyToPacketId IS NOT NULL)
           ORDER BY c2.createdAtMs DESC, c2.id DESC LIMIT 1) AS lastPreview,
          SUM(CASE WHEN c1.isOutgoing = 0 AND c1.isRead = 0 THEN 1 ELSE 0 END) AS unreadCount
        FROM channel_chat_messages AS c1
        WHERE c1.deviceMac = :mac AND c1.dmPeerNodeNum IS NOT NULL
          AND NOT (c1.isOutgoing = 1 AND TRIM(c1.text) = 'Прочитано' AND c1.replyToPacketId IS NOT NULL)
        GROUP BY c1.dmPeerNodeNum
        ORDER BY MAX(c1.createdAtMs) DESC
        """,
    )
    fun observeDmThreadSummaries(mac: String): Flow<List<DmThreadSummaryRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ChannelChatMessageEntity): Long

    @Query(
        """
        UPDATE channel_chat_messages 
        SET deliveryStatus = :status, lastError = :lastError 
        WHERE id = :id
        """,
    )
    suspend fun updateDelivery(id: Long, status: Int, lastError: String?)

    @Query(
        """
        UPDATE channel_chat_messages 
        SET meshPacketId = :packetId 
        WHERE id = :id
        """,
    )
    suspend fun updateMeshPacketId(id: Long, packetId: Long)

    @Query(
        """
        SELECT * FROM channel_chat_messages 
        WHERE deviceMac = :mac AND channelIndex = :channelIndex AND dedupKey = :dedupKey LIMIT 1
        """,
    )
    suspend fun getByDedup(mac: String, channelIndex: Int, dedupKey: String): ChannelChatMessageEntity?

    @Query(
        """
        SELECT * FROM channel_chat_messages 
        WHERE id = :id AND deviceMac = :mac AND channelIndex = :channelIndex 
        LIMIT 1
        """,
    )
    suspend fun getById(mac: String, channelIndex: Int, id: Long): ChannelChatMessageEntity?

    @Query(
        """
        SELECT * FROM channel_chat_messages 
        WHERE deviceMac = :mac AND channelIndex = :channelIndex AND meshPacketId = :packetId 
          AND dmPeerNodeNum IS NULL
        LIMIT 1
        """,
    )
    suspend fun getByMeshPacketIdChannelOnly(mac: String, channelIndex: Int, packetId: Long): ChannelChatMessageEntity?

    @Query(
        """
        SELECT * FROM channel_chat_messages 
        WHERE deviceMac = :mac AND dmPeerNodeNum = :peerNodeNum
          AND meshPacketId = :packetId
        LIMIT 1
        """,
    )
    suspend fun getByMeshPacketIdDirectThread(
        mac: String,
        peerNodeNum: Long,
        packetId: Long,
    ): ChannelChatMessageEntity?

    /** Любой тред лички с этим mesh id (для входящих реакций по packet id). */
    @Query(
        """
        SELECT * FROM channel_chat_messages
        WHERE deviceMac = :mac AND dmPeerNodeNum IS NOT NULL AND meshPacketId = :packetId
        LIMIT 1
        """,
    )
    suspend fun getByMeshPacketIdAnyDirectThread(mac: String, packetId: Long): ChannelChatMessageEntity?

    @Query(
        """
        SELECT * FROM channel_chat_messages 
        WHERE id = :id AND deviceMac = :mac AND dmPeerNodeNum = :peerNodeNum
        LIMIT 1
        """,
    )
    suspend fun getByIdDirectThread(mac: String, id: Long, peerNodeNum: Long): ChannelChatMessageEntity?

    @Query(
        """
        UPDATE channel_chat_messages 
        SET meshPacketId = NULL 
        WHERE id = :id AND deviceMac = :mac AND dmPeerNodeNum = :peerNodeNum
        """,
    )
    suspend fun clearMeshPacketIdForDirectThreadRow(mac: String, id: Long, peerNodeNum: Long)

    @Query(
        """
        DELETE FROM channel_chat_messages
        WHERE deviceMac = :mac AND id = :id AND dmPeerNodeNum = :peerNodeNum
        """,
    )
    suspend fun deleteByIdDirectThread(mac: String, id: Long, peerNodeNum: Long)

    @Query(
        """
        UPDATE channel_chat_messages 
        SET meshPacketId = NULL 
        WHERE id = :id AND deviceMac = :mac AND channelIndex = :channelIndex
        """,
    )
    suspend fun clearMeshPacketIdForRow(mac: String, channelIndex: Int, id: Long)

    @Query(
        """
        DELETE FROM channel_chat_messages 
        WHERE deviceMac = :mac AND channelIndex = :channelIndex AND dmPeerNodeNum IS NULL
        """,
    )
    suspend fun deleteAllForChannel(mac: String, channelIndex: Int)

    /** Последнее текстовое сообщение в канале (входящие и исходящие) — превью в списке. */
    @Query(
        """
        SELECT * FROM channel_chat_messages
        WHERE deviceMac = :mac AND channelIndex = :channelIndex AND dmPeerNodeNum IS NULL
          AND NOT (isOutgoing = 1 AND TRIM(text) = 'Прочитано' AND replyToPacketId IS NOT NULL)
        ORDER BY createdAtMs DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeLastMessage(mac: String, channelIndex: Int): Flow<ChannelChatMessageEntity?>

    @Query(
        """
        SELECT COUNT(*) FROM channel_chat_messages
        WHERE deviceMac = :mac AND channelIndex = :channelIndex
          AND isOutgoing = 0
          AND isRead = 0
          AND dmPeerNodeNum IS NULL
        """,
    )
    fun observeUnreadCount(mac: String, channelIndex: Int): Flow<Int>

    @Query(
        """
        UPDATE channel_chat_messages
        SET isRead = 1
        WHERE deviceMac = :mac AND channelIndex = :channelIndex
          AND isOutgoing = 0 AND isRead = 0
          AND dmPeerNodeNum IS NULL
        """,
    )
    suspend fun markIncomingReadForChannel(mac: String, channelIndex: Int)

    @Query(
        """
        UPDATE channel_chat_messages
        SET isRead = 1
        WHERE deviceMac = :mac AND dmPeerNodeNum = :peerNodeNum
          AND isOutgoing = 0 AND isRead = 0
        """,
    )
    suspend fun markIncomingReadForDirectThread(mac: String, peerNodeNum: Long)

    @Query(
        """
        SELECT * FROM channel_chat_messages
        WHERE deviceMac = :mac AND channelIndex = :channelIndex AND dmPeerNodeNum IS NULL
        ORDER BY createdAtMs ASC, id ASC
        """,
    )
    suspend fun getAllForChannelAsc(mac: String, channelIndex: Int): List<ChannelChatMessageEntity>

    @Query(
        """
        SELECT * FROM channel_chat_messages
        WHERE deviceMac = :mac AND dmPeerNodeNum = :peerNodeNum
        ORDER BY createdAtMs ASC, id ASC
        """,
    )
    suspend fun getAllForDirectPeerAsc(mac: String, peerNodeNum: Long): List<ChannelChatMessageEntity>

    @Query(
        """
        DELETE FROM channel_chat_messages
        WHERE deviceMac = :mac AND dmPeerNodeNum = :peerNodeNum
        """,
    )
    suspend fun deleteAllForDirectPeer(mac: String, peerNodeNum: Long)

    /**
     * Квитанция «Прочитано» (и mesh id) в личке: исходящий к [peerNodeNum] с [meshPacketId] → [READ_IN_PEER_APP].
     */
    @Query(
        """
        UPDATE channel_chat_messages
        SET deliveryStatus = :readStatus, lastError = NULL
        WHERE deviceMac = :mac
          AND isOutgoing = 1
          AND dmPeerNodeNum = :peerNodeNum
          AND meshPacketId = :meshPacketId
          AND deliveryStatus != :readStatus
        """,
    )
    suspend fun markDirectOutgoingReadByMeshPacketId(
        mac: String,
        peerNodeNum: Long,
        meshPacketId: Long,
        readStatus: Int,
    ): Int

    /**
     * Квитанция «Прочитано» в канале: любой участник Aura-Mesh прислал «Прочитано» с
     * Data.reply_id = [meshPacketId] нашего исходящего в канал [channelIndex] → [READ_IN_PEER_APP].
     * Порог «хотя бы один прочитал» — первое пришедшее подтверждение переводит в синие галочки,
     * повторные квитанции no-op (см. `deliveryStatus != :readStatus`).
     */
    @Query(
        """
        UPDATE channel_chat_messages
        SET deliveryStatus = :readStatus, lastError = NULL
        WHERE deviceMac = :mac
          AND isOutgoing = 1
          AND channelIndex = :channelIndex
          AND dmPeerNodeNum IS NULL
          AND meshPacketId = :meshPacketId
          AND deliveryStatus != :readStatus
        """,
    )
    suspend fun markChannelOutgoingReadByMeshPacketId(
        mac: String,
        channelIndex: Int,
        meshPacketId: Long,
        readStatus: Int,
    ): Int

    @Query(
        """
        UPDATE channel_chat_messages
        SET reactionsJson = :json
        WHERE id = :id
        """,
    )
    suspend fun updateReactionsJson(id: Long, json: String)

    @Query(
        """
        DELETE FROM channel_chat_messages
        WHERE deviceMac = :mac AND channelIndex = :channelIndex AND id = :id
        """,
    )
    suspend fun deleteById(mac: String, channelIndex: Int, id: Long)
}
