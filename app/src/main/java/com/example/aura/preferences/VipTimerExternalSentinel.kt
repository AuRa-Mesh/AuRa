package com.example.aura.preferences

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * L3-слой персистентности VIP-таймера: небольшой PNG-файл в публичной медиа-библиотеке
 * `Pictures/Aura/`, переживающий переустановку приложения.
 *
 * Почему PNG, а не `.bin`:
 *  - На API 30+ (Android 11) действует scoped storage. Не-медийные файлы в `Documents/…`
 *    после удаления приложения осиротевают (`owner_package_name` обнуляется), и новая
 *    установка их не видит без `MANAGE_EXTERNAL_STORAGE` (спец-разрешение, Google Play
 *    сильно ограничивает).
 *  - Медийные файлы (image/audio/video) после переустановки видны через `MediaStore.Images`
 *    при наличии `READ_MEDIA_IMAGES` (API 33+) или `READ_EXTERNAL_STORAGE` (API ≤ 32) —
 *    оба уже объявлены в манифесте приложения. Поэтому полезную нагрузку мы маскируем
 *    в валидный 1×1 PNG (через `tEXt`-чанк, см. [VipStegoPngCodec]).
 *
 * Слои:
 *  - **L1** — `SharedPreferences vip_access_prefs` (см. [VipAccessPreferences]), бэкапится Auto Backup.
 *  - **L2** — Android Auto Backup / device transfer (см. `backup_rules.xml`, `data_extraction_rules.xml`).
 *  - **L3 (этот файл)** — внешний PNG в `Pictures/Aura/`, имя файла зависит от mesh node id
 *    (см. [NodeScopedStorage.nodeKey]); для старых установок дополнительно читается
 *    `.aura_vip_sentinel.png`.
 *
 * Формат полезной нагрузки (53 байта, big-endian):
 *  [0..3]   magic "AVT1"
 *  [4]      version (=1)
 *  [5..12]  deadlineMs (Long)
 *  [13..20] seedMs (Long) — момент самого первого seed'а на этом устройстве
 *  [21..52] HMAC-SHA256(secret, байты [0..20])
 *
 * HMAC здесь — не защита от реверса, а гарантия от тривиальной подмены/обрезки файла.
 */
internal object VipTimerExternalSentinel {
    private const val TAG = "VipTimerSentinel"

    // Историческое имя PNG (до привязки к node id) — только чтение / миграция.
    private const val LEGACY_SENTINEL_PNG: String = ".aura_vip_sentinel.png"
    private const val RELATIVE_DIR: String = "Pictures/Aura"
    private const val MIME: String = "image/png"

    // Историческое место — .bin в Documents. Читаем при миграции, больше не пишем.
    private const val LEGACY_FILE_NAME: String = ".aura_vip_sentinel.bin"
    private const val LEGACY_RELATIVE_DIR: String = "Documents/Aura"

    private const val MAGIC_0: Byte = 'A'.code.toByte()
    private const val MAGIC_1: Byte = 'V'.code.toByte()
    private const val MAGIC_2: Byte = 'T'.code.toByte()
    private const val MAGIC_3: Byte = '1'.code.toByte()
    private const val VERSION: Byte = 0x01
    private const val HEADER_LEN = 21 // magic+ver+deadline+seed
    private const val HMAC_LEN = 32
    private const val TOTAL_LEN = HEADER_LEN + HMAC_LEN // 53

    private fun sentinelDisplayName(context: Context): String {
        val nk = NodeScopedStorage.nodeKey(context)
        return ".aura_vip_sentinel__${nk}.png"
    }

    private val HMAC_SECRET: ByteArray = byteArrayOf(
        0x41.toByte(), 0x75.toByte(), 0x52.toByte(), 0x75.toByte(),
        0x53.toByte(), 0x2D.toByte(), 0x56.toByte(), 0x49.toByte(),
        0x50.toByte(), 0x2D.toByte(), 0x53.toByte(), 0x45.toByte(),
        0x4E.toByte(), 0x54.toByte(), 0x49.toByte(), 0x4E.toByte(),
        0x45.toByte(), 0x4C.toByte(), 0x2D.toByte(), 0x76.toByte(),
        0x31.toByte(), 0x00.toByte(), 0x7E.toByte(), 0x5C.toByte(),
        0x3A.toByte(), 0x11.toByte(), 0xA5.toByte(), 0xC0.toByte(),
        0xDE.toByte(), 0x42.toByte(), 0x4D.toByte(), 0x11.toByte(),
    )

    data class Payload(val deadlineMs: Long, val seedMs: Long)

    fun read(context: Context): Payload? = try {
        readImpl(context)
    } catch (t: Throwable) {
        Log.w(TAG, "read failed: ${t.message}")
        null
    }

    fun write(context: Context, payload: Payload) {
        try {
            writeImpl(context, payload)
        } catch (t: Throwable) {
            Log.w(TAG, "write failed: ${t.message}")
        }
    }

    // ──────────────────────────────── impl ────────────────────────────────

