package id.my.daniza.litert

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale

@Singleton
class LitertBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Volatile
    var modelBytes: MappedByteBuffer? = null
        private set

    var parallelism: Int = 4
        private set

    companion object {
        const val INPUT_ESRGAN = 128
        private const val UPSCALE_ESRGAN = 4
        const val MODE_FAST = 128
        const val MODE_QUALITY = 256

        const val DEEPLABV3_INPUT = 520
        const val DEEPLABV3_NUM_CLASSES = 21
        const val DEEPLABV3_BG_CLASS = 0

        fun scaleToFit(bitmap: Bitmap, targetSize: Int): Triple<Bitmap, Int, Int> {
            val scale = if (bitmap.width >= bitmap.height)
                targetSize.toFloat() / bitmap.height
            else
                targetSize.toFloat() / bitmap.width
            val w = (bitmap.width * scale).toInt()
            val h = (bitmap.height * scale).toInt()
            return Triple(bitmap.scale(w, h, true), w, h)
        }

        fun padBitmap(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
            val padded = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(padded)
            canvas.drawBitmap(source, 0f, 0f, null)
            source.recycle()
            return padded
        }

        fun pixelsToUint8Buffer(bitmap: Bitmap): ByteBuffer {
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

        fun pixelsToFloatBuffer(bitmap: Bitmap): ByteBuffer {
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

        fun floatsToBitmap(data: FloatArray, width: Int, height: Int): Bitmap {
            val pixels = IntArray(width * height)
            for (i in pixels.indices) {
                val r = (data[i * 3 + 0] * 255f).toInt().coerceIn(0, 255)
                val g = (data[i * 3 + 1] * 255f).toInt().coerceIn(0, 255)
                val b = (data[i * 3 + 2] * 255f).toInt().coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        fun bytesToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
            val pixels = IntArray(width * height)
            for (i in pixels.indices) {
                val r = data[i * 3 + 0].toInt() and 0xFF
                val g = data[i * 3 + 1].toInt() and 0xFF
                val b = data[i * 3 + 2].toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
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
        modelBytes!!.rewind()
        initModel()
    }

    fun loadModelFromFile(file: File) {
        close()
        val input = FileInputStream(file)
        val channel = input.channel
        modelBytes = channel.map(
            FileChannel.MapMode.READ_ONLY, 0, file.length(),
        )
        modelBytes!!.rewind()
        initModel()
    }

    private fun initModel() {
        parallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val temp = makeInterpreter()
        try {
            val shape = temp.getOutputTensor(0).shape()
            val dtype = temp.getOutputTensor(0).dataType()
            Timber.i("initModel: output shape=%s, dtype=%s", shape.contentToString(), dtype)
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
        EsrganHandler.run(this@LitertBridge, bitmap, scaleTarget, progress)
    }

    suspend fun runInpainting(
        imageBitmap: Bitmap,
        maskBitmap: Bitmap,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        MiganHandler.run(this@LitertBridge, imageBitmap, maskBitmap, progress)
    }

    suspend fun runSegmentation(bitmap: Bitmap): IntArray? {
        return Deeplabv3Handler.run(this@LitertBridge, bitmap)
    }

    fun makeInterpreter(): Interpreter {
        val bytes = modelBytes ?: throw IllegalStateException("Model not loaded")
        bytes.rewind()
        return Interpreter(bytes, Interpreter.Options().apply {
            setNumThreads(1)
        })
    }

    data class ModelSession(
        val interpreter: Interpreter,
        val outputWidth: Int,
        val outputHeight: Int,
    ) {
        fun close() { interpreter.close() }
    }

    fun createSession(): ModelSession {
        val interp = makeInterpreter()
        val shape = interp.getOutputTensor(0).shape()
        Timber.i("createSession: output shape %s", shape.contentToString())
        return ModelSession(interp, shape[2], shape[1])
    }
}
