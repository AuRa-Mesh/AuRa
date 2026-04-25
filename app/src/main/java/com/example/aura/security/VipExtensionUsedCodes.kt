package com.example.aura.security

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.aura.preferences.VipStegoPngCodec
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Реестр уже использованных VIP-кодов продления — гарантия одноразовости кода.
 *
 * Два слоя:
 *  - **L1**: [android.content.SharedPreferences] `vip_extension_used_codes`, ключ — `Set<String>`.
 *    Бэкапится Android Auto Backup (см. `backup_rules.xml`).
 *  - **L2**: внешний PNG-файл `Pictures/Aura/.aura_vip_used.png` с HMAC-подписью (та же техника,
 *    что и у [com.example.aura.preferences.VipTimerExternalSentinel]): полезная нагрузка
 *    упакована в `tEXt`-чанк валидного 1×1 PNG, поэтому после удаления приложения
 *    переустановленная копия видит файл через `MediaStore.Images` по уже объявленным
 *    разрешениям `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`.
 *
 * При [isUsed] проверяются оба слоя (использован = отклик из любого). При [markUsed]
 * значение записывается в оба, причём L2 — best-effort (тихий no-op при отказе MediaStore/IO).
 *
 * Для совместимости с более ранними установками (до миграции на PNG) дополнительно
 * читается старый .bin в `Documents/Aura/`.
 *
 * **L3 (mesh)**: дополнительно ведётся набор **хэшей** `SHA-256(normalized)[0..8)`
 * (ключ [KEY_HASH_SET]). Этот набор пополняется не только из локальных вводов, но и из ответов
 * по mesh ([acceptMeshHashes]) — так повторный ввод кода после переустановки блокируется даже
 * если пользователь не дал разрешение на медиа.
 */
@Suppress("DEPRECATION")
internal object VipExtensionUsedCodes {

    private const val TAG = "VipExtUsedCodes"

    // ── L1 (SharedPrefs) ────────────────────────────────────────────────────
    private const val PREFS = "vip_extension_used_codes"
    private const val KEY_SET = "codes_set_v1"
    /** Набор усечённых SHA-256 хэшей (hex, 16 символов) — источник для mesh-восстановления. */
    private const val KEY_HASH_SET = "codes_hash_set_v1"
    /** Длина усечённого хэша (в байтах). Должно совпадать с MeshWireAuraVipUsedCodesCodec.HASH_LEN. */
    private const val HASH_LEN_BYTES: Int = 8

    /**
     * Ledger недавно применённых кодов: `"hashHex:secondsAdded:appliedAtMs"`. Нужен, чтобы при
     * опоздавшем mesh-ответе «этот код уже был использован» откатить добавленное время.
     * Записи старше [PENDING_REDEEM_TTL_MS] автоматически выкидываются (= «доверяем локально»).
     */
    private const val KEY_PENDING_REDEEMS = "pending_redeems_v1"

    /** 24 часа — окно, в течение которого mesh может аннулировать активацию. */
    private const val PENDING_REDEEM_TTL_MS: Long = 24L * 3_600L * 1_000L

    // ── L2 current (PNG в Pictures/Aura/) ───────────────────────────────────
    private const val FILE_NAME: String = ".aura_vip_used.png"
    private const val RELATIVE_DIR: String = "Pictures/Aura"
    private const val MIME: String = "image/png"

    // ── L2 legacy (.bin в Documents/Aura/) — только для чтения при миграции ─
    private const val LEGACY_FILE_NAME: String = ".aura_vip_used.bin"
    private const val LEGACY_RELATIVE_DIR: String = "Documents/Aura"

    private const val MAGIC_0: Byte = 'A'.code.toByte()
    private const val MAGIC_1: Byte = 'V'.code.toByte()
    private const val MAGIC_2: Byte = 'U'.code.toByte()
    private const val MAGIC_3: Byte = '1'.code.toByte()
    private const val VERSION: Byte = 0x01
    private const val HEADER_LEN: Int = 9  // magic(4)+ver(1)+count(4)
    private const val HMAC_LEN: Int = 32
    private const val CODE_BYTES: Int = VipExtensionCodeCodec.CODE_LENGTH // 12 ASCII

