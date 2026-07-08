package id.my.daniza.pixheal.data.editing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.my.daniza.litert.LitertBridge
import id.my.daniza.local.data.FileNameObj
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class EffectResult {
    data class Success(val bitmap: Bitmap) : EffectResult()
    data class Error(val message: String) : EffectResult()
}

@Singleton
class EditEffectManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val litertBridge: LitertBridge,
) {
    private val mutex = Mutex()
    private var loadedModel: LoadedModel = LoadedModel.NONE

    private enum class LoadedModel { NONE, ESRGAN, AOTGAN }

    suspend fun enhance(uri: Uri, qualityMode: Boolean = false): EffectResult = withContext(Dispatchers.IO) {
        val scaleTarget = if (qualityMode) LitertBridge.MODE_QUALITY else LitertBridge.MODE_FAST
        Timber.i("enhance: %s mode (target=%d)", if (qualityMode) "quality" else "fast", scaleTarget)
        val start = System.currentTimeMillis()

        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        Timber.i("enhance: decoded %dx%d %s", bitmap.width, bitmap.height, bitmap.config)

        val loadErr = ensureModelLoaded(LoadedModel.ESRGAN)
        if (loadErr != null) return@withContext EffectResult.Error("AI model failed to start: $loadErr")

        val result = litertBridge.runSuperRes(bitmap, scaleTarget)
            ?: return@withContext EffectResult.Error("AI inference failed")

        val elapsed = System.currentTimeMillis() - start
        Timber.i("enhance: done in %dms, output %dx%d", elapsed, result.width, result.height)
        EffectResult.Success(result)
    }

    suspend fun inpaint(imageUri: Uri, maskBitmap: Bitmap): EffectResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()

        val image = decodeBitmap(imageUri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        Timber.i("inpaint: decoded image %dx%d, mask %dx%d", image.width, image.height, maskBitmap.width, maskBitmap.height)

        val loadErr = ensureModelLoaded(LoadedModel.AOTGAN)
        if (loadErr != null) return@withContext EffectResult.Error("AI model failed to start: $loadErr")

        val result = litertBridge.runInpainting(image, maskBitmap)
            ?: return@withContext EffectResult.Error("AI inference failed")

        val final = blendResult(image, result, maskBitmap)
        result.recycle()

        val elapsed = System.currentTimeMillis() - start
        Timber.i("inpaint: done in %dms, output %dx%d", elapsed, final.width, final.height)
        EffectResult.Success(final)
    }

    fun isAotganModelDownloaded(): Pair<Boolean, File> {
        val dir = File(context.filesDir, FileNameObj.ModelFolder)
        val model = File(dir, FileNameObj.ModelAOTGAN)
        return Pair(model.exists() && model.length() > 0, model)
    }

    private suspend fun ensureModelLoaded(target: LoadedModel): String? {
        if (loadedModel == target && litertBridge.isLoaded) return null
        return mutex.withLock {
            if (loadedModel == target && litertBridge.isLoaded) return null
            try {
                when (target) {
                    LoadedModel.ESRGAN -> litertBridge.loadModel("esrgan/real_esrgan_x4plus.tflite")
                    LoadedModel.AOTGAN -> {
                        val (isValid, file) = isAotganModelDownloaded()
                        if (isValid) return "AOT-GAN model file not found"
                        litertBridge.loadModelFromFile(file)
                    }
                    LoadedModel.NONE -> return null
                }
                loadedModel = target
                null
            } catch (e: Exception) {
                loadedModel = LoadedModel.NONE
                e.message ?: "Unknown error"
            }
        }
    }

    private fun blendResult(original: Bitmap, inpainted: Bitmap, mask: Bitmap): Bitmap {
        val w = original.width
        val h = original.height
        val origPixels = IntArray(w * h)
        val inpaintedPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        original.getPixels(origPixels, 0, w, 0, 0, w, h)

        val maskScaled = if (mask.width != w || mask.height != h) {
            Bitmap.createScaledBitmap(mask, w, h, false)
        } else {
            mask
        }

        if (inpainted.width != w || inpainted.height != h) {
            val scaled = Bitmap.createScaledBitmap(inpainted, w, h, true)
            scaled.getPixels(inpaintedPixels, 0, w, 0, 0, w, h)
            scaled.recycle()
        } else {
            inpainted.getPixels(inpaintedPixels, 0, w, 0, 0, w, h)
        }

        maskScaled.getPixels(maskPixels, 0, w, 0, 0, w, h)
        if (maskScaled !== mask) maskScaled.recycle()

        val blended = IntArray(w * h)
        for (i in blended.indices) {
            val maskGray = ((maskPixels[i] shr 16) and 0xFF) * 0.299f +
                           ((maskPixels[i] shr 8) and 0xFF) * 0.587f +
                           (maskPixels[i] and 0xFF) * 0.114f
            if (maskGray > 30f) {
                blended[i] = inpaintedPixels[i]
            } else {
                blended[i] = origPixels[i]
            }
        }

        return Bitmap.createBitmap(blended, w, h, Config.ARGB_8888)
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        val decoded = try {
            when (uri.scheme) {
                "file" -> BitmapFactory.decodeFile(uri.path)
                else -> context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        } catch (_: Exception) { null } ?: return null

        if (decoded.config == Config.ARGB_8888) return decoded
        val converted = decoded.copy(Config.ARGB_8888, false)
        decoded.recycle()
        return converted
    }
}
