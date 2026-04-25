package com.example.aura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MeshNodePeerUptimeDao {

    @Query("SELECT * FROM mesh_node_peer_uptime WHERE deviceMacNorm = :mac")
    suspend fun getAllForDevice(mac: String): List<MeshNodePeerUptimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MeshNodePeerUptimeEntity)

    @Query("DELETE FROM mesh_node_peer_uptime WHERE deviceMacNorm = :mac")
    suspend fun deleteForDevice(mac: String)

    @Query("DELETE FROM mesh_node_peer_uptime WHERE deviceMacNorm = :mac AND nodeNum = :nodeNum")
    suspend fun deleteForMacAndNodeNum(mac: String, nodeNum: Long)
}
