package com.example.aura.mesh

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Периодическая проверка: поднять [MessageResilientService], если сессия есть, а служба не работает.
 */
class ResilientMeshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        MessageResilientService.startIfNeeded(applicationContext)
        return Result.success()
    }
}
