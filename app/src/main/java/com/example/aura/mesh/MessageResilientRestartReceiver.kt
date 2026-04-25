package com.example.aura.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "MsgResilientRestart"

/**
 * [AlarmManager] / отложенный перезапуск [MessageResilientService] после остановки процесса.
 */
class MessageResilientRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive ${intent?.action}")
        MessageResilientService.startIfNeeded(context.applicationContext)
    }
}
