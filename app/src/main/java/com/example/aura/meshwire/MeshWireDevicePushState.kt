package com.example.aura.meshwire

/**
 * Снимок [Config.DeviceConfig] (protobuf `meshtastic/config.proto`) для UI и записи через
 * `AdminMessage.set_config` → `Config.device` (oneof `payload_variant`), как в приложении типичном Android mesh-клиенте
 * (`DeviceSettingsFragment` / настройки роли и ретрансляции).
 */
data class MeshWireDevicePushState(
    /** [Config.DeviceConfig.role], enum ordinal в прошивке. */
    val roleWire: Int,
    /** [Config.DeviceConfig.rebroadcast_mode]. */
    val rebroadcastModeWire: Int,
    /** [Config.DeviceConfig.node_info_broadcast_secs] — период рассылки NodeInfo, секунды. */
    val nodeInfoBroadcastSecs: UInt,
    /** [Config.DeviceConfig.double_tap_as_button_press]. */
    val doubleTapAsButtonPress: Boolean = false,
    /** [Config.DeviceConfig.disable_triple_click] — в UI mesh переключатель «тройной клик» инвертирован. */
    val disableTripleClick: Boolean = false,
    /** [Config.DeviceConfig.tzdef]. */
    val tzdef: String = "",
    /** [Config.DeviceConfig.led_heartbeat_disabled] — в UI mesh «сердцебиение LED» = !поле. */
    val ledHeartbeatDisabled: Boolean = false,
    /** [Config.DeviceConfig.button_gpio]. */
    val buttonGpio: UInt = 0u,
    /** [Config.DeviceConfig.buzzer_gpio]. */
    val buzzerGpio: UInt = 0u,
    /** [Config.DeviceConfig.buzzer_mode], wire enum BuzzerMode. */
    val buzzerModeWire: Int = 0,
) {
    companion object {
        fun initial(): MeshWireDevicePushState = MeshWireDevicePushState(
            roleWire = 0,
            rebroadcastModeWire = 0,
            /** Как незаполненное поле в protobuf; прошивка применяет свой дефолт (часто 900 с). */
            nodeInfoBroadcastSecs = 0u,
        )
    }
}

/** Пункты выпадающего списка: wire-ordinal и подпись как в типичном mesh-клиенте (имена enum). */
data class MeshWireDeviceRoleOption(
    val wireOrdinal: Int,
    val apiName: String,
    /** Кратко по комментариям в config.proto (рус.). */
    val descriptionRu: String,
)

object MeshWireDeviceRoleOptions {
    val ALL: List<MeshWireDeviceRoleOption> = listOf(
        MeshWireDeviceRoleOption(0, "CLIENT", "Подключённое приложение или автономное устройство обмена сообщениями (по умолчанию)."),
        MeshWireDeviceRoleOption(1, "CLIENT_MUTE", "Не пересылает пакеты с других устройств."),
        MeshWireDeviceRoleOption(2, "ROUTER", "Инфраструктурная нода: ретрансляция, расширение сети."),
        MeshWireDeviceRoleOption(3, "ROUTER_CLIENT", "ROUTER+CLIENT (устарело в 2.3.15)."),
        MeshWireDeviceRoleOption(4, "REPEATER", "Ретранслятор с минимальными накладными (устарело в 2.7.11)."),
        MeshWireDeviceRoleOption(5, "TRACKER", "Приоритет позиции GPS."),
        MeshWireDeviceRoleOption(6, "SENSOR", "Приоритет телеметрии окружения."),
        MeshWireDeviceRoleOption(7, "TAK", "Оптимизация под ATAK."),
        MeshWireDeviceRoleOption(8, "CLIENT_HIDDEN", "Минимум фоновых передач, ретрансляция в основном локально."),
        MeshWireDeviceRoleOption(9, "LOST_AND_FOUND", "Периодически шлёт текст с координатами на основной канал."),
        MeshWireDeviceRoleOption(10, "TAK_TRACKER", "TAK + автоматические PLI."),
        MeshWireDeviceRoleOption(11, "ROUTER_LATE", "Ретрансляция после остальных маршрутизаторов."),
        MeshWireDeviceRoleOption(12, "CLIENT_BASE", "Базовая станция для распределения трафика от слабых узлов."),
    )

