package com.example.aura.meshwire

/**
 * Константы [Config.DeviceConfig](https://github.com/meshtastic/protobufs/blob/master/meshtastic/config.proto).
 *
 * В приложении типичном Android mesh-клиенте для экрана «Устройство» список режимов ретрансляции — полный enum
 * ([DropDownPreference] по `RebroadcastMode`), без подмены значения с ноды по роли; прошивка сама
 * трактует недопустимые комбинации (см. комментарии в config.proto).
 */
object MeshWireDeviceConfigLogic {

    /** Wire-ordinal [Config.DeviceConfig.Role]. */
    const val ROLE_CLIENT = 0
    const val ROLE_CLIENT_MUTE = 1
    const val ROLE_ROUTER = 2
    const val ROLE_ROUTER_CLIENT = 3
    const val ROLE_REPEATER = 4
    const val ROLE_TRACKER = 5
    const val ROLE_SENSOR = 6
    const val ROLE_TAK = 7
    const val ROLE_CLIENT_HIDDEN = 8
    const val ROLE_LOST_AND_FOUND = 9
    const val ROLE_TAK_TRACKER = 10
    const val ROLE_ROUTER_LATE = 11
    const val ROLE_CLIENT_BASE = 12

    /** Wire-ordinal [Config.DeviceConfig.RebroadcastMode]. */
    const val RB_ALL = 0
    const val RB_ALL_SKIP_DECODING = 1
    const val RB_LOCAL_ONLY = 2
    const val RB_KNOWN_ONLY = 3
    const val RB_NONE = 4
    const val RB_CORE_PORTNUMS_ONLY = 5

    /** Роли инфраструктуры: перед переключением mesh показывает диалог подтверждения. */
    fun isInfrastructureRoleWire(wire: Int): Boolean =
        wire == ROLE_ROUTER || wire == ROLE_REPEATER || wire == ROLE_ROUTER_LATE

    fun clampDeviceState(state: MeshWireDevicePushState): MeshWireDevicePushState = state
}
