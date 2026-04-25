package com.example.aura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageHistoryDao {

    /**
     * Не REPLACE: иначе SQLite удаляет строку группы и срабатывает CASCADE по сообщениям —
     * в истории остаётся только последняя запись.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupIgnore(group: MessageHistoryGroupEntity): Long

    @Query(
        """
        UPDATE message_history_groups SET
            lastMessageAtMs = :lastMessageAtMs,
            title = CASE
                WHEN :title IS NOT NULL AND length(trim(:title)) > 0 THEN trim(:title)
                ELSE title
            END
        WHERE groupId = :groupId
        """,
    )
    suspend fun updateGroupTimestampAndTitle(
        groupId: String,
        lastMessageAtMs: Long,
        title: String?,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(entity: MessageHistoryEntryEntity): Long

    @Query("SELECT * FROM message_history_groups ORDER BY lastMessageAtMs DESC")
    fun observeGroups(): Flow<List<MessageHistoryGroupEntity>>

    @Query("SELECT * FROM message_history_groups ORDER BY lastMessageAtMs DESC")
    suspend fun getGroupsSnapshot(): List<MessageHistoryGroupEntity>

    @Query(
        """
        SELECT * FROM message_history_entries
        WHERE groupId = :groupId
        ORDER BY createdAtEpochMs ASC, id ASC
        """,
    )
    fun observeMessagesForGroup(groupId: String): Flow<List<MessageHistoryEntryEntity>>

    @Query(
        """
        SELECT * FROM message_history_entries
        WHERE groupId = :groupId
        ORDER BY createdAtEpochMs ASC, id ASC
        """,
    )
    suspend fun getMessagesForGroup(groupId: String): List<MessageHistoryEntryEntity>

    @Query("SELECT * FROM message_history_entries ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun getAllMessages(): List<MessageHistoryEntryEntity>

    @Query("DELETE FROM message_history_entries WHERE groupId = :groupId")
    suspend fun deleteMessagesInGroup(groupId: String)

    @Query("DELETE FROM message_history_groups WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM message_history_entries")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM message_history_groups")
    suspend fun deleteAllGroups()

    @Transaction
    suspend fun clearAllHistory() {
        deleteAllMessages()
        deleteAllGroups()
    }
}
