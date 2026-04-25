package com.example.aura.mesh.uptime

import android.os.Handler
import android.os.Looper
import com.example.aura.chat.MeshWireStatusMapper
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireRoutingPayloadParser
import com.example.aura.meshwire.ParsedMeshDataPayload
import java.util.concurrent.ConcurrentHashMap

private data class WaitEntry(
    val timeoutRunnable: Runnable,
    val onDone: (Boolean) -> Unit,
)

/**
 * Ожидание [ROUTING_APP] по [Data.request_id] после исходящего пакета с [MeshPacket.want_ack].
 */
object MeshUptimeRoutingWaiter {

    private val handler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<UInt, WaitEntry>()

    fun register(packetId: UInt, timeoutMs: Long, onDone: (Boolean) -> Unit) {
        cancel(packetId, invoke = false)
        val timeout = Runnable {
            val e = pending.remove(packetId) ?: return@Runnable
            e.onDone(false)
        }
        pending[packetId] = WaitEntry(timeout, onDone)
        handler.postDelayed(timeout, timeoutMs)
    }

    /** Отмена ожидания (ошибка записи ToRadio и т.п.). */
    fun cancel(packetId: UInt, invoke: Boolean = true) {
        val e = pending.remove(packetId) ?: return
        handler.removeCallbacks(e.timeoutRunnable)
        if (invoke) e.onDone(false)
    }

    /**
     * @return true если этот ROUTING закрыл наше ожидание.
     */
    fun deliverRouting(p: ParsedMeshDataPayload): Boolean {
        if (p.portnum != MeshWireFromRadioMeshPacketParser.PORTNUM_ROUTING_APP) return false
        val pid = p.dataRequestId ?: return false
        val e = pending.remove(pid) ?: return false
        handler.removeCallbacks(e.timeoutRunnable)
        val errCode = MeshWireRoutingPayloadParser.parseErrorReason(p.payload) ?: 0
        val mapped = MeshWireStatusMapper.mapRoutingError(errCode)
        val ok = mapped == MeshWireStatusMapper.MeshRoutingUiResult.Success ||
            mapped == MeshWireStatusMapper.MeshRoutingUiResult.UnknownCode
        e.onDone(ok)
        return true
    }
}
