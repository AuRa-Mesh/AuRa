package com.example.aura.data.local

import androidx.room.Entity

@Entity(
    tableName = "hof_counters",
    primaryKeys = ["ownerNodeKey", "statKey"],
)
data class HallOfFameCounterEntity(
    val ownerNodeKey: String,
    val statKey: String,
    val value: Long,
)
