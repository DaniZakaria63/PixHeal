package id.my.daniza.litert.handler

import android.graphics.Bitmap
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

object EsrganHandler {
    private const val UPSCALE = 4

    suspend fun run(
        session: LitertBridge.ModelSession,
        bitmap: Bitmap,
        scaleTarget: Int = BitmapOps.MODE_FAST,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val inputSize = session.config.inputWidth
        Timber.i("EsrganHandler: input %dx%d, target=%d", bitmap.width, bitmap.height, scaleTarget)

        val (scaled, scaledW, scaledH) = BitmapOps.scaleToFit(bitmap, scaleTarget)
        Timber.i("EsrganHandler: scaled to %dx%d", scaledW, scaledH)

        val tilesX = ceil(scaledW.toFloat() / inputSize).toInt()
        val tilesY = ceil(scaledH.toFloat() / inputSize).toInt()
        val spacingX = if (tilesX > 1) (scaledW - inputSize).toFloat() / (tilesX - 1) else 0f
        val spacingY = if (tilesY > 1) (scaledH - inputSize).toFloat() / (tilesY - 1) else 0f
        val totalTiles = tilesX * tilesY
        Timber.i(
            "EsrganHandler: %dx%d tiles (%d), spacing %.1fx%.1f",
            tilesX,
            tilesY,
            totalTiles,
            spacingX,
            spacingY
        )

        val finalW = scaledW * UPSCALE
        val finalH = scaledH * UPSCALE

        val rAcc = IntArray(finalW * finalH)
        val gAcc = IntArray(finalW * finalH)
        val bAcc = IntArray(finalW * finalH)
        val weight = IntArray(finalW * finalH)
        val completed = AtomicInteger(0)

        val dispatcher = Dispatchers.Default.limitedParallelism(session.parallelism)

        val tileList = (0 until tilesY).flatMap { ty ->
            (0 until tilesX).map { tx -> Pair(tx, ty) }
        }

        val results = tileList.map { (tx, ty) ->
            async(dispatcher) {
                val offX = (tx * spacingX).toInt().coerceAtMost(scaledW - inputSize)
                val offY = (ty * spacingY).toInt().coerceAtMost(scaledH - inputSize)

                val tile = Bitmap.createBitmap(scaled, offX, offY, inputSize, inputSize)

                val outTile = inferTile(session, tile)
                tile.recycle()

                val done = completed.incrementAndGet()
                progress?.invoke(done, totalTiles)
                TileResult(offX, offY, outTile)
            }
        }.awaitAll()
        scaled.recycle()

        for (r in results) {
            val tw = r.bitmap.width
            val th = r.bitmap.height
            val tilePixels = IntArray(tw * th)
            r.bitmap.getPixels(tilePixels, 0, tw, 0, 0, tw, th)
            r.bitmap.recycle()

            val dstX = r.offX * UPSCALE
            val dstY = r.offY * UPSCALE

            for (tiley in 0 until th) {
                for (tilex in 0 until tw) {
                    val px = dstX + tilex
                    val py = dstY + tiley
                    if (px < finalW && py < finalH) {
                        val pixel = tilePixels[tiley * tw + tilex]
                        val idx = py * finalW + px
                        rAcc[idx] += (pixel shr 16) and 0xFF
                        gAcc[idx] += (pixel shr 8) and 0xFF
                        bAcc[idx] += pixel and 0xFF
                        weight[idx] += 1
                    }
                }
            }
        }

        val stitched = IntArray(finalW * finalH)
        for (i in stitched.indices) {
            if (weight[i] > 0) {
                val r = (rAcc[i] / weight[i]).coerceIn(0, 255)
                val g = (gAcc[i] / weight[i]).coerceIn(0, 255)
                val b = (bAcc[i] / weight[i]).coerceIn(0, 255)
                stitched[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        Timber.i("EsrganHandler: done — %dx%d", finalW, finalH)
        Bitmap.createBitmap(stitched, finalW, finalH, Bitmap.Config.ARGB_8888)
    }

    private fun inferTile(session: LitertBridge.ModelSession, tile: Bitmap): Bitmap {
        val input = session.model.createInputBuffers().first()
        val outBuf = session.model.createOutputBuffers().first()
        try {
            input.writeInt8(BitmapOps.pixelsToUint8(tile))
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

    private data class TileResult(
        val offX: Int, val offY: Int, val bitmap: Bitmap,
    )
}