package com.example.aura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MapBeaconDao {

    @Query("SELECT * FROM map_beacons ORDER BY timestampCreated DESC")
    fun observeAll(): Flow<List<MapBeaconEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MapBeaconEntity)

    @Query("DELETE FROM map_beacons WHERE id = :id AND channelIndex = :channelIndex")
    suspend fun removeByIdAndChannelIndex(id: Long, channelIndex: Int): Int

    @Query("DELETE FROM map_beacons WHERE id = :id AND creatorNodeNum = :creatorNodeNum AND (:channelId IS NULL OR channelId = :channelId)")
    suspend fun removeByOwner(id: Long, creatorNodeNum: Long, channelId: String?): Int

    @Query("DELETE FROM map_beacons WHERE (:nowMs - timestampCreated) > ttlMs")
    suspend fun deleteExpired(nowMs: Long): Int

    @Query(
        "SELECT COUNT(*) FROM map_beacons WHERE creatorNodeNum = :creatorNodeNum AND localMapInstall = 0",
    )
    suspend fun countNonLocalByCreator(creatorNodeNum: Long): Int
}
