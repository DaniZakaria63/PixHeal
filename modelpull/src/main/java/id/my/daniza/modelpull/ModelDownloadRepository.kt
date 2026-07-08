package id.my.daniza.modelpull

import id.my.daniza.modelpull.remoteconfig.RemoteConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadRepository @Inject constructor(
    private val client: OkHttpClient,
    private val modelDirectory: File,
    private val remoteConfigManager: RemoteConfigManager,
) {

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: Flow<DownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var isCancelled = false

    suspend fun downloadAotganModel(): File? {
        isCancelled = false
        val targetFile = File(modelDirectory, "aotgan.tflite")
        val metadataFile = File(modelDirectory, "aotgan_metadata.json")

        if (targetFile.exists()) {
            _downloadState.value = DownloadState(
                isComplete = true,
                modelFile = targetFile,
                progress = 1f,
            )
            return targetFile
        }

        _downloadState.value = DownloadState(isDownloading = true, progress = 0f)

        val zipTempFile = File(modelDirectory, "aotgan_zip.tmp")
        val modelUrl = remoteConfigManager.getAotganModelUrl()

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
                    FileOutputStream(zipTempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isCancelled) {
                                zipTempFile.delete()
                                _downloadState.value = DownloadState()
                                return@withContext null
                            }
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                _downloadState.value = _downloadState.value.copy(
                                    progress = bytesRead.toFloat() / contentLength * 0.9f
                                )
                            }
                        }
                    }
                }

                if (isCancelled) {
                    zipTempFile.delete()
                    _downloadState.value = DownloadState()
                    return@withContext null
                }

                val extracted = extractAotganZip(zipTempFile, modelDirectory)

                zipTempFile.delete()

                if (!extracted) {
                    _downloadState.value = DownloadState(error = "No .tflite file found in zip")
                    return@withContext null
                }

                _downloadState.value = DownloadState(
                    isComplete = true,
                    modelFile = targetFile,
                    progress = 1f,
                )
                targetFile
            }
        } catch (e: Exception) {
            zipTempFile.delete()
            targetFile.delete()
            metadataFile.delete()
            _downloadState.value = DownloadState(error = e.message ?: "Unknown error")
            null
        }
    }

    private fun extractAotganZip(zipFile: File, destDir: File): Boolean {
        var hasTflite = false
        return try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val fileName = entry.name.substringAfterLast("/")
                        if (fileName.isNotBlank()) {
                            val destFile = File(destDir, fileName)
                            FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            if (fileName.endsWith(".tflite", ignoreCase = true)) {
                                hasTflite = true
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            hasTflite
        } catch (_: Exception) {
            false
        }
    }

    fun cancelDownload() {
        isCancelled = true
    }

    fun isModelDownloaded(): Boolean {
        return File(modelDirectory, "aotgan.tflite").exists()
    }
}
