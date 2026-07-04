package id.my.daniza.modelpull

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadRepository @Inject constructor(
    private val client: OkHttpClient,
    private val modelDirectory: File,
) {

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: Flow<DownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var isCancelled = false

    suspend fun downloadAotganModel(modelUrl: String): File? {
        isCancelled = false
        val tempFile = File(modelDirectory, "aotgan.tmp")
        val targetFile = File(modelDirectory, "aotgan.tflite")

        if (targetFile.exists()) {
            _downloadState.value = DownloadState(
                isComplete = true,
                modelFile = targetFile,
                progress = 1f,
            )
            return targetFile
        }

        _downloadState.value = DownloadState(isDownloading = true, progress = 0f)

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(modelUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val error = "Download failed: HTTP ${response.code}"
                    _downloadState.value = DownloadState(error = error)
                    return@withContext null
                }

                val body = response.body ?: run {
                    _downloadState.value = DownloadState(error = "Empty response body")
                    return@withContext null
                }

                val contentLength = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isCancelled) {
                                tempFile.delete()
                                _downloadState.value = DownloadState()
                                return@withContext null
                            }
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                _downloadState.value = _downloadState.value.copy(
                                    progress = bytesRead.toFloat() / contentLength
                                )
                            }
                        }
                    }
                }

                tempFile.renameTo(targetFile)
                _downloadState.value = DownloadState(
                    isComplete = true,
                    modelFile = targetFile,
                    progress = 1f,
                )
                targetFile
            }
        } catch (e: Exception) {
            tempFile.delete()
            _downloadState.value = DownloadState(error = e.message ?: "Unknown error")
            null
        }
    }

    fun cancelDownload() {
        isCancelled = true
    }

    fun isModelDownloaded(): Boolean {
        return File(modelDirectory, "aotgan.tflite").exists()
    }
}
