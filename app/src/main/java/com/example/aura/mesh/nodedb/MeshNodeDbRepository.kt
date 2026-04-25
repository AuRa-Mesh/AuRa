package com.example.aura.mesh.nodedb

import android.content.Context
import android.util.Log
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.data.local.MeshNodePeerUptimeEntity
import com.example.aura.data.local.MeshNodePeerUptimeDao
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.meshwire.MeshWireNodeListAccumulator
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * NodeDB: потоковое наполнение из FromRadio + RAM [StateFlow] + диск [MeshNodeListDiskCache].
 */
object MeshNodeDbRepository {

    private const val TAG = "MeshNodeDbRepo"

    private val appContext = AtomicReference<Context?>(null)
    @Volatile
    private var peerUptimeDao: MeshNodePeerUptimeDao? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class PeerUptimeRow(val uptimeSec: Long, val recvEpochSec: Long)

    /** Оверлей по nodeNum для текущего [macNorm] (подмешивается в [publishLocked]). */
    private val peerUptimeOverlay = mutableMapOf<Long, PeerUptimeRow>()

    /** Очередь кадров FromRadio → разбор и publish на [Dispatchers.IO], чтобы не блокировать BLE parse executor. */
    private val ingestChannel = Channel<Pair<String, ByteArray>>(capacity = Channel.UNLIMITED)

    init {
        ioScope.launch(Dispatchers.IO) {
            while (isActive) {
                val (mac, bytes) = ingestChannel.receive()
                ingestFrameOnIo(mac, bytes)
            }
        }
    }

    private val lock = Any()
    private var macNorm: String? = null
    private var acc = MeshWireNodeListAccumulator()

    /** После [onNodeTimeSyncSent] доверяем last_heard из пакетов (RTC ноды выставлен с телефона). */
    fun onNodeTimeSyncSent() {
        synchronized(lock) {
            acc.trustNodeLastHeardTimestamps = true
            publishLocked()
        }
        schedulePersist()
    }

    private val _nodes = MutableStateFlow<List<MeshWireNodeSummary>>(emptyList())
    val nodes: StateFlow<List<MeshWireNodeSummary>> = _nodes.asStateFlow()

    private var persistJob: Job? = null

    fun init(application: Context) {
        appContext.set(application.applicationContext)
    }

    fun attachPeerUptimeDao(dao: MeshNodePeerUptimeDao) {
        peerUptimeDao = dao
    }

    fun attachDevice(rawMac: String?) {
        val ctx = appContext.get() ?: return
        synchronized(lock) {
            if (rawMac.isNullOrBlank()) {
                macNorm = null
                acc = MeshWireNodeListAccumulator()
                peerUptimeOverlay.clear()
                _nodes.value = emptyList()
                return
            }
            val m = MeshNodeSyncMemoryStore.normalizeKey(rawMac)
            if (m == macNorm) {
                publishLocked()
                return
            }
            macNorm = m
            acc = MeshWireNodeListAccumulator()
            peerUptimeOverlay.clear()
            acc.trustNodeLastHeardTimestamps = false
            MeshNodeListDiskCache.load(ctx, m)?.let { acc.seedFromSummaries(it) }
            syncMyNumFromGatt()
            publishLocked()
        }
        schedulePersist()
        scheduleReloadPeerUptimeFromRoom()
    }

    /**
     * Каждый кадр FromRadio (NodeDB / NodeInfo). Только ставит кадр в очередь на [Dispatchers.IO]
     * — разбор и [publishLocked] не блокируют чтение BLE.
     */
    fun ingestFromRadioFrame(rawMac: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        appContext.get() ?: return
        val m = MeshNodeSyncMemoryStore.normalizeKey(rawMac)
        val queued = ingestChannel.trySend(m to bytes.copyOf())
        if (!queued.isSuccess) {
            ioScope.launch(Dispatchers.IO) { ingestFrameOnIo(m, bytes.copyOf()) }
        }
    }

