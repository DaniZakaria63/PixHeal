package id.my.daniza.pixheal.litert

import android.content.res.AssetManager
import android.graphics.Bitmap
import java.io.Closeable

class LitertBridge : Closeable {

    enum class ModelType(val value: Int) {
        SUPER_RESOLUTION(0),
        INPAINTING(1)
    }

    @Volatile
    private var nativeHandle: Long = 0

    // Cached from model load — shape doesn't change during lifetime
    @Volatile private var outputWidth: Int = 0
    @Volatile private var outputHeight: Int = 0

    val isLoaded get() = nativeHandle != 0L

    fun loadModel(assetManager: AssetManager, modelName: String, type: ModelType) {
        close()
        val outShape = IntArray(2)
        nativeHandle = nativeLoadModel(assetManager, modelName, type.value, outShape)
        if (nativeHandle == 0L) throw RuntimeException("Failed to load model: $modelName")
        outputWidth = outShape[0]
        outputHeight = outShape[1]
    }

    fun runSuperRes(bitmap: Bitmap): Bitmap? {
        val handle = nativeHandle
        if (handle == 0L) return null
        val tensor = nativeRunSuperRes(handle, bitmap) ?: return null
        return tensorToBitmap(tensor, outputWidth, outputHeight)
    }

    fun runInpainting(imageBitmap: Bitmap, maskBitmap: Bitmap): Bitmap? {
        val handle = nativeHandle
        if (handle == 0L) return null
        val tensor = nativeRunInpainting(handle, imageBitmap, maskBitmap) ?: return null
        return tensorToBitmap(tensor, outputWidth, outputHeight)
    }

    override fun close() {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0
            outputWidth = 0
            outputHeight = 0
            nativeClose(handle)
        }
    }

    // ── Bitmap conversion ────────────────────────────────────────────

    private fun tensorToBitmap(tensor: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = (tensor[i * 3 + 0] * 255).toInt().coerceIn(0, 255)
            val g = (tensor[i * 3 + 1] * 255).toInt().coerceIn(0, 255)
            val b = (tensor[i * 3 + 2] * 255).toInt().coerceIn(0, 255)
            pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    companion object {
        init {
            System.loadLibrary("pixheal")
        }
    }

    // ── JNI declarations ─────────────────────────────────────────────

    private external fun nativeLoadModel(
        assetManager: AssetManager,
        modelName: String,
        modelType: Int,
        outShape: IntArray
    ): Long

    private external fun nativeRunSuperRes(handle: Long, bitmap: Bitmap): FloatArray?

    private external fun nativeRunInpainting(
        handle: Long,
        imageBitmap: Bitmap,
        maskBitmap: Bitmap
    ): FloatArray?

    private external fun nativeClose(handle: Long)
}
