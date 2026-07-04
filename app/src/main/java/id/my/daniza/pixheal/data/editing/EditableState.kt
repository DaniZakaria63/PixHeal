package id.my.daniza.pixheal.data.editing

import kotlinx.serialization.Serializable

@Serializable
data class EditableState(
    val history: List<EditStep> = emptyList(),
    val redoStack: List<EditStep> = emptyList(),
    val currentNodeIndex: Int = -1,
)

@Serializable
data class EditStep(
    val id: Long,
    val type: EditType,
    val parameters: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
enum class EditType {
    ESRGAN_ENHANCE,
    INPAINTING,
    CROP,
    ROTATE,
    ADJUST_BRIGHTNESS,
    ADJUST_CONTRAST,
}
