package com.example.aura.vip

import android.content.Context
import android.util.Log
import com.example.aura.app.AppUptimeTracker
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.preferences.VipAccessPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

private const val TAG = "VipMeshRecovery"

/**
 * «Mesh-backed» восстановление VIP-таймера после полной переустановки приложения.
 *
 * Почему это нужно. Раньше мы полагались на два слоя персистентности:
 *   - L2 — Android Auto Backup (`backup_rules.xml`): работает **только** если пользователь
 *     включил облачный бэкап и переставляет приложение через «тот же» Google-аккаунт;
 *   - L3 — sentinel-файл в публичном каталоге (`Pictures/Aura/`, см.
 *     [com.example.aura.preferences.VipTimerExternalSentinel]): требует разрешения
 *     `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`, которое пользователь может не дать.
 * Если оба слоя недоступны, таймер сбрасывался «как на новом устройстве».
 *
 * Теперь есть L4 — mesh. Каждый Aura-клиент уже шлёт раз в ~30 мин свой VIP-статус с
 * `remaining_sec` ([com.example.aura.meshwire.MeshWireAuraVipCodec]). Соседи запоминают это
 * значение в [VipStatusStore]. На свежей установке мы широковещательно запрашиваем своё
 * последнее известное значение у любого, кто нас помнит (portnum 504,
 * [com.example.aura.meshwire.MeshWireAuraVipRecoveryCodec]), и, получив unicast-ответ,
 * восстанавливаем таймер через [VipAccessPreferences.applyMeshRecoveredDeadline].
 *
 * Политика отправки — консервативная (эфир дорогой):
 *   - всего [MAX_ATTEMPTS] попыток, по одной попытке раз в [ATTEMPT_GAP_MS];
 *   - запрос перестаёт отправляться сразу, как только
 *     [VipAccessPreferences.isInitialTimerSeeded] становится `true`, аптайм уже не просит mesh
 *     ([AppUptimeTracker.wantsMeshUptimeRecovery]).
 *
 * После [MAX_ATTEMPTS] без успеха по аптайму вызывается
 * [AppUptimeTracker.markMeshUptimeRecoveryExhausted].
 *
 * Приёмная сторона запросов/ответов — [com.example.aura.mesh.repository.MeshIncomingChatRepository].
 */
object VipMeshRecoveryCoordinator {

    /** Максимум попыток рассылки запроса восстановления. */
    private const val MAX_ATTEMPTS: Int = 4

    /** Интервал между попытками (соседи могли быть вне зоны). */
    private const val ATTEMPT_GAP_MS: Long = 45_000L

    /** Ждать BLE READY, если ещё не готово. */
    private const val WAIT_BLE_POLL_MS: Long = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started: Boolean = false

    fun start(appContext: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = appContext.applicationContext
        scope.launch { runRecoveryLoop(app) }
    }

    private suspend fun runRecoveryLoop(app: Context) {
        val vipDone = VipAccessPreferences.isInitialTimerSeeded(app)
        val uptimeWants = AppUptimeTracker.wantsMeshUptimeRecovery(app)
        if (vipDone && !uptimeWants) return

        var attempt = 0
        while (coroutineContext.isActive && attempt < MAX_ATTEMPTS) {
            val needVip = !VipAccessPreferences.isInitialTimerSeeded(app)
            val needUptime = AppUptimeTracker.wantsMeshUptimeRecovery(app)
            if (!needVip && !needUptime) return
            if (!NodeGattConnection.isReady) {
                delay(WAIT_BLE_POLL_MS)
                continue
            }
            val my = NodeGattConnection.myNodeNum.value
            if (my == null || my == 0u) {
                delay(WAIT_BLE_POLL_MS)
                continue
            }
            val payload = MeshWireLoRaToRadioEncoder.encodeAuraVipRecoveryRequest(senderNodeNum = my)
            NodeGattConnection.sendToRadio(payload) { ok, err ->
                if (!ok) Log.w(TAG, "vip-recovery request failed: ${err ?: "?"}")
            }
            // Одновременно — запрос на восстановление использованных VIP-кодов
            // ([com.example.aura.meshwire.MeshWireAuraVipUsedCodesCodec], portnum 505).
            // Это гарантирует, что после переустановки ранее введённый код нельзя будет
            // активировать повторно — как только хоть один сосед нас помнит.
            val usedReq = MeshWireLoRaToRadioEncoder.encodeAuraVipUsedCodesRequest(senderNodeNum = my)
            NodeGattConnection.sendToRadio(usedReq) { ok, err ->
                if (!ok) Log.w(TAG, "vip-used-codes request failed: ${err ?: "?"}")
            }
            attempt++
            delay(ATTEMPT_GAP_MS)
        }
        if (attempt >= MAX_ATTEMPTS && AppUptimeTracker.wantsMeshUptimeRecovery(app)) {
            AppUptimeTracker.markMeshUptimeRecoveryExhausted(app)
        }
    }
}
