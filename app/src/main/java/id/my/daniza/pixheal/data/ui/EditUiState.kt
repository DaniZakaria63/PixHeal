package id.my.daniza.pixheal.data.ui

import android.graphics.Bitmap
import android.net.Uri
import id.my.daniza.pixheal.data.editing.EditStep

enum class EditTool(val label: String) {
    ENHANCER("Enhancer"),
    OBJ_REMOVAL("Obj Removal"),
    BASIC_EDIT("Basic Edit"),
    BG_REMOVAL("BG Removal"),
    EXPORT("Export"),
}

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
    val selectedTool: EditTool = EditTool.ENHANCER,
    val maskBitmap: Bitmap? = null,
    val isMaskDrawing: Boolean = false,
    val brushRadius: Float = 30f,
    val showDrawGuide: Boolean = false,
    val modelAvailable: Boolean = false,
    val isCheckingModel: Boolean = false,
    val showDownloadDialog: Boolean = false,
)
