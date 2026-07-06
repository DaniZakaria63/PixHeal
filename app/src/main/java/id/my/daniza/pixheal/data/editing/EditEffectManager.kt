package id.my.daniza.pixheal.data.editing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.my.daniza.litert.LitertBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class EffectResult {
    data class Success(val bitmap: Bitmap) : EffectResult()
    data class Error(val message: String) : EffectResult()
}

@Singleton
class EditEffectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val litertBridge: LitertBridge,
) {
    private val mutex = Mutex()

    suspend fun enhance(uri: Uri, qualityMode: Boolean = false): EffectResult = withContext(Dispatchers.IO) {
        val scaleTarget = if (qualityMode) LitertBridge.MODE_QUALITY else LitertBridge.MODE_FAST
        Timber.i("enhance: %s mode (target=%d)", if (qualityMode) "quality" else "fast", scaleTarget)
        val start = System.currentTimeMillis()

        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        Timber.i("enhance: decoded %dx%d %s", bitmap.width, bitmap.height, bitmap.config)

        val loadErr = ensureModelLoaded()
        if (loadErr != null) return@withContext EffectResult.Error("AI model failed to start: $loadErr")

        val result = litertBridge.runSuperRes(bitmap, scaleTarget)
            ?: return@withContext EffectResult.Error("AI inference failed")

        val elapsed = System.currentTimeMillis() - start
        Timber.i("enhance: done in %dms, output %dx%d", elapsed, result.width, result.height)
        EffectResult.Success(result)
    }

    suspend fun inpaint(imageUri: Uri, maskUri: Uri): EffectResult = withContext(Dispatchers.IO) {
        Timber.i("inpaint: imageUri=%s maskUri=%s", imageUri, maskUri)
        val start = System.currentTimeMillis()

        val image = decodeBitmap(imageUri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        val mask = decodeBitmap(maskUri)
            ?: return@withContext EffectResult.Error("Failed to decode mask")
        Timber.i("inpaint: decoded image %dx%d, mask %dx%d", image.width, image.height, mask.width, mask.height)

        val loadErr = ensureModelLoaded()
        if (loadErr != null) return@withContext EffectResult.Error("AI model failed to start: $loadErr")

        val result = litertBridge.runInpainting(image, mask)
            ?: return@withContext EffectResult.Error("AI inference failed")

        val elapsed = System.currentTimeMillis() - start
        Timber.i("inpaint: done in %dms, output %dx%d", elapsed, result.width, result.height)
        EffectResult.Success(result)
    }

    private suspend fun ensureModelLoaded(): String? {
        if (litertBridge.isLoaded) return null
        mutex.withLock {
            if (litertBridge.isLoaded) return null
            return try {
                litertBridge.loadModel("esrgan/real_esrgan_x4plus.tflite")
                null
            } catch (e: Exception) {
                e.message ?: "Unknown error"
            }
        }
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
