package id.my.daniza.litert.handler

import android.graphics.Bitmap
import androidx.core.graphics.scale
import id.my.daniza.litert.data.BitmapOps
import id.my.daniza.litert.LitertBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

            if (w != size || h != size) {
                val scaledMask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val maskPixels = IntArray(size * size)
                for (i in rawMask.indices) {
                    maskPixels[i] = rawMask[i]
                }
                scaledMask.setPixels(maskPixels, 0, size, 0, 0, size, size)
                val resized = Bitmap.createScaledBitmap(scaledMask, w, h, false)
                scaledMask.recycle()
                val result = IntArray(w * h)
                resized.getPixels(result, 0, w, 0, 0, w, h)
                resized.recycle()
                result
            } else {
                rawMask
            }
        }
}