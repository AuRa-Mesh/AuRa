package com.example.aura.vip

import android.content.Context
import android.util.Log
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.security.VipExtensionUsedCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "VipUsedCodeAnnouncer"

/**
 * Рассылка в mesh хэша только что использованного VIP-кода продления. Цель — чтобы соседи
 * занесли этот хэш в [VipUsedCodesMeshStore] и при следующей переустановке приложения вернули
 * его нам через
 * [MeshWireLoRaToRadioEncoder.encodeAuraVipUsedCodesResponse].
 *
 * Отправка best-effort:
 *  1) ждём готовности BLE (короткий таймаут),
 *  2) узнаём свой `myNodeNum`,
 *  3) шлём один широковещательный `ANNOUNCE` без want_ack.
 *
 * Доставка LoRa не гарантирована → приняв код, мы повторяем анонс ещё [RETRIES] раз с
 * экспоненциальной паузой, пока либо успешно не отправим, либо не исчерпаем попытки.
 */
object VipUsedCodeMeshAnnouncer {

    private const val RETRIES: Int = 3
    private const val WAIT_BLE_POLL_MS: Long = 3_000L
    private const val WAIT_BLE_MAX_MS: Long = 45_000L
    private const val RETRY_BASE_GAP_MS: Long = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Анонсировать факт использования кода [normalizedCode]. Безопасно вызывать синхронно из
     * UI-потока сразу после [VipExtensionUsedCodes.markUsed].
     */
    fun announce(context: Context, normalizedCode: String) {
        val appCtx = context.applicationContext
        val hash = VipExtensionUsedCodes.hashForMesh(normalizedCode) ?: return
        scope.launch { runAnnounceLoop(appCtx, hash) }
    }

    private suspend fun runAnnounceLoop(appCtx: Context, hash: ByteArray) {
        if (!waitForBleReady()) {
            Log.w(TAG, "BLE not ready within timeout — skipping announce")
            return
        }
        var attempt = 0
        while (attempt < RETRIES) {
            val my = NodeGattConnection.myNodeNum.value ?: 0u
            if (my == 0u) {
                delay(WAIT_BLE_POLL_MS)
                attempt++
                continue
            }
            val payload = MeshWireLoRaToRadioEncoder.encodeAuraVipUsedCodesAnnounce(
                senderNodeNum = my,
                hashes = listOf(hash),
            )
            val sent = sendOnce(payload)
            if (sent) return
            delay(RETRY_BASE_GAP_MS * (attempt + 1))
            attempt++
        }
    }

    private suspend fun waitForBleReady(): Boolean {
        var waited = 0L
        while (!NodeGattConnection.isReady && waited < WAIT_BLE_MAX_MS) {
            delay(WAIT_BLE_POLL_MS)
            waited += WAIT_BLE_POLL_MS
        }
        return NodeGattConnection.isReady
    }

    private suspend fun sendOnce(payload: ByteArray): Boolean {
        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                NodeGattConnection.sendToRadio(payload) { ok, err ->
                    if (!ok) Log.w(TAG, "used-code announce write failed: ${err ?: "?"}")
                    if (cont.isActive) cont.resumeWith(Result.success(ok))
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "used-code announce exception: ${t.message}")
            false
        }
    }
}
