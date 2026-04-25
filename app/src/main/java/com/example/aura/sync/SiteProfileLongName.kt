package com.example.aura.sync

import android.content.Context
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.security.NodeAuthStore

/**
 * LongName с ноды для поля [longName] в JSON профиля на сайт: сначала кэш [MeshNodeSyncMemoryStore],
 * затем снимок списка узлов [MeshNodeListDiskCache] по тому же MAC, если в памяти нет [User],
 * затем глобальный снимок user на диске (без привязки к MAC) — для QR/синка с сайтом.
 */
object SiteProfileLongName {
    fun resolve(context: Context): String? {
        val appCtx = context.applicationContext
        MeshNodeSyncMemoryStore.init(appCtx)

        val nodeIdStr = NodeAuthStore.loadNodeIdForPrefetch(appCtx).trim().takeIf { it.isNotEmpty() }
        val myNum = nodeIdStr?.let { MeshWireNodeNum.parseToUInt(it) }
        val wantNum = myNum?.toLong()?.and(0xFFFF_FFFFL)

        val macCandidates = listOf(
            NodeAuthStore.load(appCtx)?.deviceAddress,
            NodeAuthStore.loadDeviceAddressForPrefetch(appCtx),
            NodeAuthStore.peekBleMacAfterUserDisconnect(appCtx),
        ).mapNotNull { it?.trim()?.takeIf { a -> a.isNotEmpty() } }
            .distinctBy { MeshNodeSyncMemoryStore.normalizeKey(it) }

        if (wantNum != null) {
            for (addr in macCandidates) {
                MeshNodeSyncMemoryStore.warmFromDisk(addr)
                MeshNodeSyncMemoryStore.getUser(addr)?.longName?.trim()
                    ?.takeIf { it.isNotEmpty() }?.let { return it }
                longNameFromNodeListDisk(appCtx, addr, wantNum)?.let { return it }
            }
        }

        MeshNodeSyncMemoryStore.readSiteProfileLongNameFromGlobalDiskCache()?.let { return it }
        return null
    }

    private fun longNameFromNodeListDisk(
        context: Context,
        rawMac: String,
        myNodeNumMask: Long,
    ): String? {
        val nodes = MeshNodeListDiskCache.load(context, rawMac) ?: return null
        val self = nodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == myNodeNumMask } ?: return null
        val n = self.longName.trim()
        if (n.isEmpty() || n.equals(self.nodeIdHex, ignoreCase = true)) return null
        return n
    }
}
