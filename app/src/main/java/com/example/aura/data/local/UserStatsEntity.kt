package com.example.aura.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey val ownerNodeKey: String,
    val totalXp: Long,
    val updatedAtMs: Long,
)
