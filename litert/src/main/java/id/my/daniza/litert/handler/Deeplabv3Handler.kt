package id.my.daniza.litert.handler

import android.graphics.Bitmap
import androidx.core.graphics.scale
import id.my.daniza.litert.data.BitmapOps
import id.my.daniza.litert.LitertBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap

/**
 * DeepLabV3+ (MobileNet) segmentation handler.
 *
 * ## What it does
 * Produces a per-pixel class map (21 classes, PASCAL VOC) used downstream for background
 * removal. The source bitmap is scaled to a fixed [ModelConfig.DEEPLABV3_DEFAULT.inputWidth]
 * (520x520) square, run once through the model (no tiling — single forward pass), and the
 * resulting class indices are resized back to the original bitmap dimensions.
 *
 * ## I/O buffers
 * - Input:  UINT8 RGB, 520x520x3 bytes (via [BitmapOps.pixelsToUint8]).
 * - Output: single-channel class index per pixel, 520x520. Read as UINT8
 *           (config [id.my.daniza.litert.data.TensorDataType.UINT8]); each byte is coerced to
 *           [NUM_CLASSES]-1 (20) so out-of-range values stay valid. Returned as [IntArray]
 *           of class ids (length = original w*h).
 *
 * ## Notes
 * This handler is single-pass and runs on [Dispatchers.IO]. The returned [IntArray] is the
 * raw class index map consumed by EditEffectManager.removeBackground / removeBackgroundViaMask.
 */
object Deeplabv3Handler {
    private const val NUM_CLASSES = 21

    suspend fun run(session: LitertBridge.ModelSession, bitmap: Bitmap): IntArray? =
        withContext(Dispatchers.IO) {
            val size = session.config.inputWidth

            val w = bitmap.width
            val h = bitmap.height

            val scaled = bitmap.scale(size, size, true)
            val input = session.model.createInputBuffers().first()
            val outBuf = session.model.createOutputBuffers().first()
            val raw = try {
                input.writeInt8(BitmapOps.pixelsToUint8(scaled))
                session.model.run(listOf(input), listOf(outBuf))
                outBuf.readInt8()
            } finally {
                input.close()
                outBuf.close()
            }
            scaled.recycle()

            val rawMask = IntArray(size * size)
            for (i in rawMask.indices) {
                rawMask[i] = (raw[i].toInt() and 0xFF).coerceAtMost(NUM_CLASSES - 1)
            }

            if (w == size || h == size) return@withContext rawMask

            val scaledMask = createBitmap(size, size)
            val maskPixels = IntArray(size * size)
            for (i in rawMask.indices) {
                maskPixels[i] = rawMask[i]
            }
            scaledMask.setPixels(maskPixels, 0, size, 0, 0, size, size)
            val resized = scaledMask.scale(w, h, false)
            scaledMask.recycle()
            val result = IntArray(w * h)
            resized.getPixels(result, 0, w, 0, 0, w, h)
            resized.recycle()
            result
        }
}