    private val HMAC_SECRET: ByteArray = byteArrayOf(
        0x41.toByte(), 0x75.toByte(), 0x52.toByte(), 0x75.toByte(),
        0x53.toByte(), 0x2D.toByte(), 0x56.toByte(), 0x49.toByte(),
        0x50.toByte(), 0x2D.toByte(), 0x55.toByte(), 0x53.toByte(),
        0x45.toByte(), 0x44.toByte(), 0x43.toByte(), 0x4F.toByte(),
        0x44.toByte(), 0x45.toByte(), 0x53.toByte(), 0x2D.toByte(),
        0x76.toByte(), 0x31.toByte(), 0x00.toByte(), 0x7B.toByte(),
        0x5E.toByte(), 0x22.toByte(), 0xA9.toByte(), 0xBC.toByte(),
        0xDE.toByte(), 0x11.toByte(), 0x42.toByte(), 0x3F.toByte(),
    )

    fun isUsed(context: Context, normalizedCode: String): Boolean {
        if (normalizedCode.length != VipExtensionCodeCodec.CODE_LENGTH) return false
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SET, null) ?: emptySet()
        if (normalizedCode in set) return true
        // L1b: набор хэшей (в т.ч. восстановленных из mesh).
        val hashHex = hashHex(normalizedCode)
        val hashSet = prefs.getStringSet(KEY_HASH_SET, null) ?: emptySet()
        if (hashHex in hashSet) return true
        val externalSet = try {
            readExternalSet(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "read external set failed: ${t.message}")
            null
        } ?: return false
        if (normalizedCode in externalSet) {
            // Самовосстановление L1 (например, после переустановки).
            val merged = HashSet(set)
            merged.addAll(externalSet)
            val mergedHashes = HashSet(hashSet)
            for (c in externalSet) mergedHashes.add(hashHex(c))
            prefs.edit()
                .putStringSet(KEY_SET, merged)
                .putStringSet(KEY_HASH_SET, mergedHashes)
                .apply()
            return true
        }
        return false
    }

    fun markUsed(context: Context, normalizedCode: String) {
        if (normalizedCode.length != VipExtensionCodeCodec.CODE_LENGTH) return
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_SET, null)?.let(::HashSet) ?: HashSet()
        val externalBefore = try {
            readExternalSet(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "read external set failed: ${t.message}")
            null
        }
        if (externalBefore != null) current.addAll(externalBefore)
        current.add(normalizedCode)
        val hashes = HashSet(prefs.getStringSet(KEY_HASH_SET, null) ?: emptySet())
        for (c in current) hashes.add(hashHex(c))
        prefs.edit()
            .putStringSet(KEY_SET, current)
            .putStringSet(KEY_HASH_SET, hashes)
            .apply()
        try {
            writeExternalSet(ctx, current)
        } catch (t: Throwable) {
            Log.w(TAG, "write external set failed: ${t.message}")
        }
    }

    /**
     * Вычислить 8-байтовый хэш для транслирования по mesh. Нужен [com.example.aura.vip.VipUsedCodeMeshAnnouncer]
     * и входящим обработчикам, чтобы не дублировать криптографию.
     */
    fun hashForMesh(normalizedCode: String): ByteArray? {
        if (normalizedCode.length != VipExtensionCodeCodec.CODE_LENGTH) return null
        return sha256Prefix(normalizedCode)
    }

    /**
     * Описание одного неподтверждённого применения кода — для возможной отмены при mesh-ответе.
     */
    internal data class PendingRedeem(
        val hashHex: String,
        val secondsAdded: Long,
        val appliedAtMs: Long,
    )

    /**
     * Записать факт только что выполненной активации в ledger. Нужен строго в паре с
     * [com.example.aura.preferences.VipAccessPreferences.extendByDays] внутри
     * [com.example.aura.security.VipExtensionCodeRedeemer.redeem].
     *
     * Все entries старше [PENDING_REDEEM_TTL_MS] перетираются.
     */
    fun recordRedeem(context: Context, normalizedCode: String, daysApplied: Int, appliedAtMs: Long) {
        if (normalizedCode.length != VipExtensionCodeCodec.CODE_LENGTH) return
        if (daysApplied <= 0) return
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hashHex = hashHex(normalizedCode)
        val seconds = daysApplied.toLong() * 86_400L
        val existing = HashSet(prefs.getStringSet(KEY_PENDING_REDEEMS, null) ?: emptySet())
        // Чистка протухших.
        val fresh = existing.mapNotNull(::parsePending)
            .filter { (appliedAtMs - it.appliedAtMs) < PENDING_REDEEM_TTL_MS && it.hashHex != hashHex }
            .map { encodePending(it) }
            .toMutableSet()
        fresh.add("$hashHex:$seconds:$appliedAtMs")
        prefs.edit().putStringSet(KEY_PENDING_REDEEMS, fresh).apply()
    }

    /**
     * Снять из ledger записи, хэши которых присутствуют в [incomingHashes] (результат mesh-ответа
     * «этот код уже был использован»). Возвращает удалённые записи, чтобы вызвающая сторона
     * могла вычесть секунды через [com.example.aura.preferences.VipAccessPreferences.rollbackSeconds].
     *
     * Одновременно выкидывает протухшие по TTL записи — дальше им нечего там делать.
     */
    internal fun consumeMatchingPendingRedeems(
        context: Context,
        incomingHashes: List<ByteArray>,
    ): List<PendingRedeem> {
        if (incomingHashes.isEmpty()) return emptyList()
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_PENDING_REDEEMS, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        val nowMs = System.currentTimeMillis()
        val incoming = HashSet<String>(incomingHashes.size)
        for (h in incomingHashes) {
            if (h.size == HASH_LEN_BYTES) incoming.add(bytesToHex(h))
        }
        val kept = HashSet<String>()
        val matched = ArrayList<PendingRedeem>()
        for (s in raw) {
            val p = parsePending(s) ?: continue
            val expired = (nowMs - p.appliedAtMs) >= PENDING_REDEEM_TTL_MS
            if (expired) continue // вычищаем и забываем — «период на отмену вышел».
            if (p.hashHex in incoming) {
                matched.add(p)
            } else {
                kept.add(encodePending(p))
            }
        }
        if (matched.isNotEmpty() || kept.size != raw.size) {
            prefs.edit().putStringSet(KEY_PENDING_REDEEMS, kept).apply()
        }
        return matched
    }

    private fun parsePending(raw: String): PendingRedeem? {
        val parts = raw.split(':')
        if (parts.size != 3) return null
        val hashHex = parts[0]
        if (hashHex.length != HASH_LEN_BYTES * 2) return null
        val seconds = parts[1].toLongOrNull() ?: return null
        val appliedAt = parts[2].toLongOrNull() ?: return null
        if (seconds <= 0L || appliedAt <= 0L) return null
        return PendingRedeem(hashHex, seconds, appliedAt)
    }

    private fun encodePending(p: PendingRedeem): String =
        "${p.hashHex}:${p.secondsAdded}:${p.appliedAtMs}"

    /**
     * Принять набор хэшей, пришедший по mesh (ответ на запрос восстановления). Новые хэши
     * попадают в [KEY_HASH_SET] и немедленно начинают блокировать повторный ввод соответствующих
     * кодов. Обратно в `normalized`-набор не пишем — исходный код по хэшу нам неизвестен, но для
     * проверки одноразовости этого достаточно.
     *
     * @return true — если набор хэшей изменился.
     */
    fun acceptMeshHashes(context: Context, hashes: List<ByteArray>): Boolean {
        if (hashes.isEmpty()) return false
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = HashSet(prefs.getStringSet(KEY_HASH_SET, null) ?: emptySet())
        var changed = false
        for (h in hashes) {
            if (h.size != HASH_LEN_BYTES) continue
            val hex = bytesToHex(h)
            if (current.add(hex)) changed = true
        }
        if (changed) prefs.edit().putStringSet(KEY_HASH_SET, current).apply()
        return changed
    }

    private fun hashHex(normalizedCode: String): String = bytesToHex(sha256Prefix(normalizedCode))

    private fun sha256Prefix(normalizedCode: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val full = md.digest(normalizedCode.toByteArray(Charsets.US_ASCII))
        return full.copyOfRange(0, HASH_LEN_BYTES)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )

    // ────────────────────────────── external I/O ──────────────────────────────

    private fun readExternalSet(context: Context): Set<String>? {
        // 1. Новый формат — PNG в Pictures/Aura/.
        readCurrentBlob(context)?.let { return decodeFile(it) }
        // 2. Старый формат — .bin в Documents/Aura/. При удачном чтении мигрируем на PNG.
        val legacy = readLegacyBlob(context) ?: return null
        val decoded = decodeFile(legacy) ?: return null
        try {
            writeCurrentBlob(context, legacy)
        } catch (t: Throwable) {
            Log.w(TAG, "legacy → png migration write failed: ${t.message}")
        }
        return decoded
    }

    private fun writeExternalSet(context: Context, codes: Set<String>) {
        writeCurrentBlob(context, encodeFile(codes))
    }

    // — Current (PNG image) ————————————————————————————————————————————————

    private fun readCurrentBlob(context: Context): ByteArray? {
        val uri = findImageUri(context) ?: return null
        val pngBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        return VipStegoPngCodec.unwrap(pngBytes)
    }

    private fun writeCurrentBlob(context: Context, rawPayload: ByteArray) {
        val cr = context.contentResolver
        val pngBytes = VipStegoPngCodec.wrap(rawPayload)
        val existing = findImageUri(context)
        val uri: Uri = if (existing != null) {
            existing
        } else {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
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

    private fun findImageUri(context: Context): Uri? {
        val cr = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            args = arrayOf(FILE_NAME, "$RELATIVE_DIR/%")
        } else {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            args = arrayOf(FILE_NAME)
        }
        cr.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }

    // — Legacy (.bin в Documents/Aura/) ————————————————————————————————————

    private fun readLegacyBlob(context: Context): ByteArray? {
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
                    cr.openInputStream(uri)?.use { return it.readBytes() }
                }
            }
        }
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val f = File(File(docs, "Aura"), LEGACY_FILE_NAME)
        if (f.exists()) {
            return try { f.readBytes() } catch (t: Throwable) { null }
        }
        return null
    }

    // ────────────────────────────── codec ──────────────────────────────
    // layout: [4 magic][1 version][4 count BE][count*12 ascii][32 HMAC]

    private fun encodeFile(codes: Set<String>): ByteArray {
        val valid = codes.filter { it.length == VipExtensionCodeCodec.CODE_LENGTH }
        val total = HEADER_LEN + valid.size * CODE_BYTES + HMAC_LEN
        val out = ByteArray(total)
        out[0] = MAGIC_0; out[1] = MAGIC_1; out[2] = MAGIC_2; out[3] = MAGIC_3
        out[4] = VERSION
        val count = valid.size
        out[5] = ((count ushr 24) and 0xFF).toByte()
        out[6] = ((count ushr 16) and 0xFF).toByte()
        out[7] = ((count ushr 8) and 0xFF).toByte()
        out[8] = (count and 0xFF).toByte()
        var offset = HEADER_LEN
        for (code in valid.sorted()) {
            val b = code.toByteArray(Charsets.US_ASCII)
            System.arraycopy(b, 0, out, offset, CODE_BYTES)
            offset += CODE_BYTES
        }
        val hmacInput = out.copyOfRange(0, offset)
        val sig = hmacSha256(HMAC_SECRET, hmacInput)
        System.arraycopy(sig, 0, out, offset, HMAC_LEN)
        return out
    }

    private fun decodeFile(blob: ByteArray): Set<String>? {
        if (blob.size < HEADER_LEN + HMAC_LEN) return null
        if (blob[0] != MAGIC_0 || blob[1] != MAGIC_1 ||
            blob[2] != MAGIC_2 || blob[3] != MAGIC_3
        ) return null
        if (blob[4] != VERSION) return null
        val count = (
            ((blob[5].toInt() and 0xFF) shl 24) or
                ((blob[6].toInt() and 0xFF) shl 16) or
                ((blob[7].toInt() and 0xFF) shl 8) or
                (blob[8].toInt() and 0xFF)
            )
        if (count < 0 || count > 100_000) return null
        val expectedLen = HEADER_LEN + count * CODE_BYTES + HMAC_LEN
        if (blob.size != expectedLen) return null
        val bodyLen = HEADER_LEN + count * CODE_BYTES
        val storedSig = blob.copyOfRange(bodyLen, bodyLen + HMAC_LEN)
        val computedSig = hmacSha256(HMAC_SECRET, blob.copyOfRange(0, bodyLen))
        if (!storedSig.contentEquals(computedSig)) return null
        val result = HashSet<String>(count)
        var offset = HEADER_LEN
        repeat(count) {
            val s = String(blob, offset, CODE_BYTES, Charsets.US_ASCII)
            if (s.length == CODE_BYTES && s.all { it.isLetterOrDigit() }) {
                result.add(s)
            }
            offset += CODE_BYTES
        }
        return result
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
