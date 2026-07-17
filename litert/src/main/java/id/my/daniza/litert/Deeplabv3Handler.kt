package id.my.daniza.litert

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.core.graphics.scale
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Deeplabv3Handler {
    private const val INPUT_SIZE = 520
    private const val NUM_CLASSES = 21

    suspend fun run(bridge: LitertBridge, bitmap: Bitmap): IntArray? = withContext(Dispatchers.IO) {
        val bytes = bridge.modelBytes ?: return@withContext null

        val w = bitmap.width
        val h = bitmap.height

        val scaled = bitmap.scale(INPUT_SIZE, INPUT_SIZE, true)
        val input = LitertBridge.pixelsToUint8Buffer(scaled)
        scaled.recycle()

        val interp = bridge.makeInterpreter()
        try {
            val output = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE)
                .order(ByteOrder.nativeOrder())
            interp.run(input, output)
            output.rewind()

            val rawMask = IntArray(INPUT_SIZE * INPUT_SIZE)
            for (i in rawMask.indices) {
                rawMask[i] = (output.get().toInt() and 0xFF).coerceAtMost(NUM_CLASSES - 1)
            }
            if (w != INPUT_SIZE || h != INPUT_SIZE) {
                val scaledMask = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
                val maskPixels = IntArray(INPUT_SIZE * INPUT_SIZE)
                for (i in rawMask.indices) {
                    maskPixels[i] = rawMask[i]
                }
                scaledMask.setPixels(maskPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
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
}
