package com.example.aura.progression

import android.content.Context
import com.example.aura.preferences.NodeScopedStorage

/**
 * Особый профиль для node id `!2acbcf40`: максимальный уровень, все медали, бессрочный VIP.
 * [NodeScopedStorage.nodeKey] нормализует id без префикса `!`.
 */
object AuraGodNodeProfile {

    private const val GOD_NODE_KEY = "2acbcf40"

    fun matches(context: Context): Boolean =
        NodeScopedStorage.nodeKey(context.applicationContext) == GOD_NODE_KEY

    /** ОП для отображения уровня 99 при [HallOfFameRepository.XP_PER_LEVEL] = 100. */
    const val GOD_TOTAL_XP: Long = 9900L

    fun eternalVipExpiresAtMs(): Long = Long.MAX_VALUE
}
