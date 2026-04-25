package com.example.aura.vip

import android.content.Context
import android.util.Log
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.progression.AuraGodNodeProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

private const val TAG = "VipStatusBroadcaster"

/**
 * Синхронизация VIP-статуса по mesh: broadcast [com.example.aura.meshwire.MeshWireAuraVipCodec.PORTNUM] (503).
 */
object VipStatusBroadcaster {

    private const val HEARTBEAT_MS: Long = 30L * 60L * 1000L
    private const val DEFAULT_VALID_FOR_SEC: UInt = 3_900u
    private const val MIN_SEND_GAP_MS: Long = 2L * 60L * 1000L
    private const val IDENTICAL_PAYLOAD_MIN_INTERVAL_MS: Long = 50L * 60L * 1000L
    private const val WAIT_BLE_POLL_MS: Long = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastSentAtMs: Long = 0L

    @Volatile
    private var lastSentActive: Boolean? = null

    @Volatile
    private var lastSentPayloadSignature: String? = null

    fun start(appContext: Context) {
        val app = appContext.applicationContext
        scope.launch {
            var prevExpires: Long? = null
            VipAccessPreferences.expiresAtMsFlow.collectLatest { expires ->
                if (AuraGodNodeProfile.matches(app)) {
                    VipAccessPreferences.getExpiresAtMs(app)
                    prevExpires = AuraGodNodeProfile.eternalVipExpiresAtMs()
                    return@collectLatest
                }
                val deadlineChanged = prevExpires != null && prevExpires != expires
                prevExpires = expires
                if (deadlineChanged) {
                    delay(400)
                    broadcastSelfVipIfPossible(app, forceOnChange = true)
                }
                if (expires <= 0L) return@collectLatest
                while (System.currentTimeMillis() < expires) {
                    val left = expires - System.currentTimeMillis()
                    if (left <= 0L) break
                    delay(minOf(30_000L, left).coerceAtLeast(200L))
                }
                delay(400)
                broadcastSelfVipIfPossible(app, forceOnChange = true)
            }
        }
        scope.launch { runHeartbeatLoop(app) }
    }

    private suspend fun runHeartbeatLoop(app: Context) {
        while (coroutineContext.isActive) {
            if (!NodeGattConnection.isReady) {
                delay(WAIT_BLE_POLL_MS)
                continue
            }
            broadcastSelfVipIfPossible(app, forceOnChange = false)
            delay(HEARTBEAT_MS)
        }
    }

    private fun broadcastSelfVipIfPossible(app: Context, forceOnChange: Boolean) {
        if (!NodeGattConnection.isReady) return
        val my = NodeGattConnection.myNodeNum.value ?: return
        if (my == 0u) return
        val active = VipWireMarker.isSelfVipActive(app)
        val now = System.currentTimeMillis()
        val stateChanged = lastSentActive != active
        val throttled = (now - lastSentAtMs) < MIN_SEND_GAP_MS
        if (!stateChanged && !forceOnChange && throttled) return
        val deadlineMs = VipAccessPreferences.getExpiresAtMs(app)
        val remainingSec: UInt = if (!active || deadlineMs <= now) {
            0u
        } else {
            val remainMs = (deadlineMs - now).coerceAtLeast(0L)
            (remainMs / 1000L).coerceAtMost(UInt.MAX_VALUE.toLong()).toUInt()
        }
        val signature = "${active}|${remainingSec}"
        val refreshStaleForNeighbors = (now - lastSentAtMs) >= IDENTICAL_PAYLOAD_MIN_INTERVAL_MS
        if (!stateChanged && !forceOnChange && !refreshStaleForNeighbors && signature == lastSentPayloadSignature) {
            return
        }
        val payload = MeshWireLoRaToRadioEncoder.encodeAuraVipBroadcast(
            senderNodeNum = my,
            active = active,
            validForSec = DEFAULT_VALID_FOR_SEC,
            remainingSec = remainingSec,
        )
        NodeGattConnection.sendToRadio(payload) { ok, err ->
            if (ok) {
                lastSentAtMs = System.currentTimeMillis()
                lastSentActive = active
                lastSentPayloadSignature = signature
            } else {
                Log.w(TAG, "aura-vip broadcast write failed: ${err ?: "?"}")
            }
        }
    }
}
