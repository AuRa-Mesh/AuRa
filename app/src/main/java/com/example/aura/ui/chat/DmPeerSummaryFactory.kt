package com.example.aura.ui.chat

import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.meshwire.MeshWireNodeNum
import java.util.Locale

/**
 * Сводка узла для открытия лички: из NodeDB ([nodes]) или плейсхолдер `!xxxxxxxx` как в Meshtastic.
 */
fun meshtasticPeerSummaryForDm(
    peerNodeNum: Long,
    nodes: List<MeshWireNodeSummary>,
): MeshWireNodeSummary {
    val n = peerNodeNum and 0xFFFF_FFFFL
    nodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == n }?.let { return it }
    val hex = MeshWireNodeNum.formatHex((n and 0xFFFF_FFFFL).toUInt())
        .trim()
        .removePrefix("!")
        .lowercase(Locale.ROOT)
    val idHex = "!$hex"
    return MeshWireNodeSummary(
        nodeNum = n,
        nodeIdHex = idHex,
        longName = idHex,
        shortName = "?",
        hardwareModel = "",
        roleLabel = "CLIENT",
        userId = null,
        lastHeardLabel = "—",
    )
}

fun displayTitleForDmPeer(peerNodeNum: Long, nodes: List<MeshWireNodeSummary>): String =
    meshtasticPeerSummaryForDm(peerNodeNum, nodes).displayLongName()
