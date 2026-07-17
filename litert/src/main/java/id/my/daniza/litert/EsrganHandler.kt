package id.my.daniza.litert

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

object EsrganHandler {
    private const val INPUT_SIZE = 128
    private const val UPSCALE = 4

    suspend fun run(
        bridge: LitertBridge,
        bitmap: Bitmap,
        scaleTarget: Int = LitertBridge.MODE_FAST,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val bytes = bridge.modelBytes ?: return@coroutineScope null
        Timber.i("EsrganHandler: input %dx%d, target=%d", bitmap.width, bitmap.height, scaleTarget)

        val (scaled, scaledW, scaledH) = bridge.scaleToFit(bitmap, scaleTarget)
        Timber.i("EsrganHandler: scaled to %dx%d", scaledW, scaledH)

        val tilesX = ceil(scaledW.toFloat() / INPUT_SIZE).toInt()
        val tilesY = ceil(scaledH.toFloat() / INPUT_SIZE).toInt()
        val spacingX = if (tilesX > 1) (scaledW - INPUT_SIZE).toFloat() / (tilesX - 1) else 0f
        val spacingY = if (tilesY > 1) (scaledH - INPUT_SIZE).toFloat() / (tilesY - 1) else 0f
        val totalTiles = tilesX * tilesY
        Timber.i("EsrganHandler: %dx%d tiles (%d), spacing %.1fx%.1f", tilesX, tilesY, totalTiles, spacingX, spacingY)

        val outW = scaledW * UPSCALE
        val outH = scaledH * UPSCALE

        val rAcc = IntArray(outW * outH)
        val gAcc = IntArray(outW * outH)
        val bAcc = IntArray(outW * outH)
        val weight = IntArray(outW * outH)
        val completed = AtomicInteger(0)

        val dispatcher = Dispatchers.Default.limitedParallelism(bridge.parallelism)

        val tileList = (0 until tilesY).flatMap { ty ->
            (0 until tilesX).map { tx -> Pair(tx, ty) }
        }

        val results = tileList.map { (tx, ty) ->
            async(dispatcher) {
                val offX = (tx * spacingX).toInt().coerceAtMost(scaledW - INPUT_SIZE)
                val offY = (ty * spacingY).toInt().coerceAtMost(scaledH - INPUT_SIZE)

                val tile = Bitmap.createBitmap(scaled, offX, offY, INPUT_SIZE, INPUT_SIZE)

                lateinit var outTile: Bitmap
                val local = bridge.makeInterpreter()
                try {
                    outTile = inferTile(bridge, local, tile)
                } finally {
                    local.close()
                }
                tile.recycle()

                val done = completed.incrementAndGet()
                progress?.invoke(done, totalTiles)
                TileResult(offX, offY, outTile)
            }
        }.awaitAll()
        scaled.recycle()

        for (r in results) {
            val tilePixels = IntArray(bridge.outputWidth * bridge.outputHeight)
            r.bitmap.getPixels(tilePixels, 0, bridge.outputWidth, 0, 0, bridge.outputWidth, bridge.outputHeight)
            r.bitmap.recycle()

            val dstX = r.offX * UPSCALE
            val dstY = r.offY * UPSCALE

            for (tiley in 0 until bridge.outputHeight) {
                for (tilex in 0 until bridge.outputWidth) {
                    val px = dstX + tilex
                    val py = dstY + tiley
                    if (px < outW && py < outH) {
                        val pixel = tilePixels[tiley * bridge.outputWidth + tilex]
                        val idx = py * outW + px
                        rAcc[idx] += (pixel shr 16) and 0xFF
                        gAcc[idx] += (pixel shr 8) and 0xFF
                        bAcc[idx] += pixel and 0xFF
                        weight[idx] += 1
                    }
                }
            }
        }

        val stitched = IntArray(outW * outH)
        for (i in stitched.indices) {
            if (weight[i] > 0) {
                val r = (rAcc[i] / weight[i]).coerceIn(0, 255)
                val g = (gAcc[i] / weight[i]).coerceIn(0, 255)
                val b = (bAcc[i] / weight[i]).coerceIn(0, 255)
                stitched[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        Timber.i("EsrganHandler: done — %dx%d", outW, outH)
        Bitmap.createBitmap(stitched, outW, outH, Bitmap.Config.ARGB_8888)
    }

    private fun inferTile(bridge: LitertBridge, interp: Interpreter, tile: Bitmap): Bitmap {
        val input = bridge.pixelsToUint8Buffer(tile)
        val dtype = interp.getOutputTensor(0).dataType()
        return when (dtype) {
            org.tensorflow.lite.DataType.FLOAT32 -> {
                val output = java.nio.ByteBuffer.allocateDirect(bridge.outputWidth * bridge.outputHeight * 3 * 4)
                    .order(java.nio.ByteOrder.nativeOrder())
                interp.run(input, output)
                output.rewind()
                val floats = FloatArray(bridge.outputWidth * bridge.outputHeight * 3)
                for (i in floats.indices) floats[i] = output.float
                bridge.floatsToBitmap(floats)
            }
            org.tensorflow.lite.DataType.UINT8 -> {
                val output = java.nio.ByteBuffer.allocateDirect(bridge.outputWidth * bridge.outputHeight * 3)
                interp.run(input, output)
                output.rewind()
                val bytes = ByteArray(bridge.outputWidth * bridge.outputHeight * 3)
                output.get(bytes)
                bridge.bytesToBitmap(bytes)
            }
            else -> throw IllegalStateException("Unsupported output dtype: $dtype")
        }
    }

    private data class TileResult(
        val offX: Int, val offY: Int, val bitmap: Bitmap,
    )
}
