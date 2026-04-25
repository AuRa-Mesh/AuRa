package com.example.aura.progression

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Периодически (WorkManager, минимум ~15 мин) подтягивает начисление опыта из аптайма приложения,
 * чтобы не пропустить целые часы, если процесс долго не делал checkpoint.
 */
class AuraUptimeMiningWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AuraExperience.syncExperienceFromAppUptime(applicationContext)
        return Result.success()
    }
}
