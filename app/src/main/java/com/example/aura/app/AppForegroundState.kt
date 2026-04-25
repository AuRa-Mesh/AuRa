package com.example.aura.app

import java.util.concurrent.atomic.AtomicBoolean

/** Обновляется через [androidx.lifecycle.ProcessLifecycleOwner] в [com.example.aura.AuraApplication]. */
object AppForegroundState {
    private val fg = AtomicBoolean(true)

    fun setForeground(v: Boolean) {
        fg.set(v)
    }

    val isForeground: Boolean get() = fg.get()
}
