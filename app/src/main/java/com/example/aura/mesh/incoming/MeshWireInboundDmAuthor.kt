package com.example.aura.mesh.incoming

import android.content.Context
import android.util.Base64
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.meshwire.ParsedMeshDataPayload

/**
 * Для лички на нас: после PKI прошивка кладёт в [ParsedMeshDataPayload.meshSenderPublicKey] ключ **настоящего** отправителя;
 * [MeshPacket.from] может быть ретранслятором. Сопоставляем ключ с NodeDB (ключ из NodeInfo.user.public_key).
 */
fun resolveInboundDmPeerUInt(
    deviceMacNorm: String,
    appContext: Context,
    localNodeNum: UInt,
    p: ParsedMeshDataPayload,
): UInt {
    val base = p.logicalFrom() ?: return 0u
    val to = p.to ?: return base
    if (to != localNodeNum) return base
    val pk = p.meshSenderPublicKey ?: return base
    if (pk.size != 32) return base
    val nodes = MeshNodeListDiskCache.load(appContext.applicationContext, deviceMacNorm).orEmpty()
    val hits = ArrayList<MeshWireNodeSummary>(2)
    for (n in nodes) {
        val b64 = n.publicKeyB64 ?: continue
        val dec =
            try {
                Base64.decode(b64, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                continue
            }
        if (dec.size == 32 && dec.contentEquals(pk)) hits.add(n)
    }
    return if (hits.size == 1) {
        (hits[0].nodeNum and 0xFFFF_FFFFL).toUInt()
    } else {
        base
    }
}
