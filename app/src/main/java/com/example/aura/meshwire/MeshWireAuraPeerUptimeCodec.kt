package com.example.aura.meshwire

/**
 * Лёгкий protobuf-payload для обмена «аптаймом приложения» между Aura:
 * поле 1 — `fixed32` node id (32-bit mesh node num),
 * поле 2 — `int64` uptime в секундах.
 *
 * Portnum не из официального enum mesh — только между клиентами Aura.
 */
object MeshWireAuraPeerUptimeCodec {

    /** Зарезервированный порт приложения (вне core Portnums прошивки). */
    const val PORTNUM: Int = 502

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            shift += 7
            if ((b and 0x80) == 0) break
        }
        return result to (i - start)
    }

    fun parsePayload(dataPayload: ByteArray): Pair<UInt, Long>? {
        if (dataPayload.isEmpty()) return null
        var node: UInt? = null
        var uptime: Long? = null
        var i = 0
        while (i < dataPayload.size) {
            val tag = dataPayload[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(dataPayload, i)
                    i += n
                    if (fieldNum == 2) uptime = v
                }
                5 -> {
                    if (i + 4 > dataPayload.size) return null
                    val v = (
                        (dataPayload[i].toInt() and 0xFF) or
                            ((dataPayload[i + 1].toInt() and 0xFF) shl 8) or
                            ((dataPayload[i + 2].toInt() and 0xFF) shl 16) or
                            ((dataPayload[i + 3].toInt() and 0xFF) shl 24)
                        ).toUInt()
                    i += 4
                    if (fieldNum == 1) node = v
                }
                2 -> {
                    val (len, lb) = readVarint(dataPayload, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > dataPayload.size) return null
                    i += ln
                }
                1 -> i = minOf(i + 8, dataPayload.size)
                else -> return null
            }
        }
        val n = node ?: return null
        val u = uptime ?: return null
        return n to u
    }
}
