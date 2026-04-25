package com.example.aura.preferences

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Рантайм-разрешение, необходимое для чтения внешнего VIP-sentinel после переустановки.
 *
 * На API 30+ «осиротевшие» после удаления приложения файлы в `Pictures/Aura/` видны новой
 * установке только через `MediaStore.Images` и только при наличии:
 *  - `READ_MEDIA_IMAGES` (API 33+);
 *  - `READ_EXTERNAL_STORAGE` (API ≤ 32) — уже объявлено в `AndroidManifest.xml`.
 *
 * Без разрешения восстановление возможно лишь через Android Auto Backup (L2). Поэтому
 * на «похожей на свежую установку» сессии мы один раз просим это разрешение. Если
 * пользователь отказался — работаем без L3, поведение не деградирует по сравнению с
 * прошлой версией.
 */
internal object VipExternalReadPermission {

    /** Имя нужного разрешения для текущего API-уровня. */
    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** Уже выдано пользователем? */
    fun isGranted(context: Context): Boolean {
        val perm = requiredPermission()
        return ContextCompat.checkSelfPermission(context, perm) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Имеет ли смысл запрашивать разрешение сейчас.
     *
     * Стратегия: запрашиваем только на «свежей» установке (VIP-таймер ещё не засеян),
     * причём не чаще одного раза на установку — даже если пользователь отказался.
     * Это минимизирует навязчивость: постоянные пользователи дополнительных диалогов не увидят.
     */
    fun shouldRequest(context: Context): Boolean {
        if (isGranted(context)) return false
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return false
        val vipPrefs = context.applicationContext
            .getSharedPreferences("vip_access_prefs", Context.MODE_PRIVATE)
        val seeded = vipPrefs.getBoolean("initial_timer_seeded_v1", false)
        // Уже засеяно локально — значит восстанавливать нечего, разрешение можно не трогать.
        return !seeded
    }

    /** Отметить, что запрос уже был (независимо от ответа пользователя). */
    fun markAsked(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED, true)
            .apply()
    }

    private const val PREFS = "vip_external_perm"
    private const val KEY_ASKED = "asked_v1"
}
