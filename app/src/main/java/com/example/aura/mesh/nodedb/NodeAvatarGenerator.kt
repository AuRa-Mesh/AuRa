package com.example.aura.mesh.nodedb

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import java.util.Locale
import kotlin.math.abs

/**
 * Детерминированный «идентикон» только по [nodeIdHex]: один и тот же id даёт ту же картинку
 * на любом устройстве (без обмена файлами по эфиру), независимо от номера узла.
 */
object NodeAvatarGenerator {

    private const val SIZE = 128

    fun render(nodeIdHex: String): Bitmap {
        val s = nodeIdHex.trim().removePrefix("!").lowercase(Locale.ROOT)
        // Детерминированный 64-bit от строки id (все литералы в диапазоне Long).
        var seed = 1469598103934665603L // FNV-1a offset basis
        for (ch in s) {
            seed = seed xor ch.code.toLong()
            seed *= 1099511628211L
        }
        if (s.isEmpty()) seed = seed xor 0x13579BDF2ECA8647L
        val bits = seed xor (seed ushr 17) xor (seed shl 5)
        val hueBase = ((seed ushr 8) and 0x3FF).toFloat() / 1024f * 360f
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val hsv = floatArrayOf(hueBase, 0.28f, 0.20f)
            color = AndroidColor.HSVToColor(hsv)
        }
        canvas.drawPaint(bg)
        val n = 5
        val cell = SIZE.toFloat() / n
        val half = (n + 1) / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0 until n) {
            for (x in 0 until half) {
                val idx = y * half + x
                val on = ((bits ushr (idx and 63)) and 1L) != 0L
                if (!on) continue
                val hue = (hueBase + (x * 31 + y * 17) % 80) % 360f
                paint.color = AndroidColor.HSVToColor(
                    floatArrayOf(hue, 0.58f, 0.62f + abs((idx % 5) * 0.04f)),
                )
                val left = x * cell
                val top = y * cell
                canvas.drawRect(left, top, left + cell, top + cell, paint)
                val mx = n - 1 - x
                if (mx != x) {
                    val lx = mx * cell
                    canvas.drawRect(lx, top, lx + cell, top + cell, paint)
                }
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = SIZE * 0.035f
        paint.color = AndroidColor.HSVToColor(floatArrayOf(hueBase, 0.15f, 0.88f))
        paint.alpha = 100
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f - SIZE * 0.05f, paint)
        return bmp
    }
}
