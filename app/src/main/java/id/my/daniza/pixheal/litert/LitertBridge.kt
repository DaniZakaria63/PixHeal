package id.my.daniza.pixheal.litert

import android.content.res.AssetManager
import android.graphics.Bitmap
import java.io.Closeable

class LitertBridge : Closeable {

    enum class ModelType(val value: Int) {
        SUPER_RESOLUTION(0),
        INPAINTING(1)
    }

    private var nativeHandle: Long = 0

    val isLoaded get() = nativeHandle != 0L

    fun loadModel(assetManager: AssetManager, modelName: String, type: ModelType) {
        close()
        nativeHandle = nativeLoadModel(assetManager, modelName, type.value)
        if (nativeHandle == 0L) throw RuntimeException("Failed to load model: $modelName")
    }

    fun runSuperRes(bitmap: Bitmap, output: FloatArray): Boolean {
        checkLoaded()
        return nativeRunSuperRes(nativeHandle, bitmap, output)
    }

    fun runInpainting(imageBitmap: Bitmap, maskBitmap: Bitmap, output: FloatArray): Boolean {
        checkLoaded()
        return nativeRunInpainting(nativeHandle, imageBitmap, maskBitmap, output)
    }

    fun getInputShape(): IntArray? {
        checkLoaded()
        return nativeGetInputShape(nativeHandle)
    }

    override fun close() {
        if (nativeHandle != 0L) {
            nativeClose(nativeHandle)
            nativeHandle = 0
        }
    }

    private fun checkLoaded() {
        check(nativeHandle != 0L) { "Model not loaded" }
    }

    protected fun finalize() {
        if (nativeHandle != 0L) {
            nativeClose(nativeHandle)
        }
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
        modelType: Int
    ): Long

    private external fun nativeRunSuperRes(
        handle: Long,
        bitmap: Bitmap,
        output: FloatArray
    ): Boolean

    private external fun nativeRunInpainting(
        handle: Long,
        imageBitmap: Bitmap,
        maskBitmap: Bitmap,
        output: FloatArray
    ): Boolean

    private external fun nativeGetInputShape(handle: Long): IntArray?

    private external fun nativeClose(handle: Long)
}
