package id.my.daniza.pixheal.data.editing

import id.my.daniza.local.ProjectHandler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

/**
 * Manages the edit undo/redo DAG for a single project.
 *
 * ## Snapshot-based undo
 *
 * Each AI operation is destructive (it overwrites the source image). To support
 * undo we save a **full snapshot** of the image BEFORE every edit step:
 *
 *   snapshots/step_0.jpg   → saved before the 1st edit (pristine original)
 *   snapshots/step_1.jpg   → saved before the 2nd edit
 *   snapshots/step_2.jpg   → saved before the 3rd edit
 *   ...
 *
 * On undo the snapshot at `history.size` is restored to `image.jpg`.
 * Redo of AI operations (ESRGAN_ENHANCE, INPAINTING) is permanently blocked
 * because the intermediate computationally-produced bitmap is lost.
 */
@Singleton
class EditingStateManager @Inject constructor(
    private val projectHandler: ProjectHandler,
) {
    private val stateRef = AtomicReference<EditableState?>(null)

    fun openProject(): EditableState {
        val file = projectHandler.editStateFile()
        val state = if (file.exists()) {
            json.decodeFromString<EditableState>(file.readText())
        } else {
            val fresh = EditableState(projectId = projectHandler.currentProjectId)
            file.parentFile?.mkdirs()
            fresh
        }
        stateRef.set(state)
        Timber.d("openProject: %d steps in history", state.history.size)
        return state
    }

    fun writeState() {
        val state = stateRef.get() ?: return
        val file = projectHandler.editStateFile()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    fun closeProject() {
        if (projectHandler.currentProjectId == 0L) return
        writeState()
        stateRef.set(null)
    }

    fun getCurrentState(): EditableState = stateRef.get()
        ?: throw IllegalStateException("No project opened")

    // ── Snapshot persistence ───────────────────────────────────────────

    /**
     * Save the current image as a snapshot at [index].
     * Called BEFORE an edit, so snapshot index = current history size.
     */
    fun saveSnapshot(index: Int) {
        val src = projectHandler.imageFile()
        val dst = projectHandler.snapshotFile(index)
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
        Timber.d("saveSnapshot: step_%d (lastModified=%d)", index, src.lastModified())
    }

    fun restoreSnapshot(index: Int) {
        val snap = projectHandler.snapshotFile(index)
        val dst = projectHandler.imageFile()
        if (!snap.exists()) return
        snap.copyTo(dst, overwrite = true)
        Timber.d("restoreSnapshot: step_%d → image.jpg", index)
    }

    // ── Runtime mutations ──────────────────────────────────────────────

    fun pushStep(step: EditStep): EditableState {
        val next = stateRef.updateAndGet { current ->
            checkNotNull(current) { "No project opened" }
            current.copy(
                history = current.history + step,
                redoStack = emptyList(),
            )
        }!!
        writeState()
        val keepCount = next.history.size
        val snapshotsDir = projectHandler.snapshotDir()
        if (snapshotsDir.exists()) {
            snapshotsDir.listFiles()?.forEach { f ->
                val idx = f.nameWithoutExtension.removePrefix("step_").toIntOrNull()
                if (idx != null && idx >= keepCount) {
                    f.delete()
                    Timber.d("pushStep: cleaned orphan snapshot step_%d", idx)
                }
            }
        }
        return next
    }

    fun undo(): EditableState? {
        val current = stateRef.get() ?: return null
        if (current.history.isEmpty()) return null
        val last = current.history.last()
        val next = current.copy(
            history = current.history.dropLast(1),
            redoStack = current.redoStack + last,
        )
        stateRef.set(next)
        writeState()
        Timber.d("undo: %d steps remain, %d in redo", next.history.size, next.redoStack.size)
        return next
    }

    fun redo(): EditableState? {
        val current = stateRef.get() ?: return null
        val stack = current.redoStack
        if (stack.isEmpty()) return null
        val step = stack.last()
        if (step.type == EditType.ESRGAN_ENHANCE || step.type == EditType.INPAINTING || step.type == EditType.BG_REMOVAL) return null
        val next = current.copy(
            history = current.history + step,
            redoStack = stack.dropLast(1),
        )
        stateRef.set(next)
        writeState()
        Timber.d("redo: %d steps, %d in redo", next.history.size, next.redoStack.size)
        return next
    }

    fun getStepCount(): Int = stateRef.get()?.history?.size ?: 0

    fun canUndo(): Boolean = (stateRef.get()?.history?.size ?: 0) > 0

    fun canRedo(): Boolean {
        val stack = stateRef.get()?.redoStack ?: return false
        if (stack.isEmpty()) return false
        val nextType = stack.last().type
        return nextType != EditType.ESRGAN_ENHANCE && nextType != EditType.INPAINTING && nextType != EditType.BG_REMOVAL
    }
}
