package id.my.daniza.pixheal.data.ui

import android.graphics.Bitmap
import android.net.Uri
import id.my.daniza.pixheal.data.editing.EditStep

enum class BgRemovalMode(val label: String) {
    AUTO("Auto"),
    MANUAL("Manual"),
}

enum class EditTool(val label: String) {
    ENHANCER("Enhancer"),
    OBJ_REMOVAL("Obj Removal"),
    BASIC_EDIT("Basic Edit"),
    BG_REMOVAL("BG Removal"),
    EXPORT("Export"),
}

enum class BasicEditSubTool {
    ADJUST,
    CROP,
    ROTATE,
}

enum class BasicAdjustType(val label: String, val min: Float, val max: Float, val default: Float) {
    BRIGHTNESS("Brightness", -100f, 100f, 0f),
    CONTRAST("Contrast", -100f, 100f, 0f),
    SATURATION("Saturation", -100f, 100f, 0f),
    SHADOWS("Shadows", -100f, 100f, 0f),
    HIGHLIGHTS("Highlights", -100f, 100f, 0f),
    TEMPERATURE("Temperature", -100f, 100f, 0f),
    VIGNETTE("Vignette", 0f, 100f, 0f),
}

data class BasicAdjustValues(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val temperature: Float = 0f,
    val vignette: Float = 0f,
) {
    val isDefault: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f &&
                shadows == 0f && highlights == 0f && temperature == 0f && vignette == 0f

    fun withValue(type: BasicAdjustType, value: Float): BasicAdjustValues = when (type) {
        BasicAdjustType.BRIGHTNESS -> copy(brightness = value)
        BasicAdjustType.CONTRAST -> copy(contrast = value)
        BasicAdjustType.SATURATION -> copy(saturation = value)
        BasicAdjustType.SHADOWS -> copy(shadows = value)
        BasicAdjustType.HIGHLIGHTS -> copy(highlights = value)
        BasicAdjustType.TEMPERATURE -> copy(temperature = value)
        BasicAdjustType.VIGNETTE -> copy(vignette = value)
    }
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    STANDARD("4:3", 4f / 3f),
    WIDESCREEN("16:9", 16f / 9f),
    PORTRAIT("3:4", 3f / 4f),
    STORY("9:16", 9f / 16f),
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
    val basicSubTool: BasicEditSubTool = BasicEditSubTool.ADJUST,
    val basicValues: BasicAdjustValues = BasicAdjustValues(),
    val previewBitmap: Bitmap? = null,
    val cropActive: Boolean = false,
    val cropAspectRatio: CropAspectRatio = CropAspectRatio.FREE,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val bgRemovalMode: BgRemovalMode = BgRemovalMode.AUTO,
    val segmentationOverlay: Bitmap? = null,
    val bgThreshold: Float = 0.5f,
    val bgHardness: Float = 0.5f,
    val bgEdgeSoften: Float = 0f,
    val isSegmenting: Boolean = false,
)
