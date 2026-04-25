package com.example.aura.preferences

import android.content.Context

object ChatWallpaperPreferences {
    private const val PREFS = "chat_wallpaper_prefs"
    private const val KEY_INDEX = "wallpaper_index"
    private const val KEY_DIM_PERCENT = "wallpaper_dim_percent"

    fun getWallpaperIndex(context: Context): Int =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INDEX, 0)

    fun setWallpaperIndex(context: Context, index: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INDEX, index.coerceIn(0, 7))
            .apply()
    }

    /** Затемнение обоев чата поверх картинки, 0…100 (%). */
    fun getWallpaperDimPercent(context: Context): Int =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DIM_PERCENT, 0)
            .coerceIn(0, 100)

    fun setWallpaperDimPercent(context: Context, percent: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DIM_PERCENT, percent.coerceIn(0, 100))
            .apply()
    }
}
