package com.example.aura.mesh.nodedb

import com.example.aura.meshwire.MeshWireGeo
import com.example.aura.meshwire.MeshWireNodeSummary

/**
 * Сортировка списка узлов: своя нода всегда первая, далее по убыванию [lastSeenEpochSec]
 * (самые свежие выше). [onlineWindowSec] оставлен для совместимости вызовов.
 */
object MeshNodeListSorter {

    const val DEFAULT_ONLINE_WINDOW_SEC: Long = 15 * 60L

    fun sortForNodesTab(
        list: List<MeshWireNodeSummary>,
        selfNodeNum: Long?,
        nowEpochSec: Long,
        @Suppress("UNUSED_PARAMETER") onlineWindowSec: Long = DEFAULT_ONLINE_WINDOW_SEC,
    ): List<MeshWireNodeSummary> {
        val self = selfNodeNum?.let { it and 0xFFFF_FFFFL }
        fun isSelf(n: MeshWireNodeSummary): Boolean =
            self != null && (n.nodeNum and 0xFFFF_FFFFL) == self

        return list.sortedWith(
            compareByDescending<MeshWireNodeSummary> { isSelf(it) }
                .thenByDescending { it.lastSeenEpochSec ?: 0L },
        )
    }

    fun distanceMetersOrNull(
        self: MeshWireNodeSummary?,
        node: MeshWireNodeSummary,
    ): Double? {
        if (self == null) return null
        val la = self.latitude
        val lo = self.longitude
        val nla = node.latitude
        val nlo = node.longitude
        if (la == null || lo == null || nla == null || nlo == null) return null
        return MeshWireGeo.haversineMeters(la, lo, nla, nlo)
    }
}
