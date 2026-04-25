package com.example.aura.preferences

import android.content.Context

object ChatConversationPreferences {
    private const val PREFS = "chat_conversation_prefs"
    private const val KEY_FONT_SCALE_PERCENT = "font_scale_percent"

    /** Масштаб шрифта в чате по слайдеру, 0…100 (%). */
    fun getFontScalePercent(context: Context): Float =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_FONT_SCALE_PERCENT, 0f)
            .coerceIn(0f, 100f)

    fun setFontScalePercent(context: Context, percent: Float) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SCALE_PERCENT, percent.coerceIn(0f, 100f))
            .apply()
    }
}
