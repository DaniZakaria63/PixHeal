package id.my.daniza.litert.handler

import android.graphics.Bitmap
import id.my.daniza.litert.data.BitmapOps
import id.my.daniza.litert.LitertBridge
import id.my.daniza.litert.data.ModelConfig
import id.my.daniza.litert.data.TensorDataType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/**
 * ESRGAN (Real-ESRGAN x4plus) super-resolution handler.
 *
 * ## What it does
 * Upscales an input image by 4x using a tiled inference strategy. The source
 * bitmap is first scaled (with aspect-ratio preserved) to fit [BitmapOps.MODE_FAST]
 * (128px) or [BitmapOps.MODE_QUALITY] (256px) on its longest side, then split into
 * [ModelConfig.ESRGAN_DEFAULT.inputWidth]x[ModelConfig.ESRGAN_DEFAULT.inputHeight]
 * (128x128) tiles. Each tile is run independently through the model and the 4x-upscaled
 * tiles are blended back with overlap-weighted averaging to hide seam artifacts.
 *
 * ## Concurrency
 * Tiles run in parallel on [Dispatchers.Default] limited to [LitertBridge.ModelSession.parallelism]
 * threads. A shared [AtomicInteger] tracks completed tiles to drive the [progress] callback.
 *
 * ## I/O buffers
 * - Input:  UINT8 RGB, [inputWidth]x[inputHeight]x3 bytes (written via [BitmapOps.pixelsToUint8]).
 * - Output: [ModelConfig.ESRGAN_DEFAULT.outputWidth]x[ModelConfig.ESRGAN_DEFAULT.outputHeight]x3
 *   (512x512x3). Read as UINT8 (config [id.my.daniza.litert.data.TensorDataType.UINT8]) and
 *   converted to Bitmap via [BitmapOps.byteToBitmap].
 *
 * ## Notes
 * The model is single-input/single-output; the session's [CompiledModel] is shared across
 * all tiles (created once in [LitertBridge.runSuperRes] and passed in as [LitertBridge.ModelSession]).
 */
object EsrganHandler {
    private const val UPSCALE = 4

    suspend fun run(
        bridge: LitertBridge,
        bitmap: Bitmap,
        scaleTarget: Int = BitmapOps.MODE_FAST,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val inputSize = ModelConfig.ESRGAN_DEFAULT.inputWidth
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

        val dispatcher = Dispatchers.Default.limitedParallelism(bridge.parallelism)

        val tileList = (0 until tilesY).flatMap { ty ->
            (0 until tilesX).map { tx -> Pair(tx, ty) }
        }

        val results = tileList.map { (tx, ty) ->
            async(dispatcher) {
                val offX = (tx * spacingX).toInt().coerceAtMost(scaledW - inputSize)
                val offY = (ty * spacingY).toInt().coerceAtMost(scaledH - inputSize)

                val tile = Bitmap.createBitmap(scaled, offX, offY, inputSize, inputSize)

                val outTile = inferTile(bridge, ModelConfig.ESRGAN_DEFAULT, tile)
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

    private fun inferTile(bridge: LitertBridge, config: ModelConfig, tile: Bitmap): Bitmap {
        // Each tile gets its own CompiledModel instance. LiteRT's CompiledModel is
        // not safe for concurrent run()/buffer creation across threads, and the tiled
        // strategy fans tiles out across worker threads, so sharing one model crashes
        // natively (SIGSEGV in nativeCreateOutputBuffers).
        val session = bridge.createWorkerSession(config)
        try {
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
        } finally {
            session.close()
        }
    }

    private data class TileResult(
        val offX: Int, val offY: Int, val bitmap: Bitmap,
    )
}