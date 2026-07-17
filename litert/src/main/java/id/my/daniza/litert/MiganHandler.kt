package id.my.daniza.litert

import android.graphics.Bitmap
import kotlinx.coroutines.coroutineScope
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import androidx.core.graphics.scale
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

object MiganHandler {

    suspend fun run(
        bridge: LitertBridge,
        imageBitmap: Bitmap,
        maskBitmap: Bitmap,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val bytes = bridge.modelBytes ?: return@coroutineScope null
        val tileSize = bridge.outputWidth

        val origW = imageBitmap.width
        val origH = imageBitmap.height
        Timber.i("MiganHandler: image %dx%d, mask %dx%d", origW, origH, maskBitmap.width, maskBitmap.height)

        val (image, w, h) = bridge.scaleToFit(imageBitmap, tileSize)
        Timber.i("MiganHandler: scaled to %dx%d", w, h)

        val maskScaled = maskBitmap.scale(w, h, false)
        val invMask = invertMask(maskScaled)

        val tilesX = ceil(w.toFloat() / tileSize).toInt()
        val tilesY = ceil(h.toFloat() / tileSize).toInt()
        val spacingX = if (tilesX > 1) (w - tileSize).toFloat() / (tilesX - 1) else 0f
        val spacingY = if (tilesY > 1) (h - tileSize).toFloat() / (tilesY - 1) else 0f
        val totalTiles = tilesX * tilesY
        Timber.i("MiganHandler: %dx%d tiles (%d), spacing %.1fx%.1f", tilesX, tilesY, totalTiles, spacingX, spacingY)

        val rAcc = IntArray(w * h)
        val gAcc = IntArray(w * h)
        val bAcc = IntArray(w * h)
        val weight = IntArray(w * h)

        val interpreter = bridge.makeInterpreter()
        try {
            for (ty in 0 until tilesY) {
                for (tx in 0 until tilesX) {
                    val offX = (tx * spacingX).toInt().coerceAtMost(w - tileSize)
                    val offY = (ty * spacingY).toInt().coerceAtMost(h - tileSize)

                    val imgTile = Bitmap.createBitmap(image, offX, offY, tileSize, tileSize)
                    val maskTile = Bitmap.createBitmap(invMask, offX, offY, tileSize, tileSize)

                    val outTile = inferTile(bridge, interpreter, imgTile, maskTile)
                    imgTile.recycle()
                    maskTile.recycle()

                    val tilePixels = IntArray(tileSize * tileSize)
                    outTile.getPixels(tilePixels, 0, tileSize, 0, 0, tileSize, tileSize)
                    outTile.recycle()

                    for (tiley in 0 until tileSize) {
                        for (tilex in 0 until tileSize) {
                            val px = offX + tilex
                            val py = offY + tiley
                            if (px < w && py < h) {
                                val pixel = tilePixels[tiley * tileSize + tilex]
                                val idx = py * w + px
                                rAcc[idx] += (pixel shr 16) and 0xFF
                                gAcc[idx] += (pixel shr 8) and 0xFF
                                bAcc[idx] += pixel and 0xFF
                                weight[idx] += 1
                            }
                        }
                    }

                    progress?.invoke(ty * tilesX + tx + 1, totalTiles)
                }
            }
        } finally {
            interpreter.close()
        }
        maskScaled.recycle()
        invMask.recycle()
        if (image !== imageBitmap) image.recycle()

        val stitched = IntArray(w * h)
        for (i in stitched.indices) {
            if (weight[i] > 0) {
                val r = (rAcc[i] / weight[i]).coerceIn(0, 255)
                val g = (gAcc[i] / weight[i]).coerceIn(0, 255)
                val b = (bAcc[i] / weight[i]).coerceIn(0, 255)
                stitched[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val result = Bitmap.createBitmap(stitched, w, h, Bitmap.Config.ARGB_8888)
        Timber.i("MiganHandler: done — %dx%d, scaling back to %dx%d", w, h, origW, origH)
        val upscaled = Bitmap.createScaledBitmap(result, origW, origH, true)
        result.recycle()
        upscaled
    }

    private fun inferTile(bridge: LitertBridge, interp: Interpreter, imgTile: Bitmap, maskTile: Bitmap): Bitmap {
        val input = prepareInput(imgTile, maskTile)
        val output = ByteBuffer.allocateDirect(bridge.outputWidth * bridge.outputHeight * 3 * 4)
            .order(ByteOrder.nativeOrder())
        interp.run(input, output)
        output.rewind()
        val floats = FloatArray(bridge.outputWidth * bridge.outputHeight * 3)
        for (i in floats.indices) floats[i] = output.float
        return floatsToBitmap(bridge, floats)
    }

    private fun invertMask(mask: Bitmap): Bitmap {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val gray = ((pixels[i] shr 16) and 0xFF).coerceIn(0, 255)
            val inv = 255 - gray
            pixels[i] = (0xFF shl 24) or (inv shl 16) or (inv shl 8) or inv
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun prepareInput(imgTile: Bitmap, maskTile: Bitmap): ByteBuffer {
        val w = imgTile.width
        val h = imgTile.height
        val pixels = IntArray(w * h)
        imgTile.getPixels(pixels, 0, w, 0, 0, w, h)

        val maskPixels = IntArray(w * h)
        maskTile.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val buffer = ByteBuffer.allocateDirect(1 * 4 * w * h * 4)
            .order(ByteOrder.nativeOrder())

        for (i in 0 until w * h) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 127.5f - 1f
            val g = ((p shr 8) and 0xFF) / 127.5f - 1f
            val b = (p and 0xFF) / 127.5f - 1f

            val maskGray = ((maskPixels[i] shr 16) and 0xFF) / 255f

            buffer.putFloat(maskGray - 0.5f)
            buffer.putFloat(r * maskGray)
            buffer.putFloat(g * maskGray)
            buffer.putFloat(b * maskGray)
        }
        buffer.rewind()
        return buffer
    }

    private fun floatsToBitmap(bridge: LitertBridge, data: FloatArray): Bitmap {
        val pixels = IntArray(bridge.outputWidth * bridge.outputHeight)
        for (i in pixels.indices) {
            val r = ((data[i * 3 + 0] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((data[i * 3 + 1] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((data[i * 3 + 2] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, bridge.outputWidth, bridge.outputHeight, Bitmap.Config.ARGB_8888)
    }
}
