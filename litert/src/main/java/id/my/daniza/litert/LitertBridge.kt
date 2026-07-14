package id.my.daniza.litert

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Singleton
class LitertBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Volatile
    private var modelBytes: MappedByteBuffer? = null

    @Volatile
    private var outputWidth: Int = 512
    @Volatile
    private var outputHeight: Int = 512

    @Volatile
    private var useFloatInput: Boolean = false

    private var parallelism: Int = 4

    companion object {
        const val INPUT_ESRGAN = 128
        private const val UPSCALE_ESRGAN = 4
        const val MODE_FAST = 128
        const val MODE_QUALITY = 256

        const val DEEPLABV3_INPUT = 520
        const val DEEPLABV3_NUM_CLASSES = 21
        const val DEEPLABV3_BG_CLASS = 0
    }

    val isLoaded get() = modelBytes != null

    fun loadModel(assetPath: String) {
        close()
        val fd = context.assets.openFd("$assetPath")
        val input = FileInputStream(fd.fileDescriptor)
        val channel = input.channel
        modelBytes = channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength,
        )
        initModel()
    }

    fun loadModelFromFile(file: File) {
        close()
        val input = FileInputStream(file)
        val channel = input.channel
        modelBytes = channel.map(
            FileChannel.MapMode.READ_ONLY, 0, file.length(),
        )
        initModel()
    }

    private fun initModel() {
        parallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        Timber.i("loadModel: parallelism=%d cores", parallelism)
        val temp = makeInterpreter()
        try {
            outputHeight = temp.getOutputTensor(0).shape()[1]
            outputWidth = temp.getOutputTensor(0).shape()[2]
            val inputType = temp.getInputTensor(0).dataType()
            useFloatInput = inputType == org.tensorflow.lite.DataType.FLOAT32
            Timber.i("loadModel: output %dx%d, input dtype=%s, useFloatInput=%b", outputWidth, outputHeight, inputType, useFloatInput)
        } finally {
            temp.close()
        }
    }

    fun close() {
        Timber.i("close")
        modelBytes = null
    }

    suspend fun runSuperRes(
        bitmap: Bitmap,
        scaleTarget: Int = MODE_FAST,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val bytes = modelBytes ?: return@coroutineScope null
        Timber.i("runSuperRes: input %dx%d, target=%d", bitmap.width, bitmap.height, scaleTarget)

        val (scaled, scaledW, scaledH) = scaleToFit(bitmap, scaleTarget)
        Timber.i("runSuperRes: scaled to %dx%d", scaledW, scaledH)

        val tilesX = ceil(scaledW.toFloat() / INPUT_ESRGAN).toInt()
        val tilesY = ceil(scaledH.toFloat() / INPUT_ESRGAN).toInt()
        val spacingX = if (tilesX > 1) (scaledW - INPUT_ESRGAN).toFloat() / (tilesX - 1) else 0f
        val spacingY = if (tilesY > 1) (scaledH - INPUT_ESRGAN).toFloat() / (tilesY - 1) else 0f
        val totalTiles = tilesX * tilesY
        Timber.i("runSuperRes: %dx%d tiles (%d), spacing %.1fx%.1f", tilesX, tilesY, totalTiles, spacingX, spacingY)

        val outW = scaledW * UPSCALE_ESRGAN
        val outH = scaledH * UPSCALE_ESRGAN

        val rAcc = IntArray(outW * outH)
        val gAcc = IntArray(outW * outH)
        val bAcc = IntArray(outW * outH)
        val weight = IntArray(outW * outH)
        val completed = AtomicInteger(0)

        val dispatcher = Dispatchers.Default.limitedParallelism(parallelism)

        val tileList = (0 until tilesY).flatMap { ty ->
            (0 until tilesX).map { tx -> Pair(tx, ty) }
        }

        val results = tileList.map { (tx, ty) ->
            async(dispatcher) {
                val offX = (tx * spacingX).toInt().coerceAtMost(scaledW - INPUT_ESRGAN)
                val offY = (ty * spacingY).toInt().coerceAtMost(scaledH - INPUT_ESRGAN)

                val tile = Bitmap.createBitmap(scaled, offX, offY, INPUT_ESRGAN, INPUT_ESRGAN)

                lateinit var outTile: Bitmap
                val local = makeInterpreter()
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
            val tilePixels = IntArray(outputWidth * outputHeight)
            r.bitmap.getPixels(tilePixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
            r.bitmap.recycle()

            val dstX = r.offX * UPSCALE_ESRGAN
            val dstY = r.offY * UPSCALE_ESRGAN

            for (tiley in 0 until outputHeight) {
                for (tilex in 0 until outputWidth) {
                    val px = dstX + tilex
                    val py = dstY + tiley
                    if (px < outW && py < outH) {
                        val pixel = tilePixels[tiley * outputWidth + tilex]
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
        Timber.i("runSuperRes: done — %dx%d", outW, outH)
        Bitmap.createBitmap(stitched, outW, outH, Bitmap.Config.ARGB_8888)
    }

    suspend fun runInpainting(
        imageBitmap: Bitmap,
        maskBitmap: Bitmap,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val bytes = modelBytes ?: return@coroutineScope null
        val tileSize = outputWidth

        val origW = imageBitmap.width
        val origH = imageBitmap.height
        Timber.i("runInpainting: image %dx%d, mask %dx%d", origW, origH, maskBitmap.width, maskBitmap.height)

        val (image, w, h) = scaleToFit(imageBitmap, tileSize)
        Timber.i("runInpainting: scaled to %dx%d", w, h)

        val maskScaled = maskBitmap.scale(w, h, false)

        val tilesX = ceil(w.toFloat() / tileSize).toInt()
        val tilesY = ceil(h.toFloat() / tileSize).toInt()
        val spacingX = if (tilesX > 1) (w - tileSize).toFloat() / (tilesX - 1) else 0f
        val spacingY = if (tilesY > 1) (h - tileSize).toFloat() / (tilesY - 1) else 0f
        val totalTiles = tilesX * tilesY
        Timber.i("runInpainting: %dx%d tiles (%d), spacing %.1fx%.1f", tilesX, tilesY, totalTiles, spacingX, spacingY)

        val rAcc = IntArray(w * h)
        val gAcc = IntArray(w * h)
        val bAcc = IntArray(w * h)
        val weight = IntArray(w * h)

        val interpreter = makeInterpreter()
        try {
            for (ty in 0 until tilesY) {
                for (tx in 0 until tilesX) {
                    val offX = (tx * spacingX).toInt().coerceAtMost(w - tileSize)
                    val offY = (ty * spacingY).toInt().coerceAtMost(h - tileSize)

                    val imgTile = Bitmap.createBitmap(image, offX, offY, tileSize, tileSize)
                    val maskTile = Bitmap.createBitmap(maskScaled, offX, offY, tileSize, tileSize)

                    val outTile = inferInpaintingTile(interpreter, imgTile, maskTile)
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
        Timber.i("runInpainting: done — %dx%d, scaling back to %dx%d", w, h, origW, origH)
        val upscaled = Bitmap.createScaledBitmap(result, origW, origH, true)
        result.recycle()
        upscaled
    }

    suspend fun runSegmentation(bitmap: Bitmap): IntArray? = withContext(Dispatchers.IO) {
        val bytes = modelBytes ?: return@withContext null

        val w = bitmap.width
        val h = bitmap.height

        val scaled = bitmap.scale(DEEPLABV3_INPUT, DEEPLABV3_INPUT, true)
        val input = pixelsToUint8Buffer(scaled)
        scaled.recycle()

        val interp = makeInterpreter()
        try {
            val output = ByteBuffer.allocateDirect(DEEPLABV3_INPUT * DEEPLABV3_INPUT)
                .order(ByteOrder.nativeOrder())
            interp.run(input, output)
            output.rewind()

            val rawMask = IntArray(DEEPLABV3_INPUT * DEEPLABV3_INPUT)
            for (i in rawMask.indices) {
                rawMask[i] = (output.get().toInt() and 0xFF).coerceAtMost(DEEPLABV3_NUM_CLASSES - 1)
            }
            if (w != DEEPLABV3_INPUT || h != DEEPLABV3_INPUT) {
                val scaledMask = Bitmap.createBitmap(DEEPLABV3_INPUT, DEEPLABV3_INPUT, Bitmap.Config.ARGB_8888)
                val maskPixels = IntArray(DEEPLABV3_INPUT * DEEPLABV3_INPUT)
                for (i in rawMask.indices) {
                    maskPixels[i] = rawMask[i]
                }
                scaledMask.setPixels(maskPixels, 0, DEEPLABV3_INPUT, 0, 0, DEEPLABV3_INPUT, DEEPLABV3_INPUT)
                val resized = Bitmap.createScaledBitmap(scaledMask, w, h, false)
                scaledMask.recycle()
                val result = IntArray(w * h)
                resized.getPixels(result, 0, w, 0, 0, w, h)
                resized.recycle()
                result
            } else {
                rawMask
            }
        } finally {
            interp.close()
        }
    }

    private fun padBitmap(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val padded = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(padded)
        canvas.drawBitmap(source, 0f, 0f, null)
        source.recycle()
        return padded
    }

    private fun makeInterpreter(): Interpreter {
        val bytes = modelBytes ?: throw IllegalStateException("Model not loaded")
        return Interpreter(bytes, Interpreter.Options().apply {
            setNumThreads(1)
        })
    }

    private fun scaleToFit(bitmap: Bitmap, targetSize: Int): Triple<Bitmap, Int, Int> {
        val scale = if (bitmap.width >= bitmap.height)
            targetSize.toFloat() / bitmap.height
        else
            targetSize.toFloat() / bitmap.width
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Triple(bitmap.scale(w, h, true), w, h)
    }

    private fun inferTile(interp: Interpreter, tile: Bitmap): Bitmap {
        val input = pixelsToUint8Buffer(tile)
        val dtype = interp.getOutputTensor(0).dataType()
        return when (dtype) {
            org.tensorflow.lite.DataType.FLOAT32 -> {
                val output = ByteBuffer.allocateDirect(outputWidth * outputHeight * 3 * 4)
                    .order(ByteOrder.nativeOrder())
                interp.run(input, output)
                output.rewind()
                val floats = FloatArray(outputWidth * outputHeight * 3)
                for (i in floats.indices) floats[i] = output.float
                floatsToBitmap(floats)
            }
            org.tensorflow.lite.DataType.UINT8 -> {
                val output = ByteBuffer.allocateDirect(outputWidth * outputHeight * 3)
                interp.run(input, output)
                output.rewind()
                val bytes = ByteArray(outputWidth * outputHeight * 3)
                output.get(bytes)
                bytesToBitmap(bytes)
            }
            else -> throw IllegalStateException("Unsupported output dtype: $dtype")
        }
    }

    private fun inferInpaintingTile(interp: Interpreter, imgTile: Bitmap, maskTile: Bitmap): Bitmap {
        val imageInput: Any
        val maskInput: Any
        if (useFloatInput) {
            imageInput = pixelsToFloatBuffer(imgTile)
            maskInput = maskToFloatBuffer(maskTile)
        } else {
            imageInput = pixelsToUint8Buffer(imgTile)
            maskInput = maskToUint8Buffer(maskTile)
        }
        val dtype = interp.getOutputTensor(0).dataType()
        return when (dtype) {
            org.tensorflow.lite.DataType.FLOAT32 -> {
                val output = ByteBuffer.allocateDirect(outputWidth * outputHeight * 3 * 4)
                    .order(ByteOrder.nativeOrder())
                interp.runForMultipleInputsOutputs(arrayOf(imageInput, maskInput), mapOf(0 to output))
                output.rewind()
                val floats = FloatArray(outputWidth * outputHeight * 3)
                for (i in floats.indices) floats[i] = output.float
                floatsToBitmap(floats)
            }
            org.tensorflow.lite.DataType.UINT8 -> {
                val output = ByteBuffer.allocateDirect(outputWidth * outputHeight * 3)
                interp.runForMultipleInputsOutputs(arrayOf(imageInput, maskInput), mapOf(0 to output))
                output.rewind()
                val bytes = ByteArray(outputWidth * outputHeight * 3)
                output.get(bytes)
                bytesToBitmap(bytes)
            }
            else -> throw IllegalStateException("Unsupported output dtype: $dtype")
        }
    }

    private fun pixelsToUint8Buffer(bitmap: Bitmap): ByteBuffer {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buffer = ByteBuffer.allocateDirect(w * h * 3).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buffer.put(((p shr 16) and 0xFF).toByte())
            buffer.put(((p shr 8) and 0xFF).toByte())
            buffer.put((p and 0xFF).toByte())
        }
        buffer.rewind()
        return buffer
    }

    private fun maskToUint8Buffer(bitmap: Bitmap): ByteBuffer {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buffer = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            val gray = ((p shr 16) and 0xFF) * 0.299f +
                       ((p shr 8) and 0xFF) * 0.587f +
                       (p and 0xFF) * 0.114f
            buffer.put(gray.toInt().coerceIn(0, 255).toByte())
        }
        buffer.rewind()
        return buffer
    }

    private fun pixelsToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buffer = ByteBuffer.allocateDirect(w * h * 3 * 4).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buffer.putFloat(((p shr 16) and 0xFF) / 255f)
            buffer.putFloat(((p shr 8) and 0xFF) / 255f)
            buffer.putFloat((p and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private fun maskToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            val gray = ((p shr 16) and 0xFF) * 0.299f +
                       ((p shr 8) and 0xFF) * 0.587f +
                       (p and 0xFF) * 0.114f
            buffer.putFloat(gray / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private fun floatsToBitmap(data: FloatArray): Bitmap {
        val pixels = IntArray(outputWidth * outputHeight)
        for (i in pixels.indices) {
            val r = (data[i * 3 + 0] * 255f).toInt().coerceIn(0, 255)
            val g = (data[i * 3 + 1] * 255f).toInt().coerceIn(0, 255)
            val b = (data[i * 3 + 2] * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    }

    private fun bytesToBitmap(data: ByteArray): Bitmap {
        val pixels = IntArray(outputWidth * outputHeight)
        for (i in pixels.indices) {
            val r = data[i * 3 + 0].toInt() and 0xFF
            val g = data[i * 3 + 1].toInt() and 0xFF
            val b = data[i * 3 + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    }

    private data class TileResult(
        val offX: Int, val offY: Int, val bitmap: Bitmap,
    )
}
