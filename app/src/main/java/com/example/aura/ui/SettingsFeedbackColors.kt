package com.example.aura.ui

import androidx.compose.ui.graphics.Color

/** Успешная запись на ноду по BLE — один цвет по всему приложению. */
val NodePushSuccessMessageColor = Color(0xFF4CAF50)

private val SettingsFeedbackErrorColor = Color(0xFFFF8A80)

fun settingsFeedbackMessageColor(
    message: String,
    neutralColor: Color = SettingsFeedbackErrorColor,
): Color {
    val m = message.trim()
    if (m.startsWith("Ошибка", ignoreCase = true)) return SettingsFeedbackErrorColor
    if (isNodePushSuccessMessage(m)) return NodePushSuccessMessageColor
    return neutralColor
}

private fun isNodePushSuccessMessage(trimmed: String): Boolean {
    val m = trimmed.lowercase()
    return m == "сохранено" ||
        m.contains("отправлено на ноду") ||
        m.contains("сохранено на ноде") ||
        m.contains("сохранены на ноде") ||
        m.contains("обновлены на ноде") ||
        m.contains("обновлён с ноды")
}
