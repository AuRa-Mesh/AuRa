package com.example.aura.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * После успешной записи конфигурации на ноду: переход на вкладку «Настройки» и блокировка
 * входа в подэкраны до стабильного BLE (см. [com.example.aura.ui.screens.ChatScreen]).
 */
val LocalNotifyNodeConfigWrite = compositionLocalOf<(() -> Unit)?> { null }
