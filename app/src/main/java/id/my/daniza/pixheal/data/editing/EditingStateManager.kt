package id.my.daniza.pixheal.data.editing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class EditingStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val stateRef = AtomicReference<EditableState?>(null)
    private var projectId: Long = 0L

    /**
     * Loads the JSON file into the in-memory DTO holder.
     * All subsequent runtime operations mutate this holder directly — no I/O.
     */
    fun openProject(id: Long): EditableState {
        require(projectId == 0L || projectId == id) {
            "Cannot open project $id while $projectId is active. Call closeProject() first."
        }

        projectId = id
        val file = stateFile(id)

        val state = if (file.exists()) {
            json.decodeFromString<EditableState>(file.readText())
        } else {
            val fresh = EditableState(projectId = id)
            file.parentFile?.mkdirs()
            fresh
        }

        stateRef.set(state)
        return state
    }

    /**
     * Writes the current in-memory DTO to disk once.
     * Call on project close, app background, or explicit save.
     */
    fun writeState() {
        val state = stateRef.get() ?: return
        val file = stateFile(projectId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    /**
     * Writes to disk then releases the in-memory holder.
     */
    fun closeProject() {
        if (projectId == 0L) return
        writeState()
        stateRef.set(null)
        projectId = 0L
    }

    // ── Runtime mutations (in-memory only, lock-free via AtomicReference) ──

    fun pushStep(step: EditStep): EditableState {
        val next = stateRef.updateAndGet { current ->
            checkNotNull(current) { "No project opened" }
            current.copy(
                history = current.history + step,
                redoStack = emptyList(),
            )
        }!!
        return next
    }

    fun undo(): EditableState? {
        val current = stateRef.get() ?: return null
        if (current.history.isEmpty()) return null
        val last = current.history.last()
        stateRef.set(
            current.copy(
                history = current.history.dropLast(1),
                redoStack = current.redoStack + last,
            )
        )
        return stateRef.get()
    }

    fun redo(): EditableState? {
        val current = stateRef.get() ?: return null
        val redoStack = current.redoStack
        if (redoStack.isEmpty()) return null
        val step = redoStack.last()
        if (step.type == EditType.ESRGAN_ENHANCE || step.type == EditType.INPAINTING) return null

        stateRef.set(
            current.copy(
                history = current.history + step,
                redoStack = redoStack.dropLast(1),
            )
        )
        return stateRef.get()
    }

    fun getStepCount(): Int = stateRef.get()?.history?.size ?: 0

    fun canUndo(): Boolean = (stateRef.get()?.history?.size ?: 0) > 0

    fun canRedo(): Boolean {
        val redoStack = stateRef.get()?.redoStack ?: return false
        if (redoStack.isEmpty()) return false
        val nextType = redoStack.last().type
        return nextType != EditType.ESRGAN_ENHANCE && nextType != EditType.INPAINTING
    }

    fun deleteProjectFiles(id: Long) {
        val dir = projectDir(id)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun hasStateFile(id: Long): Boolean = stateFile(id).exists()

    private fun stateFile(id: Long): File =
        File(projectDir(id), "edit_state.json")

    private fun projectDir(id: Long): File =
        File(context.filesDir, "projects/$id")
}
