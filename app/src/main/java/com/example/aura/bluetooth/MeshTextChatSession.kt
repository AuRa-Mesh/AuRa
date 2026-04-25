package com.example.aura.bluetooth

import com.example.aura.chat.ChannelImageAttachment
import com.example.aura.chat.ChannelVoiceAttachment
import com.example.aura.data.local.ChannelChatMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Общий контракт для экрана переписки: канал или личное сообщение (DM).
 */
interface MeshTextChatSession {
    val messages: Flow<List<ChannelChatMessageEntity>>
    val imageAttachments: StateFlow<List<ChannelImageAttachment>>
    val voiceAttachments: StateFlow<List<ChannelVoiceAttachment>>

    fun start()
    fun stop()
    fun clearLocalChatUiAfterHistoryClear()

    /**
     * Текст в т.ч. в формате срочного (⚡URGENT / ⚡ACK) — поддерживается и [ChannelChatManager],
     * и [DirectChatManager] (один и тот же путь, что в каналах, что в личке).
     */
    fun sendMessage(
        text: String,
        destId: Long = 0xFFFFFFFFL,
        replyToPacketId: UInt? = null,
        replyToFromNodeNum: UInt? = null,
        replyPreviewText: String? = null,
    )

    fun sendReaction(messageId: Long, emojiIndex: Int)
    fun sendReactionUpdate(messageId: Long, emojiIndex: Int, isAdding: Boolean)
    fun retryFailedOutgoingText(rowId: Long)
    suspend fun retryFailedVoice(stableId: String): Pair<Boolean, String?>
    suspend fun sendImageLines(lines: List<String>): Pair<Boolean, String?>
    fun appendLocalImageAttachment(jpeg: ByteArray, sessionKey: String)
    suspend fun sendVoicePayload(encoded: ByteArray, durationMs: Long): Pair<Boolean, String?>
    suspend fun removeTextMessageFromChatPreservingHistory(rowId: Long)
    suspend fun removeImageAttachmentFromChat(stableId: String)
    suspend fun removeVoiceAttachmentFromChat(stableId: String)
}
