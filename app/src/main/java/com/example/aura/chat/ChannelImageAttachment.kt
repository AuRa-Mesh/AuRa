package com.example.aura.chat

/**
 * Собранное из чанков AUR1 изображение для UI (не хранится в Room).
 */
data class ChannelImageAttachment(
    val stableId: String,
    val from: UInt?,
    val jpeg: ByteArray,
    val mine: Boolean,
    val timeMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChannelImageAttachment) return false
        return stableId == other.stableId &&
            from == other.from &&
            mine == other.mine &&
            timeMs == other.timeMs &&
            jpeg.contentEquals(other.jpeg)
    }

    override fun hashCode(): Int {
        var r = stableId.hashCode()
        r = 31 * r + (from?.hashCode() ?: 0)
        r = 31 * r + mine.hashCode()
        r = 31 * r + timeMs.hashCode()
        r = 31 * r + jpeg.contentHashCode()
        return r
    }
}
