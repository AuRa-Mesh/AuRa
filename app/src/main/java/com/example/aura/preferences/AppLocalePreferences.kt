package com.example.aura.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLocalePreferences {
    private const val PREFS = "aura_app_locale_prefs"
    private const val KEY_TAG = "locale_tag"

    /** Поддерживаются только en и ru; пусто — как в системе (через пустой список локалей приложения). */
    private fun normalizeLocaleTag(raw: String): String =
        when (raw.trim().lowercase(Locale.ROOT)) {
            "en" -> "en"
            "ru" -> "ru"
            else -> ""
        }

    fun getStoredLocaleTag(context: Context): String {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "") ?: ""
        return normalizeLocaleTag(stored)
    }

    fun applyStoredLocale(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TAG, "") ?: ""
        val tag = normalizeLocaleTag(raw)
        if (tag != raw) {
            prefs.edit().putString(KEY_TAG, tag).apply()
        }
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setLocaleTag(context: Context, tag: String) {
        val normalized = normalizeLocaleTag(tag)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, normalized)
            .apply()
        applyStoredLocale(context)
    }
}
