package com.example.aura.preferences

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * PNG-стегано-контейнер для VIP-слоя персистентности.
 *
 * Задача: сохранить небольшой бинарный блоб в публичном каталоге `Pictures/Aura/` так, чтобы
 * MediaStore Android 10+ корректно распознал файл как изображение. Это обязательное условие,
 * чтобы после удаления приложения (когда `owner_package_name` «осиротеет») переустановленная
 * копия могла увидеть файл через `MediaStore.Images` — по разрешениям `READ_MEDIA_IMAGES`
 * (API 33+) или `READ_EXTERNAL_STORAGE` (API ≤ 32). С некорректным/не-медийным файлом
 * этот путь не работает (на 30+ нужно `MANAGE_EXTERNAL_STORAGE`, чего мы избегаем).
 *
 * Структура:
 *  - стандартный 1×1 RGBA-PNG (сигнатура + IHDR + IDAT + IEND);
 *  - между IHDR и IDAT вставлен чанк `tEXt` (ключевое слово + `\0` + текст).
 *    Текст — base64(payload). Получается легитимная картинка, которую читают все
 *    стандартные декодеры, а наши байты лежат в метаданных.
 */
internal object VipStegoPngCodec {

    private const val TEXT_KEYWORD: String = "AuRus"

    private val PNG_SIGNATURE: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** Обернуть произвольный блоб в валидный PNG. */
    fun wrap(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 128)
        out.write(PNG_SIGNATURE)

        // IHDR: 1×1, 8 бит на канал, RGBA, без интерлейса.
        val ihdr = ByteArray(13).apply {
            this[0] = 0; this[1] = 0; this[2] = 0; this[3] = 1 // width
            this[4] = 0; this[5] = 0; this[6] = 0; this[7] = 1 // height
            this[8] = 8                                        // bit depth
            this[9] = 6                                        // color type RGBA
            this[10] = 0                                       // compression
            this[11] = 0                                       // filter
            this[12] = 0                                       // interlace
        }
        writeChunk(out, "IHDR", ihdr)

        // tEXt: keyword (ASCII) \0 text (ASCII). Текст — base64 полезной нагрузки.
        val keyword = TEXT_KEYWORD.toByteArray(Charsets.US_ASCII)
        val text = base64Encode(payload)
        val textBytes = text.toByteArray(Charsets.US_ASCII)
        val tEXt = ByteArray(keyword.size + 1 + textBytes.size).also { buf ->
            System.arraycopy(keyword, 0, buf, 0, keyword.size)
            buf[keyword.size] = 0
            System.arraycopy(textBytes, 0, buf, keyword.size + 1, textBytes.size)
        }
        writeChunk(out, "tEXt", tEXt)

        // IDAT: один прозрачный пиксель, сжатый deflate.
        writeChunk(out, "IDAT", deflate(byteArrayOf(0, 0, 0, 0, 0)))
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** Извлечь полезную нагрузку из PNG, созданного [wrap]. Возвращает `null` при несоответствии. */
    fun unwrap(pngBytes: ByteArray): ByteArray? {
        if (pngBytes.size < PNG_SIGNATURE.size + 12) return null
        for (i in PNG_SIGNATURE.indices) {
            if (pngBytes[i] != PNG_SIGNATURE[i]) return null
        }
        var off = PNG_SIGNATURE.size
        while (off + 8 <= pngBytes.size) {
            val len = readUInt32(pngBytes, off)
            if (len < 0 || len > pngBytes.size) return null
            val type = String(pngBytes, off + 4, 4, Charsets.US_ASCII)
            val dataStart = off + 8
            val crcEnd = dataStart + len + 4
            if (crcEnd > pngBytes.size) return null
            if (type == "tEXt") {
                var sep = dataStart
                val limit = dataStart + len
                while (sep < limit && pngBytes[sep] != 0.toByte()) sep++
                if (sep < limit) {
                    val keyword = String(pngBytes, dataStart, sep - dataStart, Charsets.US_ASCII)
                    if (keyword == TEXT_KEYWORD) {
                        val textStart = sep + 1
                        val textLen = limit - textStart
                        if (textLen > 0) {
                            val text = String(pngBytes, textStart, textLen, Charsets.US_ASCII)
                            return try {
                                base64Decode(text)
                            } catch (t: Throwable) {
                                null
                            }
                        }
                    }
                }
            }
            if (type == "IEND") return null
            off = crcEnd
        }
        return null
    }

