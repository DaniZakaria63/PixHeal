package id.my.daniza.litert.handler

import android.graphics.Bitmap
import androidx.core.graphics.scale
import id.my.daniza.litert.data.BitmapOps
import id.my.daniza.litert.LitertBridge
import id.my.daniza.litert.data.TensorDataType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

object MiganHandler {

    suspend fun run(
        session: LitertBridge.ModelSession,
        imageBitmap: Bitmap,
        maskBitmap: Bitmap,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val tileSize = session.config.inputWidth
        Timber.i(
            "MiganHandler: model in=%dx%d out=%dx%d",
            session.config.inputWidth,
            session.config.inputHeight,
            session.config.outputWidth,
            session.config.outputHeight
        )

        val origW = imageBitmap.width
        val origH = imageBitmap.height
        Timber.i(
            "MiganHandler: image %dx%d, mask %dx%d",
            origW,
            origH,
            maskBitmap.width,
            maskBitmap.height
        )

        val (image, w, h) = BitmapOps.scaleToFit(imageBitmap, tileSize)
        Timber.i("MiganHandler: scaled to %dx%d", w, h)

        val maskScaled = maskBitmap.scale(w, h, false)
        val invMask = invertMask(maskScaled)

        val tilesX = ceil(w.toFloat() / tileSize).toInt()
        val tilesY = ceil(h.toFloat() / tileSize).toInt()
        val spacingX = if (tilesX > 1) (w - tileSize).toFloat() / (tilesX - 1) else 0f
        val spacingY = if (tilesY > 1) (h - tileSize).toFloat() / (tilesY - 1) else 0f
        val totalTiles = tilesX * tilesY
        Timber.i(
            "MiganHandler: %dx%d tiles (%d), spacing %.1fx%.1f",
            tilesX,
            tilesY,
            totalTiles,
            spacingX,
            spacingY
        )

        val rAcc = IntArray(w * h)
        val gAcc = IntArray(w * h)
        val bAcc = IntArray(w * h)
        val weight = IntArray(w * h)
        val completed = AtomicInteger(0)

        val dispatcher = Dispatchers.Default.limitedParallelism(session.parallelism)

        val tileList = (0 until tilesY).flatMap { ty ->
            (0 until tilesX).map { tx -> Pair(tx, ty) }
        }

        val results = tileList.map { (tx, ty) ->
            async(dispatcher) {
                val offX = (tx * spacingX).toInt().coerceAtMost(w - tileSize)
                val offY = (ty * spacingY).toInt().coerceAtMost(h - tileSize)

                val imgTile = Bitmap.createBitmap(image, offX, offY, tileSize, tileSize)
                val maskTile = Bitmap.createBitmap(invMask, offX, offY, tileSize, tileSize)

                val outTile = inferTile(session, imgTile, maskTile)
                imgTile.recycle()
                maskTile.recycle()

                val done = completed.incrementAndGet()
                progress?.invoke(done, totalTiles)
                TileResult(offX, offY, outTile)
            }
        }.awaitAll()

        maskScaled.recycle()
        invMask.recycle()
        if (image !== imageBitmap) image.recycle()

        for (r in results) {
            val tw = r.bitmap.width
            val th = r.bitmap.height
            val tilePixels = IntArray(tw * th)
            r.bitmap.getPixels(tilePixels, 0, tw, 0, 0, tw, th)
            r.bitmap.recycle()

            for (tiley in 0 until th) {
                for (tilex in 0 until tw) {
                    val px = r.offX + tilex
                    val py = r.offY + tiley
                    if (px < w && py < h) {
                        val pixel = tilePixels[tiley * tw + tilex]
                        val idx = py * w + px
                        rAcc[idx] += (pixel shr 16) and 0xFF
                        gAcc[idx] += (pixel shr 8) and 0xFF
                        bAcc[idx] += pixel and 0xFF
                        weight[idx] += 1
                    }
                }
            }
        }

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

    private fun inferTile(session: LitertBridge.ModelSession, imgTile: Bitmap, maskTile: Bitmap): Bitmap {
        val inputData = prepareInput(imgTile, maskTile)
        val input = session.model.createInputBuffers().first()
        val outBuf = session.model.createOutputBuffers().first()
        try {
            input.writeFloat(inputData)
            session.model.run(listOf(input), listOf(outBuf))
            return when (session.config.outputType) {
                TensorDataType.FLOAT32 -> BitmapOps.floatToBitmap(outBuf.readFloat(), session.config.outputWidth, session.config.outputHeight)
                TensorDataType.UINT8 -> BitmapOps.byteToBitmap(outBuf.readInt8(), session.config.outputWidth, session.config.outputHeight)
            }
        } finally {
            input.close()
            outBuf.close()
        }
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

    private fun prepareInput(imgTile: Bitmap, maskTile: Bitmap): FloatArray {
        val w = imgTile.width
        val h = imgTile.height
        val pixels = IntArray(w * h)
        imgTile.getPixels(pixels, 0, w, 0, 0, w, h)

        val maskPixels = IntArray(w * h)
        maskTile.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val data = FloatArray(w * h * 4)

        for (i in 0 until w * h) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 127.5f - 1f
            val g = ((p shr 8) and 0xFF) / 127.5f - 1f
            val b = (p and 0xFF) / 127.5f - 1f

            val maskGray = ((maskPixels[i] shr 16) and 0xFF) / 255f

            data[i * 4 + 0] = maskGray - 0.5f
            data[i * 4 + 1] = r * maskGray
            data[i * 4 + 2] = g * maskGray
            data[i * 4 + 3] = b * maskGray
        }
        return data
    }

    private data class TileResult(
        val offX: Int, val offY: Int, val bitmap: Bitmap,
    )
}