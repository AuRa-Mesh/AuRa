package com.example.aura.meshwire

/**
 * Минимальный разбор [Routing] из поля Data.payload (portnum ROUTING_APP).
 * Нужен enum [Routing.error_reason] — поле 3 (varint).
 */
object MeshWireRoutingPayloadParser {

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

    /**
     * Возвращает числовой код [Routing.Error] или null, если error_reason не найден.
     */
    fun parseErrorReason(routingPayload: ByteArray): Int? {
        if (routingPayload.isEmpty()) return null
        var i = 0
        while (i < routingPayload.size) {
            val tag = routingPayload[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(routingPayload, i)
                    i += n
                    if (fieldNum == 3) return v.toInt()
                }
                1 -> i = minOf(i + 8, routingPayload.size)
                2 -> {
                    val (len, lb) = readVarint(routingPayload, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > routingPayload.size) return null
                    i += ln
                }
                5 -> i = minOf(i + 4, routingPayload.size)
                else -> return null
            }
        }
        return null
    }
}
