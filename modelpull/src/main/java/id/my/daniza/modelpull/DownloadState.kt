package id.my.daniza.modelpull

import kotlinx.coroutines.flow.Flow

data class DownloadState(
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val modelFile: java.io.File? = null,
    val error: String? = null,
)
