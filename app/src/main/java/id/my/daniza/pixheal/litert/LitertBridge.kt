package id.my.daniza.pixheal.litert

import android.content.res.AssetManager
import android.graphics.Bitmap

object LitertBridge {

    enum class ModelType(val value: Int) {
        SUPER_RESOLUTION(0),
        INPAINTING(1)
    }

    init {
        System.loadLibrary("pixheal")
    }

    external fun nativeLoadModel(
        assetManager: AssetManager,
        modelName: String,
        modelType: Int
    ): Boolean

    external fun nativeRunSuperRes(bitmap: Bitmap, output: FloatArray): Boolean

    external fun nativeRunInpainting(
        imageBitmap: Bitmap,
        maskBitmap: Bitmap,
        output: FloatArray
    ): Boolean

    external fun nativeGetInputShape(): IntArray?

    external fun nativeClose()
}