    private fun readImpl(context: Context): Payload? {
        val ctx = context.applicationContext
        // 1. Основной путь: PNG в Pictures/Aura/.
        readCurrent(ctx)?.let { return decode(it) }
        // 2. Миграция со старого .bin в Documents/Aura/: если успели прочитать — перепишем PNG.
        val legacy = readLegacy(ctx) ?: return null
        val payload = decode(legacy) ?: return null
        try {
            writeCurrent(ctx, legacy)
        } catch (t: Throwable) {
            Log.w(TAG, "legacy → png migration write failed: ${t.message}")
        }
        return payload
    }

    private fun writeImpl(context: Context, payload: Payload) {
        val ctx = context.applicationContext
        writeCurrent(ctx, encode(payload))
    }

    // — Current: MediaStore images in Pictures/Aura/ ———————————————————————————

    private fun readCurrent(context: Context): ByteArray? {
        readPngBytes(context, sentinelDisplayName(context))?.let { return it }
        readPngBytes(context, LEGACY_SENTINEL_PNG)?.let { return it }
        return null
    }

    private fun readPngBytes(context: Context, displayName: String): ByteArray? {
        val uri = findImageUri(context, displayName) ?: return null
        val pngBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        return VipStegoPngCodec.unwrap(pngBytes)
    }

    private fun writeCurrent(context: Context, rawPayload: ByteArray) {
        val displayName = sentinelDisplayName(context)
        val cr = context.contentResolver
        val pngBytes = VipStegoPngCodec.wrap(rawPayload)
        val existing = findImageUri(context, displayName)
        val uri: Uri = if (existing != null) {
            existing
        } else {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            cr.insert(collection, values) ?: return
        }
        cr.openOutputStream(uri, "wt")?.use { it.write(pngBytes); it.flush() } ?: return
        if (existing == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            cr.update(uri, done, null, null)
        }
    }

    private fun findImageUri(context: Context, displayName: String): Uri? {
        val cr = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            args = arrayOf(displayName, "$RELATIVE_DIR/%")
        } else {
            // На API ≤ 28 RELATIVE_PATH нет — фильтруем только по имени, ожидая одну запись.
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            args = arrayOf(displayName)
        }
        cr.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }

    // — Legacy: MediaStore files in Documents/Aura/ + прямой File I/O ————————————

    private fun readLegacy(context: Context): ByteArray? {
        // MediaStore «Files» для старого .bin (API 29+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cr = context.contentResolver
            val files = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val args = arrayOf(LEGACY_FILE_NAME, "$LEGACY_RELATIVE_DIR/%")
            cr.query(files, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    val uri = Uri.withAppendedPath(files, id.toString())
                    cr.openInputStream(uri)?.use { input ->
                        val buf = ByteArray(TOTAL_LEN)
                        var read = 0
                        while (read < TOTAL_LEN) {
                            val n = input.read(buf, read, TOTAL_LEN - read)
                            if (n <= 0) break
                            read += n
                        }
                        if (read == TOTAL_LEN) return buf
                    }
                }
            }
        }
        // Прямой File — работает на API ≤ 28 и в краевых случаях на 29+ с legacy external storage.
        @Suppress("DEPRECATION")
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val f = File(File(docs, "Aura"), LEGACY_FILE_NAME)
        if (f.exists() && f.length() == TOTAL_LEN.toLong()) {
            return try { f.readBytes() } catch (t: Throwable) { null }
        }
        return null
    }

    // — codec ————————————————————————————————————————————————————————————

    private fun encode(payload: Payload): ByteArray {
        val buf = ByteBuffer.allocate(TOTAL_LEN)
        buf.put(MAGIC_0).put(MAGIC_1).put(MAGIC_2).put(MAGIC_3)
        buf.put(VERSION)
        buf.putLong(payload.deadlineMs)
        buf.putLong(payload.seedMs)
        val header = buf.array().copyOfRange(0, HEADER_LEN)
        val sig = hmacSha256(HMAC_SECRET, header)
        buf.put(sig)
        return buf.array()
    }

    private fun decode(blob: ByteArray): Payload? {
        if (blob.size != TOTAL_LEN) return null
        if (blob[0] != MAGIC_0 || blob[1] != MAGIC_1 || blob[2] != MAGIC_2 || blob[3] != MAGIC_3) return null
        if (blob[4] != VERSION) return null
        val header = blob.copyOfRange(0, HEADER_LEN)
        val sig = blob.copyOfRange(HEADER_LEN, TOTAL_LEN)
        val expected = hmacSha256(HMAC_SECRET, header)
        if (!expected.contentEquals(sig)) return null
        val bb = ByteBuffer.wrap(blob, 5, 16)
        val deadline = bb.long
        val seed = bb.long
        // deadline ≤ 0 в старых sentinel — трактуем как «нет активного дедлайна»; миграция в [ensureInitialTimerSeeded].
        if (deadline < 0L || seed <= 0L) return null
        return Payload(deadlineMs = deadline, seedMs = seed)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
