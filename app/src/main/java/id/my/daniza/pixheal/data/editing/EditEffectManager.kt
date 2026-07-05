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

    suspend fun enhance(uri: Uri): EffectResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")

        val loadErr = ensureModelLoaded()
        if (loadErr != null) return@withContext EffectResult.Error("AI model failed to start: $loadErr")

        val result = litertBridge.runSuperRes(bitmap)
            ?: return@withContext EffectResult.Error("AI inference failed — check image dimensions")

        EffectResult.Success(result)
    }

    suspend fun inpaint(imageUri: Uri, maskUri: Uri): EffectResult = withContext(Dispatchers.IO) {
        val image = decodeBitmap(imageUri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        val mask = decodeBitmap(maskUri)
            ?: return@withContext EffectResult.Error("Failed to decode mask")

        val loadErr = ensureModelLoaded()
        if (loadErr != null) return@withContext EffectResult.Error("AI model failed to start: $loadErr")

        val result = litertBridge.runInpainting(image, mask)
            ?: return@withContext EffectResult.Error("AI inference failed — check image dimensions")

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
        } catch (e: Exception) {
            null
        } ?: return null

        // Native code requires ARGB_8888. Ensure format matches.
        if (decoded.config == Config.ARGB_8888) return decoded
        val converted = decoded.copy(Config.ARGB_8888, false)
        decoded.recycle()
        return converted
    }
}
