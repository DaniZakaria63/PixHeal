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

        val (scaled, scaledW, scaledH) = LitertBridge.scaleToFit(bitmap, scaleTarget)
        Timber.i("EsrganHandler: scaled to %dx%d", scaledW, scaledH)

        val interpreter = bridge.makeInterpreter()
        val shape = interpreter.getOutputTensor(0).shape()
        Timber.i("EsrganHandler: model output shape=%s", shape.contentToString())
        interpreter.close()

        val tilesX = ceil(scaledW.toFloat() / INPUT_SIZE).toInt()
        val tilesY = ceil(scaledH.toFloat() / INPUT_SIZE).toInt()
        val spacingX = if (tilesX > 1) (scaledW - INPUT_SIZE).toFloat() / (tilesX - 1) else 0f
        val spacingY = if (tilesY > 1) (scaledH - INPUT_SIZE).toFloat() / (tilesY - 1) else 0f
        val totalTiles = tilesX * tilesY
        Timber.i("EsrganHandler: %dx%d tiles (%d), spacing %.1fx%.1f", tilesX, tilesY, totalTiles, spacingX, spacingY)

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
                val offX = (tx * spacingX).toInt().coerceAtMost(scaledW - INPUT_SIZE)
                val offY = (ty * spacingY).toInt().coerceAtMost(scaledH - INPUT_SIZE)

                val tile = Bitmap.createBitmap(scaled, offX, offY, INPUT_SIZE, INPUT_SIZE)

                lateinit var outTile: Bitmap
                val local = bridge.makeInterpreter()
                try {
                    outTile = inferTile(local, tile)
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

    private fun inferTile(interp: Interpreter, tile: Bitmap): Bitmap {
        val input = LitertBridge.pixelsToUint8Buffer(tile)
        val shape = interp.getOutputTensor(0).shape()
        val isNchw = shape.size == 4 && shape[3] > 4
        val outH = if (isNchw) shape[2] else shape[1]
        val outW = if (isNchw) shape[3] else shape[2]
        val dtype = interp.getOutputTensor(0).dataType()
        return when (dtype) {
            org.tensorflow.lite.DataType.FLOAT32 -> {
                val output = java.nio.ByteBuffer.allocateDirect(outW * outH * 3 * 4)
                    .order(java.nio.ByteOrder.nativeOrder())
                interp.run(input, output)
                output.rewind()
                val floats = FloatArray(outW * outH * 3)
                for (i in floats.indices) floats[i] = output.float
                LitertBridge.floatsToBitmap(floats, outW, outH)
            }
            org.tensorflow.lite.DataType.UINT8 -> {
                val output = java.nio.ByteBuffer.allocateDirect(outW * outH * 3)
                interp.run(input, output)
                output.rewind()
                val bytes = ByteArray(outW * outH * 3)
                output.get(bytes)
                LitertBridge.bytesToBitmap(bytes, outW, outH)
            }
            else -> throw IllegalStateException("Unsupported output dtype: $dtype")
        }
    }

    private data class TileResult(
        val offX: Int, val offY: Int, val bitmap: Bitmap,
    )
}