    /** Как в типичном mesh-клиенте: из выпадающего списка убраны роли с `deprecated = true` в proto (3, 4). */
    val DROPDOWN: List<MeshWireDeviceRoleOption> =
        ALL.filter { it.wireOrdinal != MeshWireDeviceConfigLogic.ROLE_ROUTER_CLIENT && it.wireOrdinal != MeshWireDeviceConfigLogic.ROLE_REPEATER }

    fun findByWire(w: Int): MeshWireDeviceRoleOption =
        ALL.firstOrNull { it.wireOrdinal == w } ?: ALL[0]
}

data class MeshWireRebroadcastOption(
    val wireOrdinal: Int,
    val apiName: String,
    val descriptionRu: String,
)

object MeshWireRebroadcastOptions {
    val ALL: List<MeshWireRebroadcastOption> = listOf(
        MeshWireRebroadcastOption(0, "ALL", "Ретранслировать замеченное сообщение, если оно было на нашем частном канале или из другой сетки с теми же параметрами LoRa."),
        MeshWireRebroadcastOption(1, "ALL_SKIP_DECODING", "Как ALL, но без декодирования пакета (часто только для REPEATER)."),
        MeshWireRebroadcastOption(2, "LOCAL_ONLY", "Игнорировать чужие открытые сетки; только локальные primary/secondary каналы."),
        MeshWireRebroadcastOption(3, "KNOWN_ONLY", "Как LOCAL_ONLY, плюс игнорировать nodenum вне известного NodeDB."),
        MeshWireRebroadcastOption(4, "NONE", "Без ретрансляции (для части ролей SENSOR/TRACKER/TAK_TRACKER)."),
        MeshWireRebroadcastOption(5, "CORE_PORTNUMS_ONLY", "Только стандартные portnum: NodeInfo, Text, Position, Telemetry, Routing."),
    )

    fun findByWire(w: Int): MeshWireRebroadcastOption =
        ALL.firstOrNull { it.wireOrdinal == w } ?: ALL[0]
}

/** Пресеты интервала NodeInfo (секунды) — как выбор «15 мин / 1 ч / …» в типичном mesh-клиенте. */
data class MeshWireNodeInfoIntervalOption(
    val seconds: UInt,
    val labelRu: String,
)

object MeshWireNodeInfoIntervalOptions {
    /**
     * [IntervalConfiguration.NODE_INFO_BROADCAST] в типичном mesh-клиенте Android
     * (`FixedUpdateIntervals` / подписи в UI).
     */
    val ALL: List<MeshWireNodeInfoIntervalOption> = listOf(
        MeshWireNodeInfoIntervalOption(0u, "Не задано"),
        MeshWireNodeInfoIntervalOption(10800u, "3 ч"),
        MeshWireNodeInfoIntervalOption(14400u, "4 ч"),
        MeshWireNodeInfoIntervalOption(18000u, "5 ч"),
        MeshWireNodeInfoIntervalOption(21600u, "6 ч"),
        MeshWireNodeInfoIntervalOption(43200u, "12 ч"),
        MeshWireNodeInfoIntervalOption(64800u, "18 ч"),
        MeshWireNodeInfoIntervalOption(86400u, "24 ч"),
        MeshWireNodeInfoIntervalOption(129600u, "36 ч"),
        MeshWireNodeInfoIntervalOption(172800u, "48 ч"),
        MeshWireNodeInfoIntervalOption(259200u, "72 ч"),
    )

    /** Точное совпадение или подпись «N с», если на ноде значение вне списка (как пустое поле в типичном mesh-клиенте). */
    fun findNearest(seconds: UInt): MeshWireNodeInfoIntervalOption {
        ALL.firstOrNull { it.seconds == seconds }?.let { return it }
        return MeshWireNodeInfoIntervalOption(seconds, "${seconds} с")
    }
}