    private fun ingestFrameOnIo(normalizedMac: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val ctx = appContext.get() ?: return
        synchronized(lock) {
            if (macNorm != null && macNorm != normalizedMac) return
            if (macNorm != normalizedMac) {
                macNorm = normalizedMac
                acc = MeshWireNodeListAccumulator()
                acc.trustNodeLastHeardTimestamps = false
                MeshNodeListDiskCache.load(ctx, normalizedMac)?.let { acc.seedFromSummaries(it) }
            }
            syncMyNumFromGatt()
            if (NodeGattConnection.initialWantConfigAcknowledged.value) {
                acc.acceptNodeInfo = true
            }
            val recvSec = System.currentTimeMillis() / 1000L
            try {
                acc.consumeFromRadio(bytes, recvSec, ctx)
                publishLocked()
            } catch (e: Throwable) {
                Log.e(TAG, "consumeFromRadio failed — кадр пропущен", e)
            }
        }
        schedulePersist()
    }

    /**
     * Точечное обновление одного узла (тот же upsert, что и из FromRadio): только изменившиеся поля,
     * запись «своей» ноды не затирается чужими данными — см. [MeshWireNodeListAccumulator].
     */
    fun upsertNode(rawMac: String, node: MeshWireNodeSummary) {
        val ctx = appContext.get() ?: return
        val m = MeshNodeSyncMemoryStore.normalizeKey(rawMac)
        ioScope.launch(Dispatchers.IO) {
            synchronized(lock) {
                if (macNorm != null && macNorm != m) return@synchronized
                if (macNorm != m) {
                    macNorm = m
                    acc = MeshWireNodeListAccumulator()
                    acc.trustNodeLastHeardTimestamps = false
                    MeshNodeListDiskCache.load(ctx, m)?.let { acc.seedFromSummaries(it) }
                }
                syncMyNumFromGatt()
                acc.seedFromSummaries(listOf(node))
                publishLocked()
            }
            schedulePersist()
        }
    }

    /** Результат отдельного want_config-запроса (карта / ручное обновление). */
    /**
     * Убрать узел из кэша приложения (RAM + оверлей аптайма + дисковый снимок списка).
     * Запись на радио NodeDB не выполняется.
     */
    fun forgetCachedNode(rawMac: String?, nodeNum: Long) {
        if (rawMac.isNullOrBlank()) return
        val ctx = appContext.get() ?: return
        val m = MeshNodeSyncMemoryStore.normalizeKey(rawMac)
        ioScope.launch(Dispatchers.IO) {
            try {
                peerUptimeDao?.deleteForMacAndNodeNum(m, nodeNum)
            } catch (_: Throwable) {
            }
        }
        val snapForDisk: List<MeshWireNodeSummary>
        synchronized(lock) {
            if (macNorm != m) return
            acc.removeNodeNum(nodeNum)
            peerUptimeOverlay.remove(nodeNum)
            publishLocked()
            snapForDisk = _nodes.value.toList()
        }
        persistJob?.cancel()
        ioScope.launch(Dispatchers.IO) {
            if (snapForDisk.isNotEmpty()) {
                MeshNodeListDiskCache.save(ctx, m, snapForDisk)
            } else {
                MeshNodeListDiskCache.clear(ctx, rawMac)
            }
        }
    }

    fun mergeFullSnapshot(rawMac: String, list: List<MeshWireNodeSummary>) {
        val ctx = appContext.get() ?: return
        val m = MeshNodeSyncMemoryStore.normalizeKey(rawMac)
        synchronized(lock) {
            if (macNorm != m) {
                macNorm = m
                acc = MeshWireNodeListAccumulator()
                acc.trustNodeLastHeardTimestamps = false
            }
            acc.seedFromSummaries(list)
            syncMyNumFromGatt()
            publishLocked()
        }
        ioScope.launch {
            MeshNodeListDiskCache.save(ctx, m, _nodes.value)
        }
    }

    fun onGattSessionEnded() {
        // Список остаётся в RAM; после разрыва BLE снова не доверяем last_heard с ноды до нового set_time_only.
        synchronized(lock) {
            acc.trustNodeLastHeardTimestamps = false
        }
    }

