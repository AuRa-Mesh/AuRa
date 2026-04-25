package com.example.aura.data.local

import androidx.room.Entity

/**
 * Последний известный «аптайм приложения» другого узла (Aura), полученный по эфиру.
 * Привязка к привязанной ноде [deviceMacNorm] (Bluetooth / TCP / USB).
 */
@Entity(
    tableName = "mesh_node_peer_uptime",
    primaryKeys = ["deviceMacNorm", "nodeNum"],
)
data class MeshNodePeerUptimeEntity(
    val deviceMacNorm: String,
    val nodeNum: Long,
    /** Суммарное время в секундах, как прислал узел. */
    val lastKnownUptimeSec: Long,
    /** Unix сек — когда мы получили пакет. */
    val uptimeTimestampEpochSec: Long,
)
