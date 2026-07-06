package id.my.daniza.litert

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

import timber.log.Timber
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale
import kotlin.math.ceil

/**
 * LiteRT inference engine for ESRGAN and AOT-GAN models on Android.
 *
 * ## Model contract
 * - Input:  128×128 uint8 RGB (ESRGAN) or 512×512 uint8 RGB + 512×512 uint8 mask (AOT-GAN)
 * - Output: 512×512 float32 / uint8 RGB (4× upscale for ESRGAN, 1:1 for AOT-GAN)
 * - Tensor layout: NHWC (batch, height, width, channels)
 *
 * ## Handling non-square images
 *
 * The model operates on **square** (1:1) inputs. When the source image is not square
 * (e.g. portrait 9:16 or landscape 16:9), we use **overlapping tiling**:
 *
 * 1. Scale the image proportionally so the *shorter* side equals the model input size (128).
 * 2. Split the *longer* side into overlapping 128×128 tiles with equal spacing.
 * 3. Run inference on each tile independently → each produces a 512×512 output.
 * 4. Stitch the output tiles into a single canvas at the model's 4× upscale.
 * 5. Average overlapping pixels for seamless transitions between tiles.
 *
 * ### Example — portrait 9:16 (1080×1920)
 * ```
 *   Step 1: scale by width → 128×227 (proportional, no distortion)
 *   Step 2: ceil(227/128) = 2 tiles, spacing = (227-128)/1 = 99 px
 *            Tile 0: rows   0..127   on scaled source
 *            Tile 1: rows  99..226   on scaled source    (28 px overlap)
 *   Step 3: each tile 128×128 → model → 512×512
 *   Step 4: canvas 512 wide × (227×4) = 908 tall
 *            Tile 0 output at y =   0 (rows   0..511)
 *            Tile 1 output at y = 396 (rows 396..907)   (99×4 → overlap rows 396..511)
 *   Step 5: rows 396..511 averaged between both tiles → seamless blend
 * ```
 *
 * ### Quality limitations
 * The aggressive downscale to 128 px loses most of the original resolution for large
 * photos (e.g. a 12 MP image → 128×227). The model can only add detail at 4× its
 * input scale, so the output is sharper *at the model's scale* but should not be
 * further resized to the original size — doing so would introduce interpolation blur.
 */
