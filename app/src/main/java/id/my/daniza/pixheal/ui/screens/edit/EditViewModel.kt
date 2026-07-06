package id.my.daniza.pixheal.ui.screens.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import id.my.daniza.pixheal.data.remoteconfig.RemoteConfigManager
import id.my.daniza.pixheal.data.ui.EditTool
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
    private val remoteConfigManager: RemoteConfigManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadState> = modelDownloadRepository.downloadState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), DownloadState())

    private var projectId: Long = 0

    private fun imageFileUri(): Uri {
        val file = editingStateManager.imageFile()
        return Uri.parse("file://${file.absolutePath}?t=${System.currentTimeMillis()}")
    }

    fun initProject(id: Long) {
        projectId = id
        val state = editingStateManager.openProject(id)
        viewModelScope.launch {
            val imageFile = editingStateManager.imageFile()
            _uiState.update {
                it.copy(
                    imageUri = imageFileUri(),
                    editHistory = state.history,
                )
            }
        }
        refreshUndoRedo()
    }

    fun selectTool(tool: EditTool) {
        _uiState.update { it.copy(selectedTool = tool) }
        if (tool == EditTool.OBJ_REMOVAL) {
            checkModelAvailability()
        }
    }

    private fun checkModelAvailability() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingModel = true) }
            val available = editEffectManager.isAotganModelDownloaded()
            if (!available) {
                _uiState.update {
                    it.copy(
                        isCheckingModel = false,
                        modelAvailable = false,
                        showDrawGuide = true,
                    )
                }
                triggerModelDownload()
            } else {
                val file = editEffectManager.aotganModelFile()
                val valid = withContext(Dispatchers.IO) { file.exists() && file.length() > 0 }
                _uiState.update {
                    it.copy(
                        isCheckingModel = false,
                        modelAvailable = valid,
                        showDrawGuide = valid,
                    )
                }
                if (valid) initMaskBitmap()
            }
        }
    }

    fun dismissDownloadDialog() {
        _uiState.update { it.copy(showDownloadDialog = false) }
    }

    private fun triggerModelDownload() {
        _uiState.update { it.copy(showDownloadDialog = true) }
        val url = remoteConfigManager.getAotganModelUrl()
        viewModelScope.launch {
            val result = modelDownloadRepository.downloadAotganModel(url)
            if (result != null) {
                _uiState.update {
                    it.copy(modelAvailable = true, showDrawGuide = true)
                }
                initMaskBitmap()
            }
        }
    }

    private fun initMaskBitmap() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val imageFile = editingStateManager.imageFile()
                val original = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                if (original != null) {
                    val mask = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
                    mask.eraseColor(android.graphics.Color.TRANSPARENT)
                    original.recycle()
                    _uiState.update { it.copy(maskBitmap = mask) }
                }
            }
        }
    }

    fun dismissDrawGuide() {
        _uiState.update { it.copy(showDrawGuide = false) }
    }

    fun onMaskDrawStart(x: Float, y: Float) {
        val mask = _uiState.value.maskBitmap ?: return
        drawBrushOnMask(mask, x, y, _uiState.value.brushRadius)
        _uiState.update { it.copy(maskBitmap = mask.copy(Bitmap.Config.ARGB_8888, true), isMaskDrawing = true) }
    }

    fun onMaskDrawMove(x: Float, y: Float) {
        val mask = _uiState.value.maskBitmap ?: return
        drawBrushOnMask(mask, x, y, _uiState.value.brushRadius)
        _uiState.update { it.copy(maskBitmap = mask.copy(Bitmap.Config.ARGB_8888, true)) }
    }

    fun onMaskDrawEnd() {
        _uiState.update { it.copy(isMaskDrawing = false) }
    }

    fun setBrushRadius(radius: Float) {
        _uiState.update { it.copy(brushRadius = radius) }
    }

    fun clearMask() {
        val mask = _uiState.value.maskBitmap
        mask?.eraseColor(android.graphics.Color.TRANSPARENT)
        _uiState.update { it.copy(maskBitmap = mask) }
    }

    fun triggerObjectRemoval() {
        triggerInpainting()
    }

    private fun drawBrushOnMask(mask: Bitmap, x: Float, y: Float, radius: Float) {
        val canvas = Canvas(mask)
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun triggerInpainting() {
        val mask = _uiState.value.maskBitmap ?: return
        val imageUri = _uiState.value.imageUri ?: return

        if (!hasMaskContent(mask)) {
            _uiState.update { it.copy(error = "Draw on the image first to mark the object to remove") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            val before = editingStateManager.getCurrentState()
            editingStateManager.saveSnapshot(before.history.size)

            when (val result = editEffectManager.inpaint(imageUri, mask)) {
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
                        result.bitmap.recycle()
                    }

                    val step = EditStep(
                        id = System.currentTimeMillis(),
                        type = EditType.INPAINTING,
                    )
                    val next = editingStateManager.pushStep(step)

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

                    clearMaskWithoutTrigger()
                    refreshUndoRedo()
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            resultBitmap = null,
                            imageUri = imageFileUri(),
                            editHistory = next.history,
                            showDrawGuide = true,
                        )
                    }
                }
            }
        }
    }

    private fun clearMaskWithoutTrigger() {
        val mask = _uiState.value.maskBitmap
        mask?.eraseColor(android.graphics.Color.TRANSPARENT)
        _uiState.update { it.copy(maskBitmap = mask) }
    }

    private fun hasMaskContent(mask: Bitmap): Boolean {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        for (p in pixels) {
            if ((p and 0xFFFFFF) != 0) return true
        }
        return false
    }

    fun enhance() {
        val uri = _uiState.value.imageUri ?: run {
            _uiState.update { it.copy(error = "No source image") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            val before = editingStateManager.getCurrentState()
            editingStateManager.saveSnapshot(before.history.size)

            val qualityMode = _uiState.value.qualityMode
            when (val result = editEffectManager.enhance(uri, qualityMode)) {
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
                            imageUri = imageFileUri(),
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

    fun toggleQualityMode() {
        _uiState.update {
            it.copy(qualityMode = !it.qualityMode)
        }
    }

    fun downloadAotganModel() {
        triggerModelDownload()
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
