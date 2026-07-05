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
import id.my.daniza.pixheal.data.editing.EffectResult
import id.my.daniza.pixheal.data.editing.EditStep
import id.my.daniza.pixheal.data.editing.EditType
import id.my.daniza.pixheal.data.editing.EditingStateManager
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
import java.io.File
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
            val imageFile = projectRepository.imageFile(id)
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

            when (val result = editEffectManager.enhance(uri)) {
                is EffectResult.Error -> {
                    _uiState.update { it.copy(isProcessing = false, error = result.message) }
                    return@launch
                }
                is EffectResult.Success -> {
                    // Save enhanced result over the source image file
                    val imageFile = projectRepository.imageFile(projectId)
                    withContext(Dispatchers.IO) {
                        FileOutputStream(imageFile).use { out ->
                            result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                    }

                    editingStateManager.pushStep(
                        EditStep(
                            id = System.currentTimeMillis(),
                            type = EditType.ESRGAN_ENHANCE,
                        )
                    )

                    val project = projectRepository.getProjectById(projectId)
                    if (project != null) {
                        projectRepository.updateProject(
                            project.copy(
                                stepCount = editingStateManager.getStepCount(),
                                status = "edited",
                                lastEditedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    refreshUndoRedo()

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            resultBitmap = result.bitmap,
                            imageUri = Uri.fromFile(imageFile),
                        )
                    }
                }
            }
        }
    }

    fun downloadAotganModel(url: String) {
        viewModelScope.launch { modelDownloadRepository.downloadAotganModel(url) }
    }

    fun cancelDownload() {
        modelDownloadRepository.cancelDownload()
    }

    fun undo() {
        editingStateManager.undo()
        refreshUndoRedo()
    }

    fun redo() {
        editingStateManager.redo()
        refreshUndoRedo()
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
