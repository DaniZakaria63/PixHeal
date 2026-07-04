package id.my.daniza.pixheal.data.editing

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class EditingStateManager @Inject constructor() {

    private var currentState = EditableState()

    fun getCurrentState(): EditableState = currentState

    fun pushStep(step: EditStep): EditableState {
        currentState = currentState.copy(
            history = currentState.history + step,
            redoStack = emptyList(),
            currentNodeIndex = currentState.history.size,
        )
        return currentState
    }

    fun undo(): EditableState? {
        val history = currentState.history
        if (history.isEmpty()) return null
        val lastStep = history.last()
        currentState = currentState.copy(
            history = history.dropLast(1),
            redoStack = currentState.redoStack + lastStep,
            currentNodeIndex = history.size - 2,
        )
        return currentState
    }

    fun redo(): EditableState? {
        val redoStack = currentState.redoStack
        if (redoStack.isEmpty()) return null
        val step = redoStack.last()
        val stepType = step.type
        // Cannot redo after undo on AI model operations
        if (stepType == EditType.ESRGAN_ENHANCE || stepType == EditType.INPAINTING) return null
        currentState = currentState.copy(
            history = currentState.history + step,
            redoStack = redoStack.dropLast(1),
            currentNodeIndex = currentState.history.size,
        )
        return currentState
    }

    fun canUndo(): Boolean = currentState.history.isNotEmpty()
    fun canRedo(): Boolean {
        if (currentState.redoStack.isEmpty()) return false
        val nextType = currentState.redoStack.last().type
        return nextType != EditType.ESRGAN_ENHANCE && nextType != EditType.INPAINTING
    }

    fun serialize(): String = json.encodeToString(currentState)

    fun restore(fromJson: String) {
        currentState = json.decodeFromString<EditableState>(fromJson)
    }
}
