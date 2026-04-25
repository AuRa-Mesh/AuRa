package com.example.aura.mesh.nodedb

import android.content.Context
import com.example.aura.bluetooth.MeshNodeRemoteActions
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.mesh.PacketDispatcher
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireNodeSummary
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Результат принудительного запроса NodeInfo / Position по mesh. */
sealed interface NodeInfoRefreshResult {
    data object Success : NodeInfoRefreshResult
    data class Cooldown(val secondsLeft: Int) : NodeInfoRefreshResult
    data class SendFailed(val message: String?) : NodeInfoRefreshResult
    data object Timeout : NodeInfoRefreshResult
}

/**
 * Запрос карточки узла (NODEINFO_APP, пустой payload, `to = target`, `want_response = true`)
 * и ожидание ответа по FromRadio или обновлению [MeshNodeDbRepository].
 *
 * Лимит: не чаще одного **успешного** запроса на пару (устройство, target) за [COOLDOWN_MS]
 * (таймаут или ошибка записи не блокируют повтор).
 */
object MeshNodeInfoRefreshCoordinator {

    private const val COOLDOWN_MS = 60_000L
    private const val AWAIT_MS = 30_000L

    private val lastCompletedSendAt = ConcurrentHashMap<String, Long>()

    private fun key(deviceAddress: String, targetNodeNumLong: Long): String =
        "${MeshNodeSyncMemoryStore.normalizeKey(deviceAddress)}|${targetNodeNumLong and 0xFFFF_FFFFL}"

    fun cooldownSecondsRemaining(deviceAddress: String, targetNodeNumLong: Long): Int {
        val last = lastCompletedSendAt[key(deviceAddress, targetNodeNumLong)] ?: return 0
        val rem = COOLDOWN_MS - (System.currentTimeMillis() - last)
        return if (rem <= 0) 0 else ((rem + 999) / 1000).toInt()
    }

    private fun coordsMeaningfullyChanged(n: MeshWireNodeSummary, b: MeshWireNodeSummary): Boolean {
        val nLat = n.latitude
        val nLon = n.longitude
        val bLat = b.latitude
        val bLon = b.longitude
        if (nLat != null && nLon != null) {
            if (bLat == null || bLon == null) return true
            if (kotlin.math.abs(nLat - bLat) > 1e-7 || kotlin.math.abs(nLon - bLon) > 1e-7) return true
        }
        return false
    }

    private fun nodeDbMeaningfullyUpdated(n: MeshWireNodeSummary, baseline: MeshWireNodeSummary): Boolean {
        if (n.longName.trim() != baseline.longName.trim()) return true
        if (n.shortName.trim() != baseline.shortName.trim()) return true
        if (coordsMeaningfullyChanged(n, baseline)) return true
        val nSeen = n.lastSeenEpochSec
        val bSeen = baseline.lastSeenEpochSec
        if (nSeen != null && bSeen != null && nSeen > bSeen) return true
        if (n.lastSnrDb != baseline.lastSnrDb && n.lastSnrDb != null) return true
        if (n.batteryPercent != baseline.batteryPercent && n.batteryPercent != null) return true
        if (n.publicKeyB64 != baseline.publicKeyB64 && !n.publicKeyB64.isNullOrBlank()) return true
        return false
    }

    /**
     * Успех по NodeDB, если явных изменений полей нет, но [lastSeenEpochSec] продвинулся относительно
     * снимка **до** отправки запроса (типичный ответ NODEINFO без смены имени).
     *
     * Сравнение с [baseline] из UI недостаточно: merge мог уже обновить кэш до нажатия кнопки.
     */
    private fun nodeDbShowsNewHearAfterRequest(
        n: MeshWireNodeSummary,
        baseline: MeshWireNodeSummary,
        preRequestLastSeenEpoch: Long?,
    ): Boolean {
        if (nodeDbMeaningfullyUpdated(n, baseline)) return true
        val seen = n.lastSeenEpochSec ?: return false
        if (preRequestLastSeenEpoch == null) return false
        return seen > preRequestLastSeenEpoch
    }

