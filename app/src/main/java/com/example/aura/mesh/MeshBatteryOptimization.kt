package com.example.aura.mesh

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

private const val TAG = "MeshBatteryOpt"
private const val PREFS = "aura_mesh_prefs"
private const val KEY_BATTERY_PROMPTED = "battery_ignore_prompted"

/**
 * Однократный запрос исключения из агрессивной оптимизации батареи (Doze),
 * чтобы фоновый BLE и передача данных не обрывались.
 */
object MeshBatteryOptimization {

    fun maybeRequestOnce(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val ctx = activity.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_PROMPTED, false)) return
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val pkg = ctx.packageName
        if (pm.isIgnoringBatteryOptimizations(pkg)) {
            prefs.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply()
            return
        }
        prefs.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply()
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                activity.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "battery optimization intent", e)
        }
    }
}
