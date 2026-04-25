package com.example.aura.mesh.repository

import com.example.aura.data.local.ChannelChatMessageDao
import com.example.aura.data.local.ChannelChatMessageEntity
import com.example.aura.data.local.ChatMessageReactionsJson
import com.example.aura.meshwire.MeshReactionEmojiRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * Хранение и дедупликация входящих по [packet_id] и ключу dedup, как в Meshtastic
 * (совпадение с эфиром / экраном ноды без повторной вставки).
 */
class MessageRepository(
    private val dao: ChannelChatMessageDao,
) {
    private val recentInboundPacketIds = ConcurrentHashMap<String, Boolean>()
    private val maxRecentKeys = 4000

    private fun packetKey(mac: String, channelIndex: Int, packetId: UInt): String =
        "$mac|$channelIndex|${packetId.toLong() and 0xFFFF_FFFFL}"

    /** Без индекса LoRa-канала: один пакет DM от узла не должен дублироваться в разных «окнах». */
    private fun directPacketKey(mac: String, peerFrom: UInt, packetId: UInt): String =
        "$mac|dm|${peerFrom.toLong() and 0xFFFF_FFFFL}|${packetId.toLong() and 0xFFFF_FFFFL}"

    /**
     * @return true если этот [packetId] уже обрабатывали (память или БД) — пропускаем вставку.
     */
    suspend fun isDuplicateInboundPacket(
        mac: String,
        channelIndex: Int,
        packetId: UInt?,
    ): Boolean {
        if (packetId == null) return false
        val k = packetKey(mac, channelIndex, packetId)
        if (recentInboundPacketIds.putIfAbsent(k, true) != null) return true
        trimIfNeeded()
        val row = dao.getByMeshPacketIdChannelOnly(
            mac,
            channelIndex,
            packetId.toLong() and 0xFFFF_FFFFL,
        )
        return row != null
    }

    suspend fun isDuplicateInboundDirectPacket(
        mac: String,
        peerFrom: UInt,
        packetId: UInt?,
    ): Boolean {
        if (packetId == null) return false
        val k = directPacketKey(mac, peerFrom, packetId)
        if (recentInboundPacketIds.putIfAbsent(k, true) != null) return true
        trimIfNeeded()
        val peerLong = peerFrom.toLong() and 0xFFFF_FFFFL
        val row = dao.getByMeshPacketIdDirectThread(
            mac,
            peerLong,
            packetId.toLong() and 0xFFFF_FFFFL,
        )
        return row != null
    }

    suspend fun insertIncoming(entity: ChannelChatMessageEntity): Long = dao.insert(entity)

    suspend fun getByDedup(mac: String, channelIndex: Int, dedupKey: String) =
        dao.getByDedup(mac, channelIndex, dedupKey)

    /** Toggle реакции [emoji] от [reactorNodeNum] на сообщение с [targetMeshPacketId]. */
    suspend fun applyReactionTapOrToggle(
        mac: String,
        channelIndex: Int,
        targetMeshPacketId: Long,
        reactorNodeNum: Long,
        emoji: String,
        atMillis: Long,
    ): Boolean {
        val row = dao.getByMeshPacketIdChannelOnly(mac, channelIndex, targetMeshPacketId and 0xFFFF_FFFFL) ?: return false
        val reactorKey = (reactorNodeNum and 0xFFFF_FFFFL).toString()
        val newJson = ChatMessageReactionsJson.applyTapOrToggle(row.reactionsJson, reactorKey, emoji, atMillis)
        if (newJson != row.reactionsJson) {
            dao.updateReactionsJson(row.id, newJson)
        }
        return true
    }

    /**
     * Входящая реакция с компактным wire-ID (1 байт в payload tapback).
     * Логический тип «REACTION» в Meshtastic — это TEXT tapback с [MeshReactionEmojiRegistry].
     */
    suspend fun applyReactionFromWireEmojiId(
        mac: String,
        channelIndex: Int,
        targetMeshPacketId: Long,
        reactorNodeNum: Long,
        wireEmojiId: Int,
        atMillis: Long,
    ): Boolean {
        val emoji = MeshReactionEmojiRegistry.emojiForWireId(wireEmojiId) ?: return false
        return applyReactionTapOrToggle(mac, channelIndex, targetMeshPacketId, reactorNodeNum, emoji, atMillis)
    }

    /** Снять у [reactorNodeNum] конкретную реакцию [emoji] (входящий wire remove). */
    suspend fun removeSenderEmojiIfPresent(
        mac: String,
        channelIndex: Int,
        targetMeshPacketId: Long,
        reactorNodeNum: Long,
        emoji: String,
    ): Boolean {
        val row = dao.getByMeshPacketIdChannelOnly(mac, channelIndex, targetMeshPacketId and 0xFFFF_FFFFL) ?: return false
        val reactorKey = (reactorNodeNum and 0xFFFF_FFFFL).toString()
        val newJson = ChatMessageReactionsJson.removeSenderEmojiIfPresent(row.reactionsJson, reactorKey, emoji)
        if (newJson != row.reactionsJson) {
            dao.updateReactionsJson(row.id, newJson)
        }
        return true
    }

    /** Удалить все реакции узла [reactorNodeNum] с этого сообщения (revoke / пустой tapback). */
    suspend fun clearAllReactionsFromSenderOnMessage(
        mac: String,
        channelIndex: Int,
        targetMeshPacketId: Long,
        reactorNodeNum: Long,
    ): Boolean {
        val row = dao.getByMeshPacketIdChannelOnly(mac, channelIndex, targetMeshPacketId and 0xFFFF_FFFFL) ?: return false
        val reactorKey = (reactorNodeNum and 0xFFFF_FFFFL).toString()
        val newJson = ChatMessageReactionsJson.removeAllFromSender(row.reactionsJson, reactorKey)
        if (newJson != row.reactionsJson) {
            dao.updateReactionsJson(row.id, newJson)
        }
        return true
    }

    suspend fun applyReactionTapOrToggleDirect(
        mac: String,
        targetMeshPacketId: Long,
        reactorNodeNum: Long,
        emoji: String,
        atMillis: Long,
    ): Boolean {
        val row = dao.getByMeshPacketIdAnyDirectThread(mac, targetMeshPacketId and 0xFFFF_FFFFL) ?: return false
        val reactorKey = (reactorNodeNum and 0xFFFF_FFFFL).toString()
        val newJson = ChatMessageReactionsJson.applyTapOrToggle(row.reactionsJson, reactorKey, emoji, atMillis)
        if (newJson != row.reactionsJson) {
            dao.updateReactionsJson(row.id, newJson)
        }
        return true
    }

    suspend fun removeSenderEmojiIfPresentDirect(
        mac: String,
        targetMeshPacketId: Long,
        reactorNodeNum: Long,
        emoji: String,
    ): Boolean {
        val row = dao.getByMeshPacketIdAnyDirectThread(mac, targetMeshPacketId and 0xFFFF_FFFFL) ?: return false
        val reactorKey = (reactorNodeNum and 0xFFFF_FFFFL).toString()
        val newJson = ChatMessageReactionsJson.removeSenderEmojiIfPresent(row.reactionsJson, reactorKey, emoji)
        if (newJson != row.reactionsJson) {
            dao.updateReactionsJson(row.id, newJson)
        }
        return true
    }

    suspend fun clearAllReactionsFromSenderOnMessageDirect(
        mac: String,
        targetMeshPacketId: Long,
        reactorNodeNum: Long,
    ): Boolean {
        val row = dao.getByMeshPacketIdAnyDirectThread(mac, targetMeshPacketId and 0xFFFF_FFFFL) ?: return false
        val reactorKey = (reactorNodeNum and 0xFFFF_FFFFL).toString()
        val newJson = ChatMessageReactionsJson.removeAllFromSender(row.reactionsJson, reactorKey)
        if (newJson != row.reactionsJson) {
            dao.updateReactionsJson(row.id, newJson)
        }
        return true
    }

    private fun trimIfNeeded() {
        if (recentInboundPacketIds.size <= maxRecentKeys) return
        recentInboundPacketIds.clear()
    }
}
