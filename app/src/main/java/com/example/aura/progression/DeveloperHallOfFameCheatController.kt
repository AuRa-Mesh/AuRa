package com.example.aura.progression

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Скрытый режим: после пароля в диалоге — тап по медали в «Аллее славы» зачитывает её на максимум. */
object DeveloperHallOfFameCheatController {

    const val DEV_PASSWORD: String = "5288"

    private val _hofCheatUnlocked = MutableStateFlow(false)
    val hofCheatUnlocked: StateFlow<Boolean> = _hofCheatUnlocked.asStateFlow()

    fun setHallOfFameCheatUnlocked(value: Boolean) {
        _hofCheatUnlocked.value = value
    }
}
