package com.example.aura.preferences

import android.content.Context
import android.net.Uri
import com.example.aura.progression.AuraProgressCounters
import java.io.File

/** Локальный аватар профиля: копия файла во внутреннем хранилище и путь в SharedPreferences. */
object ProfileLocalAvatarStore {
    private const val PREFS = "aura_profile_local_avatar"
    private const val KEY_PATH = "file_path"
    private const val FILE_NAME = "local_profile_avatar.bin"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadPath(context: Context): String? {
        val raw = prefs(context).getString(KEY_PATH, null)?.trim().orEmpty().ifBlank { return null }
        return if (File(raw).isFile) raw else {
            prefs(context).edit().remove(KEY_PATH).apply()
            null
        }
    }

    /** Удаляет сохранённый аватар с диска и из настроек, возвращая профиль к виду по умолчанию. */
    fun clear(context: Context) {
        val appCtx = context.applicationContext
        prefs(appCtx).edit().remove(KEY_PATH).apply()
        File(appCtx.filesDir, FILE_NAME).takeIf { it.exists() }?.delete()
    }

    /** Копирует изображение из URI во внутренний файл и сохраняет путь. Возвращает абсолютный путь или null. */
    fun copyFromUriAndPersist(context: Context, uri: Uri): String? {
        return try {
            val appCtx = context.applicationContext
            val out = File(appCtx.filesDir, FILE_NAME)
            appCtx.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (!out.exists() || out.length() == 0L) return null
            val path = out.absolutePath
            prefs(context).edit().putString(KEY_PATH, path).apply()
            AuraProgressCounters.markAvatarSet(appCtx)
            path
        } catch (_: Throwable) {
            null
        }
    }
}