    /** Обёртка под UI-задачу: On-Demand запрос NodeInfo по `Int` node num. */
    suspend fun requestRemoteNodeInfo(
        context: Context,
        deviceAddress: String,
        targetNodeNum: Int,
        baseline: MeshWireNodeSummary,
    ): NodeInfoRefreshResult =
        sendNodeInfoRequestAndAwait(
            context = context,
            deviceAddress = deviceAddress,
            targetNodeNum = targetNodeNum.toUInt(),
            baseline = baseline,
        )

    /**
     * Отправляет запрос NodeInfo на [targetNodeNum], ждёт `NODEINFO_APP` с `from == target`
     * или обновление строки узла в NodeDB относительно [baseline].
     */
    suspend fun sendNodeInfoRequestAndAwait(
        context: Context,
        deviceAddress: String,
        targetNodeNum: UInt,
        baseline: MeshWireNodeSummary,
    ): NodeInfoRefreshResult = withContext(Dispatchers.IO) {
        val targetLong = targetNodeNum.toLong() and 0xFFFF_FFFFL
        val k = key(deviceAddress, targetLong)
        val now = System.currentTimeMillis()
        val prev = lastCompletedSendAt[k] ?: 0L
        if (now - prev < COOLDOWN_MS) {
            val left = COOLDOWN_MS - (now - prev)
            return@withContext NodeInfoRefreshResult.Cooldown(((left + 999) / 1000).toInt())
        }

        val preRequestLastSeenEpoch: Long? =
            MeshNodeDbRepository.nodes.value
                .firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == targetLong }
                ?.lastSeenEpochSec

        val fromTargetSeen = CompletableDeferred<Unit>()
        val listener: (ByteArray) -> Unit = listener@{ frame ->
            try {
                if (fromTargetSeen.isCompleted) return@listener
                for (legacyNum in MeshWireFromRadioMeshPacketParser.extractLegacyNodeInfoNodeNumsFromFromRadio(frame)) {
                    if (legacyNum == targetLong) {
                        fromTargetSeen.complete(Unit)
                        return@listener
                    }
                }
                if (fromTargetSeen.isCompleted) return@listener
                val raw = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(frame)
                val payloads = PacketDispatcher.prioritizeMeshPayloads(raw)
                for (p in payloads) {
                    val from = p.logicalFrom() ?: continue
                    if ((from.toLong() and 0xFFFF_FFFFL) != targetLong) continue
                    when (p.portnum) {
                        MeshWireFromRadioMeshPacketParser.PORTNUM_NODEINFO_APP,
                        MeshWireFromRadioMeshPacketParser.PORTNUM_POSITION_APP,
                        MeshWireFromRadioMeshPacketParser.PORTNUM_TELEMETRY,
                        -> {
                            fromTargetSeen.complete(Unit)
                            break
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        NodeGattConnection.addFrameListener(listener)
        try {
            val okNodeInfo = suspendCancellableCoroutine { cont ->
                MeshNodeRemoteActions.requestRemoteNodeInfo(
                    context.applicationContext,
                    deviceAddress,
                    targetNodeNum.toInt(),
                ) { ok, err, _ ->
                    if (cont.isActive) cont.resume(ok to err)
                }
            }
            if (!okNodeInfo.first) {
                return@withContext NodeInfoRefreshResult.SendFailed(okNodeInfo.second)
            }

            val reached = withTimeoutOrNull(AWAIT_MS) {
                coroutineScope {
                    val packetWait = async { fromTargetSeen.await() }
                    val dbWait = async {
                        MeshNodeDbRepository.nodes.first { nodes: List<MeshWireNodeSummary> ->
                            val n = nodes.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == targetLong }
                                ?: return@first false
                            nodeDbShowsNewHearAfterRequest(n, baseline, preRequestLastSeenEpoch)
                        }
                        Unit
                    }
                    select {
                        packetWait.onAwait {
                            packetWait.await()
                        }
                        dbWait.onAwait {
                            dbWait.await()
                        }
                    }
                }
            }
            if (reached == null) {
                NodeInfoRefreshResult.Timeout
            } else {
                lastCompletedSendAt[k] = System.currentTimeMillis()
                NodeInfoRefreshResult.Success
            }
        } finally {
            NodeGattConnection.removeFrameListener(listener)
        }
    }
}
