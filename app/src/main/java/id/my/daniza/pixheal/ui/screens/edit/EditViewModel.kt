package id.my.daniza.pixheal.ui.screens.edit

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.my.daniza.local.ProjectRepository
import id.my.daniza.modelpull.DownloadState
import id.my.daniza.modelpull.ModelDownloadRepository
import id.my.daniza.pixheal.data.editing.EditEffectManager
import id.my.daniza.pixheal.data.editing.EditStep
import id.my.daniza.pixheal.data.editing.EditType
import id.my.daniza.pixheal.data.editing.EditingStateManager
import id.my.daniza.pixheal.data.editing.EffectResult
import id.my.daniza.pixheal.data.ui.EditUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class EditViewModel @Inject constructor(
    private val editingStateManager: EditingStateManager,
    private val projectRepository: ProjectRepository,
    private val editEffectManager: EditEffectManager,
    private val modelDownloadRepository: ModelDownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadState> = modelDownloadRepository.downloadState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), DownloadState())

    private var projectId: Long = 0

    fun initProject(id: Long) {
        projectId = id
        editingStateManager.openProject(id)
        viewModelScope.launch {
            val imageFile = editingStateManager.imageFile()
            _uiState.update {
                it.copy(imageUri = Uri.fromFile(imageFile))
            }
        }
        refreshUndoRedo()
    }

    fun enhance() {
        val uri = _uiState.value.imageUri ?: run {
            _uiState.update { it.copy(error = "No source image") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            // Snapshot the current image BEFORE the destructive edit.
            val before = editingStateManager.getCurrentState()
            editingStateManager.saveSnapshot(before.history.size)

            when (val result = editEffectManager.enhance(uri)) {
                is EffectResult.Error -> {
                    _uiState.update { it.copy(isProcessing = false, error = result.message) }
                    return@launch
                }
                is EffectResult.Success -> {
                    val imageFile = editingStateManager.imageFile()
                    withContext(Dispatchers.IO) {
                        FileOutputStream(imageFile).use { out ->
                            result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                    }

                    val step = EditStep(
                        id = System.currentTimeMillis(),
                        type = EditType.ESRGAN_ENHANCE,
                    )
                    val next = editingStateManager.pushStep(step)

                    // Persist in DB and regenerate thumbnail.
                    val project = projectRepository.getProjectById(projectId)
                    if (project != null) {
                        projectRepository.updateProject(
                            project.copy(
                                stepCount = next.history.size,
                                status = "edited",
                                lastEditedAt = System.currentTimeMillis(),
                            )
                        )
                        projectRepository.regenerateThumbnail(projectId)
                    }

                    refreshUndoRedo()
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            resultBitmap = result.bitmap,
                            imageUri = Uri.fromFile(imageFile),
                            editHistory = next.history,
                        )
                    }
                }
            }
        }
    }

    fun undo() {
        val state = editingStateManager.undo() ?: return
        editingStateManager.restoreSnapshot(state.history.size)

        refreshUndoRedo()
        val imageFile = editingStateManager.imageFile()
        _uiState.update {
            it.copy(
                imageUri = Uri.fromFile(imageFile),
                resultBitmap = null,
                editHistory = state.history,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val project = projectRepository.getProjectById(projectId)
            if (project != null) {
                projectRepository.updateProject(project.copy(stepCount = state.history.size))
                projectRepository.regenerateThumbnail(projectId)
            }
        }
    }

    fun redo() {
        val state = editingStateManager.redo() ?: return

        refreshUndoRedo()
        _uiState.update { it.copy(editHistory = state.history) }

        viewModelScope.launch(Dispatchers.IO) {
            val project = projectRepository.getProjectById(projectId)
            if (project != null) {
                projectRepository.updateProject(project.copy(stepCount = state.history.size))
            }
        }
    }

    fun toggleHistory() {
        _uiState.update {
            it.copy(showHistory = !it.showHistory)
        }
    }

    fun downloadAotganModel(url: String) {
        viewModelScope.launch { modelDownloadRepository.downloadAotganModel(url) }
    }

    fun cancelDownload() {
        modelDownloadRepository.cancelDownload()
    }

    private fun refreshUndoRedo() {
        _uiState.update {
            it.copy(
                canUndo = editingStateManager.canUndo(),
                canRedo = editingStateManager.canRedo(),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        editingStateManager.closeProject()
    }
}
