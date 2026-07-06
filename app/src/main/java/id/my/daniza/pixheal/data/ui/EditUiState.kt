package id.my.daniza.pixheal.data.ui

import android.graphics.Bitmap
import android.net.Uri
import id.my.daniza.pixheal.data.editing.EditStep

data class EditUiState(
    val imageUri: Uri? = null,
    val isProcessing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val resultBitmap: Bitmap? = null,
    val error: String? = null,
    val editHistory: List<EditStep> = emptyList(),
    val showHistory: Boolean = false,
    val qualityMode: Boolean = false,
)
