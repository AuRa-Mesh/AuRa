package com.example.aura.ui.vip

import android.content.Context
import android.widget.Toast

/**
 * Единая «пилюля» с текстом «Работает только в VIP тарифе», которую показываем, когда пользователь
 * нажимает на элементы UI, отключённые в ограниченном режиме (истёк VIP-таймер):
 *   • вкладку «Карта»;
 *   • иконку вложений («скрепка») и пункты её меню;
 *   • голосовую кнопку в чатах;
 *   • клик по beacon-share-ссылкам в пузырях;
 *   • клики по меткам и прочие VIP-only действия.
 *
 * Реализация — обычный [Toast]; одно и то же сообщение на все точки входа, чтобы пользователь быстро
 * запомнил причину «ничего не произошло».
 */
object VipRestrictedToast {

    private const val MESSAGE: String = "Работает только в VIP тарифе"

    @Volatile
    private var lastShownAtMs: Long = 0L

    /** Минимальный интервал между двумя Toast-ами, чтобы не заикаться при повторных нажатиях. */
    private const val MIN_INTERVAL_MS: Long = 800L

    /**
     * Показывает «пузырь» о VIP-ограничении. Повторные вызовы в течение [MIN_INTERVAL_MS]
     * игнорируются — защита от спама при быстрых кликах.
     */
    fun show(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastShownAtMs < MIN_INTERVAL_MS) return
        lastShownAtMs = now
        Toast.makeText(context.applicationContext, MESSAGE, Toast.LENGTH_SHORT).show()
    }
}
