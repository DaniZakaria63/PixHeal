package id.my.daniza.pixheal.data.editing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
) {
    private val stateRef = AtomicReference<EditableState?>(null)
    private var projectId: Long = 0L

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
        Timber.d("openProject: %d steps in history", state.history.size)
        return state
    }

    fun writeState() {
        val state = stateRef.get() ?: return
        val file = stateFile(projectId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    fun closeProject() {
        if (projectId == 0L) return
        writeState()
        stateRef.set(null)
        projectId = 0L
    }

    fun getCurrentState(): EditableState = stateRef.get()
        ?: throw IllegalStateException("No project opened")

    // ── Snapshot persistence ───────────────────────────────────────────

    /**
     * Save the current image as a snapshot at [index].
     * Called BEFORE an edit, so snapshot index = current history size.
     */
    fun saveSnapshot(index: Int) {
        val src = imageFile()
        if (!src.exists()) return
        val dst = snapshotFile(index)
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
        Timber.d("saveSnapshot: step_%d (lastModified=%d)", index, src.lastModified())
    }

    /**
     * Restore the snapshot at [index] over the working image.
     * Called on UNDO, where index = history.size after the step was popped.
     */
    fun restoreSnapshot(index: Int) {
        val snap = snapshotFile(index)
        val dst = imageFile()
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
        // Clean up orphan snapshots (from overwritten redo branch)
        val keepCount = next.history.size
        val snapshotsDir = snapshotsDir()
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
        Timber.d("undo: %d steps remain, %d in redo", next.history.size, next.redoStack.size)
        return next
    }

    fun redo(): EditableState? {
        val current = stateRef.get() ?: return null
        val stack = current.redoStack
        if (stack.isEmpty()) return null
        val step = stack.last()
        if (step.type == EditType.ESRGAN_ENHANCE || step.type == EditType.INPAINTING) return null
        val next = current.copy(
            history = current.history + step,
            redoStack = stack.dropLast(1),
        )
        stateRef.set(next)
        Timber.d("redo: %d steps, %d in redo", next.history.size, next.redoStack.size)
        return next
    }

    fun getStepCount(): Int = stateRef.get()?.history?.size ?: 0

    fun canUndo(): Boolean = (stateRef.get()?.history?.size ?: 0) > 0

    fun canRedo(): Boolean {
        val stack = stateRef.get()?.redoStack ?: return false
        if (stack.isEmpty()) return false
        val nextType = stack.last().type
        return nextType != EditType.ESRGAN_ENHANCE && nextType != EditType.INPAINTING
    }

    // ── File paths ─────────────────────────────────────────────────────

    fun imageFile(): File =
        File(projectDir(), "image.jpg")

    private fun stateFile(id: Long): File =
        File(projectDir(id), "edit_state.json")

    private fun snapshotsDir(): File =
        File(projectDir(), "snapshots")

    private fun snapshotFile(index: Int): File =
        File(snapshotsDir(), "step_$index.jpg")

    private fun projectDir(id: Long = projectId): File =
        File(context.filesDir, "projects/$id")
}
