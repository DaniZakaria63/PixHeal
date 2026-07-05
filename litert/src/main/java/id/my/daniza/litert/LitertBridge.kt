package id.my.daniza.litert

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LitertBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile
    private var interpreter: Interpreter? = null

    val isLoaded get() = interpreter != null

    private var outputWidth: Int = 512
    private var outputHeight: Int = 512

    fun loadModel(assetPath: String) {
        close()
        val fileDescriptor = context.assets.openFd(assetPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val channel = inputStream.channel
        val byteBuffer = channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
        interpreter = Interpreter(byteBuffer, Interpreter.Options().apply {
            setNumThreads(4)
        })
    }

    fun runSuperRes(bitmap: Bitmap): Bitmap? {
        val interp = interpreter ?: return null

        val input = preprocessUint8(bitmap, 128, 128, 3)

        val outputSize = outputWidth * outputHeight * 3 * 4
        val output = ByteBuffer.allocateDirect(outputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        interp.run(input, output)

        output.rewind()
        val floats = FloatArray(outputWidth * outputHeight * 3)
        for (i in floats.indices) floats[i] = output.float
        return floatsToBitmap(floats)
    }

    fun runInpainting(imageBitmap: Bitmap, maskBitmap: Bitmap): Bitmap? {
        val interp = interpreter ?: return null

        val imageInput = preprocessUint8(imageBitmap, 512, 512, 3)
        val maskInput = preprocessUint8(maskBitmap, 512, 512, 1)

        val outputSize = outputWidth * outputHeight * 3 * 4
        val output = ByteBuffer.allocateDirect(outputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        interp.run(arrayOf(imageInput, maskInput), mapOf(0 to output))

        output.rewind()
        val floats = FloatArray(outputWidth * outputHeight * 3)
        for (i in floats.indices) floats[i] = output.float
        return floatsToBitmap(floats)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun preprocessUint8(bitmap: Bitmap, targetWidth: Int, targetHeight: Int, channels: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)

        val buffer = ByteBuffer.allocateDirect(targetWidth * targetHeight * channels).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(targetWidth * targetHeight)
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        scaled.recycle()

        if (channels == 3) {
            for (pixel in pixels) {
                buffer.put(((pixel shr 16) and 0xFF).toByte())
                buffer.put(((pixel shr 8) and 0xFF).toByte())
                buffer.put((pixel and 0xFF).toByte())
            }
        } else {
            for (pixel in pixels) {
                val gray = ((pixel shr 16) and 0xFF) * 0.299f +
                           ((pixel shr 8) and 0xFF) * 0.587f +
                           (pixel and 0xFF) * 0.114f
                buffer.put(gray.toInt().coerceIn(0, 255).toByte())
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun floatsToBitmap(data: FloatArray): Bitmap {
        val pixels = IntArray(outputWidth * outputHeight)
        for (i in pixels.indices) {
            val r = (data[i * 3 + 0] * 255).toInt().coerceIn(0, 255)
            val g = (data[i * 3 + 1] * 255).toInt().coerceIn(0, 255)
            val b = (data[i * 3 + 2] * 255).toInt().coerceIn(0, 255)
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    }

    enum class ModelType(val value: Int) {
        SUPER_RESOLUTION(0),
        INPAINTING(1)
    }
}
