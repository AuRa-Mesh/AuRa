package com.example.aura.mesh.uptime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aura.app.AppUptimeTracker
import com.example.aura.bluetooth.NodeGattConnection

/**
 * Периодическая проверка (WorkManager, не чаще интервала политики) — отправка аптайма в mesh при необходимости.
 */
class UptimeMeshSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!UptimeMeshSyncPreferences.shouldAttemptSend(applicationContext)) {
            return Result.success()
        }
        if (!NodeGattConnection.isReady) {
            return Result.success()
        }
        AppUptimeTracker.checkpoint()
        UptimeMeshSyncPreferences.markAttemptNow(applicationContext)
        val ok = try {
            MeshPeerUptimeMeshSender.trySendAndAwaitRoutingAck(applicationContext)
        } catch (_: Throwable) {
            false
        }
        if (ok) {
            UptimeMeshSyncPreferences.markSendSuccess(applicationContext)
        } else {
            UptimeMeshSyncPreferences.markSendFailure(applicationContext)
        }
        return Result.success()
    }
}
