package com.banana.search.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.ByteArrayOutputStream

object ImagePreprocess {

    fun cropQuestion(src: Bitmap, cropTop: Float, cropBottom: Float, region: android.graphics.Rect? = null): Bitmap {
        if (region != null) {
            val left = region.left.coerceIn(0, src.width - 1)
            val top = region.top.coerceIn(0, src.height - 1)
            val right = region.right.coerceIn(left + 1, src.width)
            val bottom = region.bottom.coerceIn(top + 1, src.height)
            return Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        }
        val top = (src.height * cropTop).toInt().coerceIn(0, src.height - 1)
        val bottom = (src.height * cropBottom).toInt().coerceIn(top + 1, src.height)
        val h = bottom - top
        if (h <= 0) return src
        return Bitmap.createBitmap(src, 0, top, src.width, h)
    }

    fun scaleToMaxSide(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    fun prepareForOcr(src: Bitmap): Bitmap {
        val inverted = isDarkBackground(src)
        val cm = ColorMatrix()
        if (inverted) cm.set(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))

        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        if (inverted) cm.postConcat(contrastMatrix) else cm.set(contrastMatrix)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun isDarkBackground(src: Bitmap): Boolean {
        var sum = 0.0
        var count = 0
        val stepX = (src.width / 8).coerceAtLeast(1)
        val stepY = (src.height / 8).coerceAtLeast(1)
        var y = 0
        while (y < src.height) {
            var x = 0
            while (x < src.width) {
                val c = src.getPixel(x, y)
                sum += (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF))
                count++
                x += stepX
            }
            y += stepY
        }
        return count > 0 && sum / count < 110.0
    }

    fun toJpeg(src: Bitmap, quality: Int = 78): ByteArray {
        val bos = ByteArrayOutputStream()
        src.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }
}
