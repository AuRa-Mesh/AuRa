package com.example.aura.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одно сообщение в истории; привязано к [groupId].
 * Время — [createdAtEpochMs] (для [java.time] см. маппинг во ViewModel).
 */
@Entity(
    tableName = "message_history_entries",
    foreignKeys = [
        ForeignKey(
            entity = MessageHistoryGroupEntity::class,
            parentColumns = ["groupId"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["groupId", "createdAtEpochMs"]),
        Index(value = ["groupId"]),
    ],
)
data class MessageHistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    /** [MessageHistoryType.code] */
    val type: Int,
    val createdAtEpochMs: Long,
    val textBody: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Напр. "delivery", "system". */
    val serviceKind: String? = null,
    val servicePayloadJson: String? = null,
    /** Относительно [android.content.Context.getFilesDir]. */
    val voiceFileRelativePath: String? = null,
    val voiceDurationMs: Long? = null,
    val imageFileRelativePath: String? = null,
    val isOutgoing: Boolean,
    val fromNodeNum: Long = 0L,
    val dedupKey: String? = null,
    val deliveryStatus: Int? = null,
)
