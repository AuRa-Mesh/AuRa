package com.example.aura.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fav_groups")
data class FavGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "fav_group_members",
    primaryKeys = ["groupId", "nodeNum"],
    foreignKeys = [
        ForeignKey(
            entity = FavGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["groupId"])],
)
data class FavGroupMemberEntity(
    val groupId: Long,
    val nodeNum: Long,
)
