package com.example.aura.security

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Защита от перебора пароля/кода.
 *
 * Состояние хранится в [android.content.SharedPreferences] (`aura_brute_force_guard`) per scope,
 * чтобы переживать рестарт процесса. Прогрессивные блокировки после N неверных подряд попыток:
 *
 * | попытка | пауза до следующей |
 * |---------|---------------------|
 * | 1 .. 2  | 0                   |
 * | 3       | 30 сек              |
 * | 4       | 1 мин               |
 * | 5       | 5 мин               |
 * | 6       | 15 мин              |
 * | 7+      | 30 мин (cap)        |
 *
 * При успехе вызывайте [reset] — счётчик обнуляется.
 *
 * Время берётся из `System.currentTimeMillis`. Откат часов назад удлинит блокировку, откат
 * вперёд её укоротит — приемлемый компромисс; злоупотребление «переводом часов» доступно
 * только тому, кто уже имеет доступ к устройству, а реальная защита от перебора строится на
 * том, что счётчик **не сбрасывается** при перезапуске приложения.
 */
object BruteForceGuard {

    private const val PREFS = "aura_brute_force_guard"

    data class State(val failedCount: Int, val lockedUntilMs: Long)

    // attempt index (1-based after failure) → lockout в секундах; последний элемент — cap.
    private val LOCKOUTS_SEC: LongArray = longArrayOf(
        0L,      // count==0 (никогда не используется, count ≥ 1 после первой неудачи)
        0L,      // count==1: свободная повторная попытка
        0L,      // count==2: свободная повторная попытка
        30L,     // count==3: 30 сек
        60L,     // count==4: 1 мин
        5 * 60L, // count==5: 5 мин
        15 * 60L,// count==6: 15 мин
        30 * 60L,// count==7+: cap 30 мин
    )

    /** Текущее состояние охраны (для UI). */
    fun state(context: Context, scope: String): State {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = p.getInt(keyCount(scope), 0).coerceAtLeast(0)
        val until = p.getLong(keyUntil(scope), 0L).coerceAtLeast(0L)
        return State(failedCount = count, lockedUntilMs = until)
    }

    /** Осталось миллисекунд блокировки; 0 — разблокировано. */
    fun remainingLockMs(context: Context, scope: String, nowMs: Long = System.currentTimeMillis()): Long {
        val until = state(context, scope).lockedUntilMs
        return (until - nowMs).coerceAtLeast(0L)
    }

    /**
     * Зафиксировать неудачную попытку: инкрементировать счётчик и выставить новое
     * `lockedUntilMs` согласно [LOCKOUTS_SEC]. Возвращает обновлённое состояние.
     */
    fun registerFailure(context: Context, scope: String): State {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nextCount = (p.getInt(keyCount(scope), 0) + 1).coerceAtLeast(1)
        val lockoutSec = LOCKOUTS_SEC[nextCount.coerceAtMost(LOCKOUTS_SEC.lastIndex)]
        val lockedUntil = if (lockoutSec > 0L) System.currentTimeMillis() + lockoutSec * 1000L else 0L
        p.edit()
            .putInt(keyCount(scope), nextCount)
            .putLong(keyUntil(scope), lockedUntil)
            .apply()
        return State(failedCount = nextCount, lockedUntilMs = lockedUntil)
    }

    /** Сбросить всё состояние после успешной авторизации/валидации. */
    fun reset(context: Context, scope: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(keyCount(scope))
            .remove(keyUntil(scope))
            .apply()
    }

    /**
     * Отформатировать остаток блокировки для отображения: `M:SS` для ≥1 мин, `N сек` иначе.
     */
    fun formatRemaining(remainingMs: Long): String {
        if (remainingMs <= 0L) return ""
        val totalSec = (remainingMs + 999L) / 1000L
        return if (totalSec >= 60L) {
            val m = totalSec / 60L
            val s = totalSec % 60L
            "%d:%02d".format(m, s)
        } else {
            "${totalSec} сек"
        }
    }

    private fun keyCount(scope: String): String = "count_$scope"
    private fun keyUntil(scope: String): String = "until_$scope"
}

/**
 * Живой снимок состояния [BruteForceGuard] для Compose-UI: перетикивает обратный отсчёт
 * с шагом 500 мс, пока идёт блокировка, и пересчитывает [remainingMs] только на кадр.
 */
class BruteForceGuardHolder internal constructor(
    val scope: String,
    initialFailedCount: Int,
    initialLockedUntilMs: Long,
) {
    var failedCount: Int by mutableIntStateOf(initialFailedCount)
        internal set
    var lockedUntilMs: Long by mutableLongStateOf(initialLockedUntilMs)
        internal set
    internal var nowMs: Long by mutableLongStateOf(System.currentTimeMillis())

    val remainingMs: Long get() = (lockedUntilMs - nowMs).coerceAtLeast(0L)
    val isLocked: Boolean get() = remainingMs > 0L
    val remainingLabel: String get() = BruteForceGuard.formatRemaining(remainingMs)

    fun registerFailure(context: Context) {
        val s = BruteForceGuard.registerFailure(context.applicationContext, scope)
        failedCount = s.failedCount
        lockedUntilMs = s.lockedUntilMs
        nowMs = System.currentTimeMillis()
    }

    fun reset(context: Context) {
        BruteForceGuard.reset(context.applicationContext, scope)
        failedCount = 0
        lockedUntilMs = 0L
        nowMs = System.currentTimeMillis()
    }
}

@Composable
fun rememberBruteForceGuard(scope: String): BruteForceGuardHolder {
    val context = LocalContext.current
    val holder = remember(scope) {
        val s = BruteForceGuard.state(context.applicationContext, scope)
        BruteForceGuardHolder(scope, s.failedCount, s.lockedUntilMs)
    }
    LaunchedEffect(holder.lockedUntilMs) {
        while (true) {
            holder.nowMs = System.currentTimeMillis()
            if (!holder.isLocked) break
            delay(500L)
        }
    }
    return holder
}
