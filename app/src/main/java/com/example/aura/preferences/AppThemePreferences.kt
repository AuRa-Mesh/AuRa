package com.example.aura.preferences

import android.content.Context

/** Режим темы как в типичном mesh-клиенте: системная / светлая / тёмная. */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

object AppThemePreferences {
    private const val PREFS = "aura_app_theme_prefs"
    private const val KEY_MODE = "theme_mode"

    fun getMode(context: Context): AppThemeMode {
        val v = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        return when (v) {
            "light" -> AppThemeMode.LIGHT
            "dark" -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    }

    fun setMode(context: Context, mode: AppThemeMode) {
        val s = when (mode) {
            AppThemeMode.SYSTEM -> "system"
            AppThemeMode.LIGHT -> "light"
            AppThemeMode.DARK -> "dark"
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, s)
            .apply()
    }
}
