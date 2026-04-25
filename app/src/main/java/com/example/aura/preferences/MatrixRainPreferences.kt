package com.example.aura.preferences

import android.content.Context

/**
 * Настройки эффекта Matrix: плотность столбцов, скорость падения, затемнение поверх «дождя».
 */
object MatrixRainPreferences {
    private const val PREFS = "matrix_rain_prefs"
    private const val KEY_DENSITY = "density_mul"
    private const val KEY_SPEED = "speed_mul"
    private const val KEY_DIM = "dim_alpha"

    const val DENSITY_MIN = 0.35f
    const val DENSITY_MAX = 1.85f
    const val SPEED_MIN = 0.2f
    const val SPEED_MAX = 2.4f
    const val DIM_MIN = 0f
    const val DIM_MAX = 0.88f

    private const val DEF_DENSITY = 1f
    private const val DEF_SPEED = 1f
    private const val DEF_DIM = 0.38f

    fun densityMultiplier(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_DENSITY, DEF_DENSITY).coerceIn(DENSITY_MIN, DENSITY_MAX)

    fun speedMultiplier(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_SPEED, DEF_SPEED).coerceIn(SPEED_MIN, SPEED_MAX)

    /** Затемнение: непрозрачность чёрной плашки поверх символов (0 = без затемнения). */
    fun dimOverlayAlpha(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_DIM, DEF_DIM).coerceIn(DIM_MIN, DIM_MAX)

    fun save(
        ctx: Context,
        densityMultiplier: Float,
        speedMultiplier: Float,
        dimOverlayAlpha: Float,
    ) {
        prefs(ctx).edit()
            .putFloat(KEY_DENSITY, densityMultiplier.coerceIn(DENSITY_MIN, DENSITY_MAX))
            .putFloat(KEY_SPEED, speedMultiplier.coerceIn(SPEED_MIN, SPEED_MAX))
            .putFloat(KEY_DIM, dimOverlayAlpha.coerceIn(DIM_MIN, DIM_MAX))
            .apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
