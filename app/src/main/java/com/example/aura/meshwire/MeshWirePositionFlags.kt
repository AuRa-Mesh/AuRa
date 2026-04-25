package com.example.aura.meshwire

/**
 * [Config.PositionConfig.PositionFlags] (config.proto) — биты для UI как BitwisePreference в типичном mesh-клиенте.
 */
enum class MeshWirePositionFlags(val mask: UInt, val labelRu: String) {
    ALTITUDE(0x0001u, "Высота"),
    ALTITUDE_MSL(0x0002u, "Высота MSL"),
    GEOIDAL_SEPARATION(0x0004u, "Геоидальное разделение"),
    DOP(0x0008u, "DOP"),
    HVDOP(0x0010u, "Отдельно HDOP/VDOP"),
    SATINVIEW(0x0020u, "Спутники в зоне видимости"),
    SEQ_NO(0x0040u, "Номер последовательности"),
    TIMESTAMP(0x0080u, "Метка времени позиции"),
    HEADING(0x0100u, "Курс"),
    SPEED(0x0200u, "Скорость"),
    ;

    companion object {
        val ALL: List<MeshWirePositionFlags> = entries

        fun withToggled(flags: UInt, bit: MeshWirePositionFlags, on: Boolean): UInt =
            if (on) flags or bit.mask else flags and bit.mask.inv()
    }
}
