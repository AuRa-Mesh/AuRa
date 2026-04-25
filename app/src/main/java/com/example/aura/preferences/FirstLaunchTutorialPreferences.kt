package com.example.aura.preferences

import android.content.Context
import android.content.SharedPreferences

/** Первый запуск: инструкция — флаг **по mesh node id**, как прогресс в аллее славы. */
object FirstLaunchTutorialPreferences {
    private const val PREFS = "aura_first_launch_tutorial"
    private const val KEY_FLOW_COMPLETED = "first_launch_flow_completed"
    private const val KEY_SCOPE_MIGRATED = "first_launch_scope_migrated_v1"

    private fun rawPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun migrateFlatIfNeeded(context: Context) {
        val app = context.applicationContext
        val p = rawPrefs(app)
        if (p.getBoolean(KEY_SCOPE_MIGRATED, false)) return
        val nk = NodeScopedStorage.nodeKey(app)
        if (nk == NodeScopedStorage.UNBOUND) return
        if (!p.contains(KEY_FLOW_COMPLETED)) {
            p.edit().putBoolean(KEY_SCOPE_MIGRATED, true).apply()
            return
        }
        val scoped = NodeScopedStorage.scopedStorageKey(KEY_FLOW_COMPLETED, nk)
        if (!p.contains(scoped)) {
            p.edit().putBoolean(scoped, p.getBoolean(KEY_FLOW_COMPLETED, false)).apply()
        }
        p.edit().remove(KEY_FLOW_COMPLETED).putBoolean(KEY_SCOPE_MIGRATED, true).apply()
    }

    fun isFirstLaunchFlowCompleted(context: Context): Boolean {
        val ctx = context.applicationContext
        migrateFlatIfNeeded(context)
        val nk = NodeScopedStorage.nodeKey(ctx)
        if (nk != NodeScopedStorage.UNBOUND) {
            return rawPrefs(ctx).getBoolean(NodeScopedStorage.scopedKey(ctx, KEY_FLOW_COMPLETED), false)
        }
        return rawPrefs(ctx).getBoolean(KEY_FLOW_COMPLETED, false)
    }

    fun markFirstLaunchFlowCompleted(context: Context) {
        val ctx = context.applicationContext
        val nk = NodeScopedStorage.nodeKey(ctx)
        val p = rawPrefs(ctx)
        if (nk == NodeScopedStorage.UNBOUND) {
            p.edit().putBoolean(KEY_FLOW_COMPLETED, true).apply()
            return
        }
        migrateFlatIfNeeded(context)
        p.edit().putBoolean(NodeScopedStorage.scopedKey(ctx, KEY_FLOW_COMPLETED), true).apply()
    }
}
