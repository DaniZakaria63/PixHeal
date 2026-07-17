package id.my.daniza.litert.data

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.scale

object BitmapOps {
    const val MODE_FAST = 128
    const val MODE_QUALITY = 256

    fun scaleToFit(bitmap: Bitmap, targetSize: Int): Triple<Bitmap, Int, Int> {
        val scale = if (bitmap.width >= bitmap.height)
            targetSize.toFloat() / bitmap.height
        else
            targetSize.toFloat() / bitmap.width
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Triple(bitmap.scale(w, h, true), w, h)
    }

    fun padBitmap(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val padded = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawBitmap(source, 0f, 0f, null)
        source.recycle()
        return padded
    }

    fun pixelsToUint8(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val data = ByteArray(w * h * 3)
        var i = 0
        for (p in pixels) {
            data[i++] = ((p shr 16) and 0xFF).toByte()
            data[i++] = ((p shr 8) and 0xFF).toByte()
            data[i++] = (p and 0xFF).toByte()
        }
        return data
    }

    fun pixelsToFloat(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val data = FloatArray(w * h * 3)
        var i = 0
        for (p in pixels) {
            data[i++] = ((p shr 16) and 0xFF) / 255f
            data[i++] = ((p shr 8) and 0xFF) / 255f
            data[i++] = (p and 0xFF) / 255f
        }
        return data
    }

    fun floatToBitmap(data: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = (data[i * 3 + 0] * 255f).toInt().coerceIn(0, 255)
            val g = (data[i * 3 + 1] * 255f).toInt().coerceIn(0, 255)
            val b = (data[i * 3 + 2] * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun byteToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = data[i * 3 + 0].toInt() and 0xFF
            val g = data[i * 3 + 1].toInt() and 0xFF
            val b = data[i * 3 + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}