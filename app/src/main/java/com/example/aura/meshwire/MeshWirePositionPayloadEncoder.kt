package com.example.aura.meshwire

/**
 * `Position` (mesh.proto) для `Data` с portnum [POSITION_APP](3).
 * [location_source] = LOC_EXTERNAL — координаты с телефона (EUD), как в типичном mesh-клиенте Android.
 */
object MeshWirePositionPayloadEncoder {

    /** [Position.LocSource.LOC_EXTERNAL] */
    private const val LOC_EXTERNAL = 3

    /**
     * @param timeEpochSec Unix seconds (для поля [Position.time] fixed32).
     */
    fun encodePosition(
        latitudeDeg: Double,
        longitudeDeg: Double,
        altitudeMeters: Int?,
        timeEpochSec: UInt,
    ): ByteArray =
        MeshWireProtobufWriter().apply {
            val latI = (latitudeDeg * 1e7).toInt()
            val lonI = (longitudeDeg * 1e7).toInt()
            writeFixed32Field(1, latI.toUInt())
            writeFixed32Field(2, lonI.toUInt())
            altitudeMeters?.let { writeInt32Field(3, it) }
            writeFixed32Field(4, timeEpochSec)
            writeEnumField(5, LOC_EXTERNAL)
        }.toByteArray()
}
