package com.example.aura.progression

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Упаковка счётчиков медалей Аллеи славы для VIP-broadcast (503) и mesh recovery (504).
 * Фиксированный порядок — обход [HallOfFameCatalog.CATEGORIES] и медалей внутри категории (всего 20).
 */
object HallOfFameWireStats {

    const val MEDAL_COUNT: Int = 20

    /** 20 × uint64 little-endian. */
    const val PACKED_BYTE_LEN: Int = MEDAL_COUNT * 8

    val ORDERED_STAT_KEYS: List<String> = HallOfFameCatalog.CATEGORIES.flatMap { cat ->
        cat.medals.map { it.statKey }
    }.also { require(it.size == MEDAL_COUNT) { "HoF wire order: expected $MEDAL_COUNT stats" } }

    fun packFromValues(values: LongArray): ByteArray {
        require(values.size == MEDAL_COUNT)
        val buf = ByteBuffer.allocate(PACKED_BYTE_LEN).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until MEDAL_COUNT) {
            buf.putLong(values[i].coerceAtLeast(0L))
        }
        return buf.array()
    }

    fun unpackToLongArray(packed: ByteArray): LongArray? {
        if (packed.size != PACKED_BYTE_LEN) return null
        val buf = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        val out = LongArray(MEDAL_COUNT)
        buf.get(out)
        return out
    }
}
