package com.example.aura.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Группа переписки (канал на конкретной ноде): [groupId] = стабильный ключ.
 */
@Entity(
    tableName = "message_history_groups",
    indices = [Index(value = ["deviceMac", "channelIndex"], unique = true)],
)
data class MessageHistoryGroupEntity(
    @PrimaryKey val groupId: String,
    val deviceMac: String,
    val channelIndex: Int,
    /** Для сортировки списка групп. */
    val lastMessageAtMs: Long,
    /** Опциональное отображаемое имя (кэш). */
    val title: String? = null,
)
