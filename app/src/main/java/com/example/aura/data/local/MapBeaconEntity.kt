package com.example.aura.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "map_beacons",
    primaryKeys = ["channelId", "id"],
    indices = [
        Index(value = ["channelIndex"]),
        Index(value = ["timestampCreated"]),
    ],
)
data class MapBeaconEntity(
    val id: Long,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val timestampCreated: Long,
    val creatorNodeNum: Long,
    val creatorLongName: String,
    val channelId: String,
    val channelIndex: Int,
    val ttlMs: Long,
    @ColumnInfo(defaultValue = "#39E7FF")
    val color: String = "#39E7FF",
    /** Метка создана из ссылки/координат в чате (импорт), а не с карты / не только с mesh. */
    @ColumnInfo(defaultValue = "0")
    val fromChatLink: Boolean = false,
    /** Метка с карты с опцией «Установить локально»: только устройство, показ в режиме «Локально». */
    @ColumnInfo(defaultValue = "0")
    val localMapInstall: Boolean = false,
)
