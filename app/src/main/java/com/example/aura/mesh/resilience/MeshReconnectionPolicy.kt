package com.example.aura.mesh.resilience

import kotlin.math.min
import kotlin.math.pow

/**
 * Экспоненциальная задержка между попытками восстановления связи (мс).
 * baseMs * 2^attempt, capped at [maxDelayMs].
 */
object MeshReconnectionPolicy {

    private const val BASE_MS = 500L
    private const val MAX_MS = 15_000L

    fun delayMsForAttempt(attempt: Int): Long {
        if (attempt <= 0) return BASE_MS
        val exp = BASE_MS * 2.0.pow((attempt - 1).coerceAtMost(12)).toLong()
        return min(exp, MAX_MS)
    }
}
