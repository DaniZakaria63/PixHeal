package id.my.daniza.pixheal.data.editing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.my.daniza.litert.LitertBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditEffectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val litertBridge: LitertBridge,
) {

    suspend fun enhance(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri) ?: return@withContext null
        litertBridge.runSuperRes(bitmap)
    }

    suspend fun inpaint(imageUri: Uri, maskUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val image = decodeBitmap(imageUri) ?: return@withContext null
        val mask = decodeBitmap(maskUri) ?: return@withContext null
        litertBridge.runInpainting(image, mask)
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }
}
