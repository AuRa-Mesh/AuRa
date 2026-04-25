package com.example.aura.mesh.nodedb

import android.content.Context
import android.net.Uri
import com.example.aura.meshwire.MeshWireNodeSummary
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Локальные аватары узлов: свой файл ([saveCustomAvatarFromUri], в т.ч. GIF) или сгенерированный PNG по [nodeIdHex].
 * Кастом и сгенерированный кэш привязаны к id, не к номеру узла.
 */
object NodeAvatarStore {

    private const val DIR = "node_avatars"
    /** Префикс v2: раньше PNG строился и по nodeNum — старые файлы `gen_` не используются. */
    private const val PREFIX_GEN = "gen_id_"
    private const val PREFIX_CST = "cst_"
    private const val MAX_CUSTOM_BYTES = 8L * 1024 * 1024

    private val locks = ConcurrentHashMap<String, Any>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun invalidate() {
        _revision.value = _revision.value + 1
    }

    private fun lock(key: String): Any = locks.computeIfAbsent(key) { Any() }

    fun sanitizeKey(nodeIdHex: String): String =
        nodeIdHex.trim().removePrefix("!").lowercase(Locale.ROOT).ifEmpty { "unknown" }

    private fun baseDir(ctx: Context): File =
        File(ctx.filesDir, DIR).also { it.mkdirs() }

    private fun generatedFile(ctx: Context, key: String): File =
        File(baseDir(ctx), "$PREFIX_GEN$key.png")

    /** Текущий кастомный файл (без расширения, чтобы сохранять GIF как есть). */
    private fun customFilePrimary(ctx: Context, key: String): File =
        File(baseDir(ctx), "$PREFIX_CST$key")

    /** Раньше сохраняли как .jpg — учитываем при чтении и удалении. */
    private fun customFileLegacyJpg(ctx: Context, key: String): File =
        File(baseDir(ctx), "$PREFIX_CST$key.jpg")

    private fun resolveCustomFile(ctx: Context, key: String): File? {
        val a = customFilePrimary(ctx, key)
        if (a.isFile && a.length() > 0L) return a
        val b = customFileLegacyJpg(ctx, key)
        if (b.isFile && b.length() > 0L) return b
        return null
    }

    fun hasCustomAvatar(ctx: Context, nodeIdHex: String): Boolean =
        resolveCustomFile(ctx, sanitizeKey(nodeIdHex)) != null

    fun clearCustomAvatar(ctx: Context, nodeIdHex: String) {
        val key = sanitizeKey(nodeIdHex)
        synchronized(lock(key)) {
            customFilePrimary(ctx, key).delete()
            customFileLegacyJpg(ctx, key).delete()
        }
        invalidate()
    }

    /**
     * Файл для отображения (Coil): кастомный как есть или сгенерированный PNG по id узла.
     */
    fun ensureAvatarDisplayFile(ctx: Context, nodeIdHex: String): File {
        val key = sanitizeKey(nodeIdHex)
        synchronized(lock(key)) {
            resolveCustomFile(ctx, key)?.let { return it }
            val gen = generatedFile(ctx, key)
            if (gen.isFile && gen.length() > 0L) return gen
            val bmp = NodeAvatarGenerator.render(nodeIdHex)
            try {
                FileOutputStream(gen).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 92, out) }
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
            return gen
        }
    }

    suspend fun saveCustomAvatarFromUri(ctx: Context, nodeIdHex: String, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val key = sanitizeKey(nodeIdHex)
            synchronized(lock(key)) {
                try {
                    val out = customFilePrimary(ctx, key)
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output, bufferSize = 8192) }
                    } ?: return@synchronized false
                    if (!out.exists() || out.length() == 0L) {
                        out.delete()
                        return@synchronized false
                    }
                    if (out.length() > MAX_CUSTOM_BYTES) {
                        out.delete()
                        return@synchronized false
                    }
                    customFileLegacyJpg(ctx, key).delete()
                    invalidate()
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

    fun bitmapKey(node: MeshWireNodeSummary): String =
        sanitizeKey(node.nodeIdHex)
}
