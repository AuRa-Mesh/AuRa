package com.example.aura.meshwire

/**
 * Извлекает [my_node_num] из кадра [FromRadio] (поле [my_info], внутри него varint поле 1).
 * Логика совпадает с [com.example.aura.bluetooth.MeshGattClient] (GATT).
 */
object MeshWireFromRadioMyNodeNumParser {

    fun parseMyNodeNum(bytes: ByteArray): Long? {
        var i = 0
        while (i < bytes.size) {
            val tagByte = bytes[i++].toInt() and 0xFF
            val fieldNum = tagByte ushr 3
            val wireType = tagByte and 0x07
            when (wireType) {
                0 -> {
                    while (i < bytes.size && (bytes[i++].toInt() and 0x80) != 0) {
                    }
                }
                1 -> i += 8
                5 -> i += 4
                2 -> {
                    val (len, lenBytes) = readVarint(bytes, i)
                    i += lenBytes
                    if (len <= 0 || i + len > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + len.toInt())
                    i += len.toInt()
                    if (fieldNum == 3) return readNodeNumFromMyInfo(sub)
                }
                else -> break
            }
        }
        return null
    }

    private fun readNodeNumFromMyInfo(bytes: ByteArray): Long? {
        var i = 0
        while (i < bytes.size) {
            val tagByte = bytes[i++].toInt() and 0xFF
            val fieldNum = tagByte ushr 3
            val wireType = tagByte and 0x07
            if (fieldNum == 1 && wireType == 0) return readVarint(bytes, i).first
            when (wireType) {
                0 -> {
                    while (i < bytes.size && (bytes[i++].toInt() and 0x80) != 0) {
                    }
                }
                1 -> i += 8
                5 -> i += 4
                2 -> {
                    val (len, lenBytes) = readVarint(bytes, i)
                    i += lenBytes + len.toInt()
                }
                else -> break
            }
        }
        return null
    }

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
}
