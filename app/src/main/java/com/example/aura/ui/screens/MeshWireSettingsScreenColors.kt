package com.example.aura.ui.screens

import androidx.compose.ui.graphics.Color

/**
 * Единая палитра вложенных экранов настроек (как [LoRaSettingsContent]).
 */
object MeshWireSettingsScreenColors {
    /**
     * Основной фон контента главных вкладок (список каналов, узлы, карта, настройки) —
     * как фон экрана диалога в чате mesh.
     */
    val mainTabBackground = Color.Transparent

    val bg = Color(0xFF121212)
    val card = Color(0xFF1E1E1E)
    val text = Color(0xFFFFFFFF)
    val muted = Color(0xFFB0B0B0)
    val accent = Color(0xFF67EA94)
    val dividerInCard = Color(0xFF2C2C2C)
    val dividerOuter = Color(0xFF333333)
    val cancelBtn = Color(0xFF2C2C2C)
    val onAccent = Color(0xFF1A1A1A)
    val switchThumbUnchecked = Color(0xFF6B7280)
    val switchTrackUnchecked = Color(0xFF3D4555)

    /** Бейджи / вторичные акценты (каналы). */
    val secondaryLock = Color(0xFFE8C24A)
}
