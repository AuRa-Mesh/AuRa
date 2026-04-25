package com.example.aura.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * После загрузки устройства восстанавливаем foreground-службу при полной BLE-сессии.
 */
class MessageResilientBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        MessageResilientService.startIfNeeded(context.applicationContext)
    }
}