@Singleton
class LitertBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var interpreter: Interpreter? = null

    val isLoaded get() = interpreter != null

    private var outputWidth: Int = 512
    private var outputHeight: Int = 512

    companion object {
        private const val INPUT_ESRGAN = 128
        private const val UPSCALE_ESRGAN = 4
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun loadModel(assetPath: String) {
        close()
        val fileDescriptor = context.assets.openFd(assetPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val channel = inputStream.channel
        val byteBuffer = channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength,
        )
        val start = System.currentTimeMillis()
        interpreter = Interpreter(byteBuffer, Interpreter.Options().apply {
            setNumThreads(4)
        })
        val elapsed = System.currentTimeMillis() - start
        Timber.i("loadModel: created in %dms", elapsed)

        val interp = interpreter ?: return
        val inputCount = interp.inputTensorCount
        val outputCount = interp.outputTensorCount
        Timber.i("loadModel: %d input(s), %d output(s)", inputCount, outputCount)
        for (i in 0 until inputCount) {
            val t = interp.getInputTensor(i)
            Timber.i("loadModel:  input[%d]  shape=[%s]  dtype=%s", i, t.shape().joinToString("x"), t.dataType())
        }
        for (i in 0 until outputCount) {
            val t = interp.getOutputTensor(i)
            Timber.i("loadModel: output[%d]  shape=[%s]  dtype=%s", i, t.shape().joinToString("x"), t.dataType())
            if (t.numDimensions() >= 4) {
                outputHeight = t.shape()[1]
                outputWidth = t.shape()[2]
            }
        }
        Timber.i("loadModel: using output size %dx%d", outputWidth, outputHeight)
    }

    fun close() {
        Timber.i("close")
        interpreter?.close()
        interpreter = null
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Run ESRGAN 4× super-resolution on [bitmap].
     *
     * For non-square images the input is tiled with overlapping tiles; see class
     * doc for the full algorithm.  For square (1:1) images a single tile is used.
     *
     * @return upscaled Bitmap maintaining the original aspect ratio, or null on error.
     */
    fun runSuperRes(bitmap: Bitmap): Bitmap? {
        val interp = interpreter ?: return null
        val dtype = interp.getOutputTensor(0).dataType()
        Timber.i("runSuperRes: input %dx%d, dtype=%s", bitmap.width, bitmap.height, dtype)

        // 1. Scale so the shorter side = model input size (128).
        val (scaled, scaledW, scaledH) = scaleToFit(bitmap, INPUT_ESRGAN)
        Timber.i("runSuperRes: scaled to %dx%d", scaledW, scaledH)

        // 2. Determine tiling parameters.
        val horizontal = scaledW >= scaledH
        val longDim = if (horizontal) scaledW else scaledH
        val shortDim = if (horizontal) scaledH else scaledW
        val numTiles = ceil(longDim.toFloat() / INPUT_ESRGAN).toInt()
        val spacing = if (numTiles > 1) (longDim - INPUT_ESRGAN).toFloat() / (numTiles - 1) else 0f

        // 3. Output canvas dimensions (4× upscale).
        val outShort = shortDim * UPSCALE_ESRGAN
        val outLong = longDim * UPSCALE_ESRGAN
        val outW = if (horizontal) outLong else outShort
        val outH = if (horizontal) outShort else outLong
        Timber.i("runSuperRes: %d tiles, %.1f px spacing, canvas %dx%d", numTiles, spacing, outW, outH)

        // 4. Process each tile and accumulate RGB + weight.
        val rAcc = IntArray(outW * outH)
        val gAcc = IntArray(outW * outH)
        val bAcc = IntArray(outW * outH)
        val weight = IntArray(outW * outH)

        for (t in 0 until numTiles) {
            val offset = (t * spacing).toInt()
            val maxOffset = if (horizontal) scaled.width - INPUT_ESRGAN else scaled.height - INPUT_ESRGAN
            val safe = offset.coerceAtMost(maxOffset)
            val tileX = if (horizontal) safe else 0
            val tileY = if (horizontal) 0 else safe

            val tile = Bitmap.createBitmap(scaled, tileX, tileY, INPUT_ESRGAN, INPUT_ESRGAN)
            val outTile = inferTile(interp, tile, interpolationDType = dtype)
            tile.recycle()

            val tilePixels = IntArray(outputWidth * outputHeight)
            outTile.getPixels(tilePixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
            outTile.recycle()

            val dstX = tileX * UPSCALE_ESRGAN
            val dstY = tileY * UPSCALE_ESRGAN

            for (ty in 0 until outputHeight) {
                for (tx in 0 until outputWidth) {
                    val px = dstX + tx
                    val py = dstY + ty
                    if (px < outW && py < outH) {
                        val pixel = tilePixels[ty * outputWidth + tx]
                        val idx = py * outW + px
                        rAcc[idx] += (pixel shr 16) and 0xFF
                        gAcc[idx] += (pixel shr 8) and 0xFF
                        bAcc[idx] += pixel and 0xFF
                        weight[idx] += 1
                    }
                }
            }
        }
        scaled.recycle()

        // 5. Normalise by accumulation weight (averages overlapping pixels).
        val stitched = IntArray(outW * outH)
        for (i in stitched.indices) {
            if (weight[i] > 0) {
                val r = (rAcc[i] / weight[i]).coerceIn(0, 255)
                val g = (gAcc[i] / weight[i]).coerceIn(0, 255)
                val b = (bAcc[i] / weight[i]).coerceIn(0, 255)
                stitched[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val result = Bitmap.createBitmap(stitched, outW, outH, Bitmap.Config.ARGB_8888)
        Timber.i("runSuperRes: done — %dx%d", result.width, result.height)
        return result
    }

    /**
     * Run AOT-GAN inpainting on [imageBitmap] using [maskBitmap].
     *
     * Both image and mask are tiled identically using the same overlap parameters,
     * so the spatial correspondence is preserved.
     */
    fun runInpainting(imageBitmap: Bitmap, maskBitmap: Bitmap): Bitmap? {
        val interp = interpreter ?: return null
        val inputTarget = outputWidth // 512 for AOT-GAN
        val dtype = interp.getOutputTensor(0).dataType()
        Timber.i("runInpainting: image %dx%d, mask %dx%d, dtype=%s",
            imageBitmap.width, imageBitmap.height, maskBitmap.width, maskBitmap.height, dtype)

        // Scale both image and mask identically.
        val (imgScaled, scaledW, scaledH) = scaleToFit(imageBitmap, inputTarget)
        val maskScaled = maskBitmap.scale(scaledW, scaledH, true)
        Timber.i("runInpainting: scaled to %dx%d", scaledW, scaledH)

        val horizontal = scaledW >= scaledH
        val longDim = if (horizontal) scaledW else scaledH
        val shortDim = if (horizontal) scaledH else scaledW
        val numTiles = ceil(longDim.toFloat() / inputTarget).toInt()
        val spacing = if (numTiles > 1) (longDim - inputTarget).toFloat() / (numTiles - 1) else 0f

        val outShort = shortDim
        val outLong = longDim
        val outW = if (horizontal) outLong else outShort
        val outH = if (horizontal) outShort else outLong
        Timber.i("runInpainting: %d tiles, %.1f px spacing, canvas %dx%d", numTiles, spacing, outW, outH)

        val rAcc = IntArray(outW * outH)
        val gAcc = IntArray(outW * outH)
        val bAcc = IntArray(outW * outH)
        val weight = IntArray(outW * outH)

        for (t in 0 until numTiles) {
            val offset = (t * spacing).toInt()
            val maxOffset = if (horizontal) imgScaled.width - inputTarget else imgScaled.height - inputTarget
            val safe = offset.coerceAtMost(maxOffset)
            val tileX = if (horizontal) safe else 0
            val tileY = if (horizontal) 0 else safe

            val imgTile = Bitmap.createBitmap(imgScaled, tileX, tileY, inputTarget, inputTarget)
            val maskTile = Bitmap.createBitmap(maskScaled, tileX, tileY, inputTarget, inputTarget)

            val outTile = inferInpaintingTile(interp, imgTile, maskTile, dtype)
            imgTile.recycle()
            maskTile.recycle()

            val tilePixels = IntArray(inputTarget * inputTarget)
            outTile.getPixels(tilePixels, 0, inputTarget, 0, 0, inputTarget, inputTarget)
            outTile.recycle()

            for (ty in 0 until inputTarget) {
                for (tx in 0 until inputTarget) {
                    val px = tileX + tx
                    val py = tileY + ty
                    if (px < outW && py < outH) {
                        val pixel = tilePixels[ty * inputTarget + tx]
                        val idx = py * outW + px
                        rAcc[idx] += (pixel shr 16) and 0xFF
                        gAcc[idx] += (pixel shr 8) and 0xFF
                        bAcc[idx] += pixel and 0xFF
                        weight[idx] += 1
                    }
                }
            }
        }
        imgScaled.recycle()
        maskScaled.recycle()

        val stitched = IntArray(outW * outH)
        for (i in stitched.indices) {
            if (weight[i] > 0) {
                val r = (rAcc[i] / weight[i]).coerceIn(0, 255)
                val g = (gAcc[i] / weight[i]).coerceIn(0, 255)
                val b = (bAcc[i] / weight[i]).coerceIn(0, 255)
                stitched[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val result = Bitmap.createBitmap(stitched, outW, outH, Bitmap.Config.ARGB_8888)
        Timber.i("runInpainting: done — %dx%d", result.width, result.height)
        return result
    }

    // ── Helpers — image sizing ─────────────────────────────────────────────

    /** Scale proportionally so the shorter side = [targetSize]. */
    private fun scaleToFit(
        bitmap: Bitmap,
        targetSize: Int,
    ): Triple<Bitmap, Int, Int> {
        val scale = if (bitmap.width >= bitmap.height)
            targetSize.toFloat() / bitmap.height
        else
            targetSize.toFloat() / bitmap.width
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Triple(bitmap.scale(w, h, true), w, h)
    }

    // ── Helpers — tile inference ───────────────────────────────────────────

    /** Run a single 128×128 or 512×512 tile through the model. */
    private fun inferTile(
        interp: Interpreter,
        tile: Bitmap,
        interpolationDType: DataType,
    ): Bitmap {
        val input = pixelsToUint8Buffer(tile)
        return when (interpolationDType) {
            DataType.FLOAT32 -> {
                val output = ByteBuffer.allocateDirect(outputWidth * outputHeight * 3 * 4)
                    .order(ByteOrder.nativeOrder())
                interp.run(input, output)
                output.rewind()
                val floats = FloatArray(outputWidth * outputHeight * 3)
                for (i in floats.indices) floats[i] = output.float
                floatsToBitmap(floats)
            }
            DataType.UINT8 -> {
                val output = ByteBuffer.allocateDirect(outputWidth * outputHeight * 3)
                interp.run(input, output)
                output.rewind()
                val bytes = ByteArray(outputWidth * outputHeight * 3)
                output.get(bytes)
                bytesToBitmap(bytes)
            }
            else -> throw IllegalStateException("Unsupported output dtype: $interpolationDType")
        }
    }

    /** Run a single 512×512 image + mask tile through the inpainting model. */
    private fun inferInpaintingTile(
        interp: Interpreter,
        imgTile: Bitmap,
        maskTile: Bitmap,
        dtype: DataType,
    ): Bitmap {
        val imageInput = pixelsToUint8Buffer(imgTile)
        val maskInput = maskToUint8Buffer(maskTile)
        return when (dtype) {
            DataType.FLOAT32 -> {
                val output = ByteBuffer.allocateDirect(outputWidth * outputHeight * 3 * 4)
                    .order(ByteOrder.nativeOrder())
                interp.run(arrayOf(imageInput, maskInput), mapOf(0 to output))
                output.rewind()
                val floats = FloatArray(outputWidth * outputHeight * 3)
                for (i in floats.indices) floats[i] = output.float
                floatsToBitmap(floats)
            }
            DataType.UINT8 -> {
                val output = ByteBuffer.allocateDirect(outputWidth * outputHeight * 3)
                interp.run(arrayOf(imageInput, maskInput), mapOf(0 to output))
                output.rewind()
                val bytes = ByteArray(outputWidth * outputHeight * 3)
                output.get(bytes)
                bytesToBitmap(bytes)
            }
            else -> throw IllegalStateException("Unsupported output dtype: $dtype")
        }
    }

    // ── Helpers — buffer I/O ───────────────────────────────────────────────

    /** Convert an ARGB_8888 Bitmap to a direct uint8 ByteBuffer (RGB, NHWC). */
    private fun pixelsToUint8Buffer(bitmap: Bitmap): ByteBuffer {
        val pw = bitmap.width
        val ph = bitmap.height
        val pixels = IntArray(pw * ph)
        bitmap.getPixels(pixels, 0, pw, 0, 0, pw, ph)

        val buffer = ByteBuffer.allocateDirect(pw * ph * 3).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buffer.put(((p shr 16) and 0xFF).toByte())  // R
            buffer.put(((p shr 8) and 0xFF).toByte())   // G
            buffer.put((p and 0xFF).toByte())            // B
        }
        buffer.rewind()
        return buffer
    }

    /** Convert an ARGB_8888 mask Bitmap to a single-channel uint8 buffer (ITU-R BT.601 luma). */
    private fun maskToUint8Buffer(bitmap: Bitmap): ByteBuffer {
        val pw = bitmap.width
        val ph = bitmap.height
        val pixels = IntArray(pw * ph)
        bitmap.getPixels(pixels, 0, pw, 0, 0, pw, ph)

        val buffer = ByteBuffer.allocateDirect(pw * ph).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            val gray = ((p shr 16) and 0xFF) * 0.299f +
                       ((p shr 8) and 0xFF) * 0.587f +
                       (p and 0xFF) * 0.114f
            buffer.put(gray.toInt().coerceIn(0, 255).toByte())
        }
        buffer.rewind()
        return buffer
    }

    /** Build ARGB_8888 bitmap from FLOAT32 RGB data in [0, 1] range. */
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

    /** Build ARGB_8888 bitmap from raw uint8 RGB bytes. */
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
}