    fun clearForDevice(rawMac: String) {
        val ctx = appContext.get() ?: return
        val m = MeshNodeSyncMemoryStore.normalizeKey(rawMac)
        ioScope.launch(Dispatchers.IO) {
            try {
                peerUptimeDao?.deleteForDevice(m)
            } catch (_: Throwable) {
            }
        }
        synchronized(lock) {
            if (macNorm == m) {
                acc = MeshWireNodeListAccumulator()
                peerUptimeOverlay.clear()
                _nodes.value = emptyList()
            }
        }
        MeshNodeListDiskCache.clear(ctx, rawMac)
    }

    /**
     * Синхронное чтение последнего известного аптайма (сек) для [nodeNum] из RAM-оверлея
     * (заполняется из Room при attach и из пакетов portnum 502). Для ответа mesh-recovery (504).
     */
    fun peerLastKnownUptimeSecForNode(nodeNum: UInt): Long {
        val key = nodeNum.toLong() and 0xFFFF_FFFFL
        synchronized(lock) {
            return peerUptimeOverlay[key]?.uptimeSec ?: 0L
        }
    }

    /**
     * Обновление после приёма пакета аптайма (Room + оверлей + UI).
     */
    fun applyPeerUptimeFromNetwork(deviceMacNorm: String, nodeNum: Long, uptimeSec: Long, recvEpochSec: Long) {
        val dm = MeshNodeSyncMemoryStore.normalizeKey(deviceMacNorm)
        ioScope.launch(Dispatchers.IO) {
            try {
                peerUptimeDao?.upsert(
                    MeshNodePeerUptimeEntity(
                        deviceMacNorm = dm,
                        nodeNum = nodeNum,
                        lastKnownUptimeSec = uptimeSec,
                        uptimeTimestampEpochSec = recvEpochSec,
                    ),
                )
            } catch (_: Throwable) {
            }
            val applied = synchronized(lock) {
                if (macNorm != dm) {
                    false
                } else {
                    peerUptimeOverlay[nodeNum] = PeerUptimeRow(uptimeSec, recvEpochSec)
                    publishLocked()
                    true
                }
            }
            if (applied) schedulePersist()
        }
    }

    private fun scheduleReloadPeerUptimeFromRoom() {
        val dao = peerUptimeDao ?: return
        val targetMac = macNorm ?: return
        ioScope.launch(Dispatchers.IO) {
            val rows = try {
                dao.getAllForDevice(targetMac)
            } catch (_: Throwable) {
                emptyList()
            }
            synchronized(lock) {
                if (macNorm != targetMac) return@launch
                peerUptimeOverlay.clear()
                for (r in rows) {
                    peerUptimeOverlay[r.nodeNum] = PeerUptimeRow(r.lastKnownUptimeSec, r.uptimeTimestampEpochSec)
                }
                publishLocked()
            }
        }
    }

    private fun syncMyNumFromGatt() {
        NodeGattConnection.myNodeNum.value?.let { n ->
            if (n != 0u) acc.myNodeNum = n.toLong() and 0xFFFF_FFFFL
        }
    }

    private fun publishLocked() {
        syncMyNumFromGatt()
        val base = acc.toSummaries()
        val ov = peerUptimeOverlay
        if (ov.isEmpty()) {
            _nodes.value = base
            return
        }
        _nodes.value = base.map { n ->
            val u = ov[n.nodeNum] ?: return@map n
            n.copy(
                peerReportedUptimeSec = u.uptimeSec,
                peerUptimeReceivedEpochSec = u.recvEpochSec,
            )
        }
    }

    private fun schedulePersist() {
        val m = macNorm ?: return
        val ctx = appContext.get() ?: return
        persistJob?.cancel()
        persistJob = ioScope.launch {
            delay(750)
            val snap = synchronized(lock) { _nodes.value.toList() }
            if (snap.isNotEmpty()) {
                MeshNodeListDiskCache.save(ctx, m, snap)
            }
        }
    }
}
