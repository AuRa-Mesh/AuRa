package com.example.aura

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Однократный перенос файлов SharedPreferences и БД после смены префикса `aurus_` → `aura_`,
 * чтобы обновление с предыдущей сборки не теряло данные.
 */
object LegacyStorageMigration {

    private const val MARKER_PREFS = "aura_legacy_storage_marker"
    private const val KEY_DONE = "from_aurus_names_v1"

    fun migrateIfNeeded(context: Context) {
        val marker = context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
        if (marker.getBoolean(KEY_DONE, false)) return

        for ((oldName, newName) in SHARED_PREFS_RENAMES) {
            copySharedPreferencesIfLegacyExists(context, oldName, newName)
        }
        renameDatabaseIfLegacyExists(context, "aurus_chat.db", "aura_chat.db")
        renameFileUnderFilesDir(context, "offline/maps/aurus.mbtiles", "offline/maps/aura.mbtiles")

        marker.edit().putBoolean(KEY_DONE, true).apply()
    }

    private val SHARED_PREFS_RENAMES: List<Pair<String, String>> = listOf(
        "aurus_mesh_node_list" to "aura_mesh_node_list",
        "aurus_mesh_notifications" to "aura_mesh_notifications",
        "aurus_mesh_location_prefs" to "aura_mesh_location_prefs",
        "aurus_last_user_map_center" to "aura_last_user_map_center",
        "aurus_mesh_node_sync" to "aura_mesh_node_sync",
        "aurus_auth" to "aura_auth",
        "aurus_app_uptime_prefs" to "aura_app_uptime_prefs",
        "aurus_first_launch_tutorial" to "aura_first_launch_tutorial",
        "aurus_profile_local_avatar" to "aura_profile_local_avatar",
        "aurus_app_theme_prefs" to "aura_app_theme_prefs",
        "aurus_app_locale_prefs" to "aura_app_locale_prefs",
        "aurus_uptime_mesh_sync" to "aura_uptime_mesh_sync",
        "aurus_mesh_own_node" to "aura_mesh_own_node",
        "aurus_mesh_prefs" to "aura_mesh_prefs",
    )

    private fun copySharedPreferencesIfLegacyExists(context: Context, oldName: String, newName: String) {
        val old = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
        val new = context.getSharedPreferences(newName, Context.MODE_PRIVATE)
        if (old.all.isEmpty()) return
        if (new.all.isNotEmpty()) return
        val e = new.edit()
        copyAllPrefs(old, e)
        e.apply()
        context.deleteSharedPreferences(oldName)
    }

    private fun copyAllPrefs(from: SharedPreferences, to: SharedPreferences.Editor) {
        for ((k, v) in from.all) {
            when (v) {
                is String -> to.putString(k, v)
                is Int -> to.putInt(k, v)
                is Long -> to.putLong(k, v)
                is Float -> to.putFloat(k, v)
                is Boolean -> to.putBoolean(k, v)
                is Set<*> -> @Suppress("UNCHECKED_CAST") to.putStringSet(k, v as Set<String>)
                null -> to.remove(k)
                else -> Unit
            }
        }
    }

    private fun renameDatabaseIfLegacyExists(context: Context, oldFileName: String, newFileName: String) {
        val newF = context.getDatabasePath(newFileName)
        if (newF.exists()) return
        val oldF = context.getDatabasePath(oldFileName)
        if (oldF.exists()) {
            oldF.renameTo(newF)
        }
    }

    private fun renameFileUnderFilesDir(context: Context, oldRelative: String, newRelative: String) {
        val newF = File(context.filesDir, newRelative)
        if (newF.exists()) return
        val oldF = File(context.filesDir, oldRelative)
        if (oldF.exists()) {
            newF.parentFile?.mkdirs()
            oldF.renameTo(newF)
        }
    }
}
