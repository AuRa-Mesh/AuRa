package com.example.aura.vip

import android.content.Context
import com.example.aura.preferences.VipAccessPreferences

/**
 * Устаревший невидимый суффикс в конце текста (совместимость со старыми сообщениями в БД).
 * VIP других узлов передаётся только через расширение protobuf `User` в NODEINFO_APP.
 */
object VipWireMarker {

    /** Пять невидимых Unicode-разделителей (U+2062 / U+2063). 15 байт в UTF-8. */
    const val SUFFIX: String = "\u2063\u2062\u2063\u2062\u2063"

    /**
     * Рамка у **нашего** аватара — пока действует VIP по времени
     * ([VipAccessPreferences.isVipTimerActive]).
     */
    fun isSelfVipActive(context: Context): Boolean =
        VipAccessPreferences.isVipTimerActive(context)

    /** Убрать устаревший суффикс: (очищенный_текст, был_ли_суффикс). */
    fun parseAndStrip(text: String): Pair<String, Boolean> {
        if (text.isEmpty()) return text to false
        if (!text.endsWith(SUFFIX)) return text to false
        return text.removeSuffix(SUFFIX) to true
    }

    /** Есть ли в конце текста устаревший суффикс (без удаления). */
    fun containsMarker(text: String): Boolean = text.endsWith(SUFFIX)
}
