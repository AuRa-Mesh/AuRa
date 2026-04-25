package com.example.aura.mesh.uptime

import android.content.Context
import com.example.aura.app.AppUptimeTracker
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.security.NodeAuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Отправка аптайма в эфир (primary channel, broadcast) с ожиданием [ROUTING_APP] при [want_ack].
 */
object MeshPeerUptimeMeshSender {

    suspend fun trySendAndAwaitRoutingAck(context: Context): Boolean {
        val ctx = context.applicationContext
        if (!NodeGattConnection.isReady) return false
        val my = NodeGattConnection.myNodeNum.value ?: return false
        if (my == 0u) return false
        if (NodeAuthStore.load(ctx)?.deviceAddress?.trim().isNullOrEmpty()) return false
        val uptimeSec = AppUptimeTracker.uptimeMs() / 1000L
        val (bytes, packetId) = MeshWireLoRaToRadioEncoder.encodeAuraPeerUptimeBroadcast(
            senderNodeNum = my,
            uptimeSeconds = uptimeSec,
            channelIndex = 0u,
        )
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                MeshUptimeRoutingWaiter.register(packetId, 12_000L) { ok ->
                    if (cont.isActive) cont.resume(ok)
                }
                cont.invokeOnCancellation {
                    MeshUptimeRoutingWaiter.cancel(packetId, invoke = false)
                }
                NodeGattConnection.sendToRadio(bytes) { gattOk, _ ->
                    if (!gattOk) {
                        MeshUptimeRoutingWaiter.cancel(packetId, invoke = true)
                    }
                }
            }
        }
    }
}