    // ──────────────────────────────── internals ────────────────────────────────

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val len = data.size
        out.write(
            byteArrayOf(
                ((len ushr 24) and 0xFF).toByte(),
                ((len ushr 16) and 0xFF).toByte(),
                ((len ushr 8) and 0xFF).toByte(),
                (len and 0xFF).toByte(),
            ),
        )
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val v = crc.value.toInt()
        out.write(
            byteArrayOf(
                ((v ushr 24) and 0xFF).toByte(),
                ((v ushr 16) and 0xFF).toByte(),
                ((v ushr 8) and 0xFF).toByte(),
                (v and 0xFF).toByte(),
            ),
        )
    }

    private fun deflate(input: ByteArray): ByteArray {
        val def = Deflater()
        def.setInput(input)
        def.finish()
        val buf = ByteArray(64)
        val out = ByteArrayOutputStream()
        while (!def.finished()) {
            val n = def.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        def.end()
        return out.toByteArray()
    }

    private fun readUInt32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    // Небольшой автономный base64 (без зависимости от `android.util.Base64` — чтобы модуль
    // оставался чистым и тестируемым стандартным Kotlin/JVM). RFC 4648, без переносов.
    private const val B64_ALPHABET: String =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun base64Encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i + 3 <= data.size) {
            val a = data[i].toInt() and 0xFF
            val b = data[i + 1].toInt() and 0xFF
            val c = data[i + 2].toInt() and 0xFF
            sb.append(B64_ALPHABET[a ushr 2])
            sb.append(B64_ALPHABET[((a and 0x03) shl 4) or (b ushr 4)])
            sb.append(B64_ALPHABET[((b and 0x0F) shl 2) or (c ushr 6)])
            sb.append(B64_ALPHABET[c and 0x3F])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val a = data[i].toInt() and 0xFF
                sb.append(B64_ALPHABET[a ushr 2])
                sb.append(B64_ALPHABET[(a and 0x03) shl 4])
                sb.append('=').append('=')
            }
            2 -> {
                val a = data[i].toInt() and 0xFF
                val b = data[i + 1].toInt() and 0xFF
                sb.append(B64_ALPHABET[a ushr 2])
                sb.append(B64_ALPHABET[((a and 0x03) shl 4) or (b ushr 4)])
                sb.append(B64_ALPHABET[(b and 0x0F) shl 2])
                sb.append('=')
            }
        }
        return sb.toString()
    }

    private fun base64Decode(text: String): ByteArray {
        val s = text.filter { it != '\n' && it != '\r' && it != ' ' && it != '\t' }
        if (s.isEmpty()) return ByteArray(0)
        if (s.length % 4 != 0) throw IllegalArgumentException("bad b64 length")
        val pad = when {
            s.endsWith("==") -> 2
            s.endsWith("=") -> 1
            else -> 0
        }
        val outLen = (s.length / 4) * 3 - pad
        val out = ByteArray(outLen)
        var oi = 0
        var i = 0
        while (i < s.length) {
            val c0 = indexOf(s[i]); val c1 = indexOf(s[i + 1])
            val c2 = if (s[i + 2] == '=') 0 else indexOf(s[i + 2])
            val c3 = if (s[i + 3] == '=') 0 else indexOf(s[i + 3])
            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (oi < outLen) out[oi++] = ((triple ushr 16) and 0xFF).toByte()
            if (oi < outLen) out[oi++] = ((triple ushr 8) and 0xFF).toByte()
            if (oi < outLen) out[oi++] = (triple and 0xFF).toByte()
            i += 4
        }
        return out
    }

    private fun indexOf(ch: Char): Int {
        val i = B64_ALPHABET.indexOf(ch)
        if (i < 0) throw IllegalArgumentException("bad b64 char: $ch")
        return i
    }
}
