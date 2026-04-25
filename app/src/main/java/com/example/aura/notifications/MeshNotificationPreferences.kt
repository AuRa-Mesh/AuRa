package com.example.aura.notifications

import android.content.Context

/**
 * Локальные настройки телефона для уведомлений (не путать с [com.example.aura.meshwire.MeshWireExternalNotificationPushState] на ноде).
 */
object MeshNotificationPreferences {
    private const val PREFS = "aura_mesh_notifications"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_FILTER_DM = "filter_dm"
    private const val KEY_FILTER_CHANNEL = "filter_channel"
    private const val KEY_SMART_ALERT = "smart_alert"
    private const val KEY_SHOW_PREVIEW = "show_preview"
    private const val KEY_LAST_BATTERY_WARN_MS = "last_battery_warn_ms"

    fun isMasterEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MASTER, false)

    fun setMasterEnabled(context: Context, v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MASTER, v).apply()
    }

    fun filterPrivateMessages(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FILTER_DM, true)

    fun setFilterPrivateMessages(context: Context, v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FILTER_DM, v).apply()
    }

    fun filterChannelMessages(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FILTER_CHANNEL, true)

    fun setFilterChannelMessages(context: Context, v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FILTER_CHANNEL, v).apply()
    }

    fun smartAlert(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SMART_ALERT, true)

    fun setSmartAlert(context: Context, v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SMART_ALERT, v).apply()
    }

    fun showPreview(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_PREVIEW, true)

    fun setShowPreview(context: Context, v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_PREVIEW, v).apply()
    }

    fun lastBatteryWarnEpochMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_BATTERY_WARN_MS, 0L)

    fun setLastBatteryWarnEpochMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_BATTERY_WARN_MS, ms).apply()
    }
}
