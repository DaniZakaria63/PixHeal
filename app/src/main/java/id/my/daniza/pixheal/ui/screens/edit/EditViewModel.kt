package id.my.daniza.pixheal.ui.screens.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.my.daniza.local.ProjectHandler
import id.my.daniza.local.ProjectRepository
import id.my.daniza.modelpull.DownloadState
import id.my.daniza.modelpull.ModelDownloadRepository
import id.my.daniza.pixheal.data.editing.EditEffectManager
import id.my.daniza.pixheal.data.editing.EditStep
import id.my.daniza.pixheal.data.editing.EditType
import id.my.daniza.pixheal.data.editing.EditingStateManager
import id.my.daniza.pixheal.data.editing.EffectResult
import id.my.daniza.pixheal.data.ui.BasicAdjustType
import id.my.daniza.pixheal.data.ui.BasicAdjustValues
import id.my.daniza.pixheal.data.ui.BasicEditSubTool
import id.my.daniza.pixheal.data.ui.CropAspectRatio
import id.my.daniza.pixheal.data.ui.EditTool
import id.my.daniza.pixheal.data.ui.EditUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap

@HiltViewModel
class EditViewModel @Inject constructor(
    private val projectHandler: ProjectHandler,
    private val editingStateManager: EditingStateManager,
    private val projectRepository: ProjectRepository,
    private val editEffectManager: EditEffectManager,
    private val modelDownloadRepository: ModelDownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadState> = modelDownloadRepository.downloadState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), DownloadState())

    private var adjustPreviewJob: Job? = null

    init {
        _uiState
            .map { it.editHistory }
            .distinctUntilChanged()
            .onEach {
                _uiState.update { state ->
                    state.copy(
                        canUndo = editingStateManager.canUndo(),
                        canRedo = editingStateManager.canRedo(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun initProject(id: Long) {
        viewModelScope.launch {
            projectHandler.openProject(id)
            val state = editingStateManager.openProject()
            val uri = imageFileUri()
            val dims = withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(projectHandler.imageFile().absolutePath, opts)
                opts.outWidth to opts.outHeight
            }
            _uiState.update {
                it.copy(
                    imageUri = uri,
                    editHistory = state.history,
                    imageWidth = dims.first,
                    imageHeight = dims.second,
                )
            }
        }
    }

    fun selectTool(tool: EditTool) {
        _uiState.update { it.copy(selectedTool = tool) }
        if (tool == EditTool.OBJ_REMOVAL) {
            checkModelAvailability()
        }
        if (tool == EditTool.BASIC_EDIT) {
            loadImageDims()
        }
    }

    private fun loadImageDims() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(projectHandler.imageFile().absolutePath, opts)
                _uiState.update {
                    it.copy(imageWidth = opts.outWidth, imageHeight = opts.outHeight)
                }
            }
        }
    }

    // ── Model download ─────────────────────────────────────────────────

    private fun checkModelAvailability() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingModel = true) }
            val (available) = editEffectManager.isAotganModelDownloaded()

            _uiState.update {
                it.copy(
                    isCheckingModel = false,
                    modelAvailable = available,
                    showDrawGuide = !available,
                )
            }

            if (available) initMaskBitmap() else triggerModelDownload()
        }
    }

    fun triggerModelDownload() {
        _uiState.update { it.copy(showDownloadDialog = true) }
        viewModelScope.launch {
            val result = modelDownloadRepository.downloadAotganModel()
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
                val original = BitmapFactory.decodeFile(projectHandler.imageFile().absolutePath)
                if (original != null) {
                    val mask = createBitmap(original.width, original.height)
                    mask.eraseColor(android.graphics.Color.TRANSPARENT)
                    original.recycle()
                    _uiState.update { it.copy(maskBitmap = mask) }
                }
            }
        }
    }

    fun dismissDownloadDialog() {
        _uiState.update { it.copy(showDownloadDialog = false) }
    }

    fun dismissDrawGuide() {
        _uiState.update { it.copy(showDrawGuide = false) }
    }

    // ── Mask drawing ───────────────────────────────────────────────────

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

    private fun drawBrushOnMask(mask: Bitmap, x: Float, y: Float, radius: Float) {
        val canvas = Canvas(mask)
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, radius, paint)
    }

    // ── Inpainting ─────────────────────────────────────────────────────

    fun triggerInpainting() {
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
                    withContext(Dispatchers.IO) {
                        FileOutputStream(projectHandler.imageFile()).use { out ->
                            result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        result.bitmap.recycle()
                    }

                    val next = editingStateManager.pushStep(
                        EditStep(
                            id = System.currentTimeMillis(),
                            type = EditType.INPAINTING,
                        )
                    )

                    projectRepository.updateProject(
                        stepCount = next.history.size,
                        status = "edited",
                    )
                    projectRepository.regenerateThumbnail()

                    clearMaskWithoutTrigger()
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

    // ── ESRGAN Enhance ─────────────────────────────────────────────────

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
                    val nextStep = editingStateManager.pushStep(
                        EditStep(
                            id = System.currentTimeMillis(),
                            type = EditType.ESRGAN_ENHANCE,
                        )
                    )

                    withContext(Dispatchers.IO) {
                        FileOutputStream(projectHandler.imageFile()).use { out ->
                            result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        
                        projectRepository.updateProject(
                            stepCount = nextStep.history.size,
                            status = "edited",
                        )
                        
                        result.bitmap.recycle()
                    }

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            resultBitmap = null,
                            imageUri = imageFileUri(),
                            editHistory = nextStep.history,
                        )
                    }
                }
            }
        }
    }

    // ── Basic Edit: Adjust ─────────────────────────────────────────────

    fun selectBasicSubTool(subTool: BasicEditSubTool) {
        adjustPreviewJob?.cancel()
        _uiState.update {
            it.copy(
                basicSubTool = subTool,
                previewBitmap = null,
                cropActive = subTool == BasicEditSubTool.CROP,
            )
        }
    }

    fun updateAdjustment(type: BasicAdjustType, value: Float) {
        _uiState.update {
            it.copy(
                basicValues = it.basicValues.withValue(type, value),
                error = null,
            )
        }
        scheduleAdjustPreview()
    }

    fun resetAdjustments() {
        adjustPreviewJob?.cancel()
        _uiState.update {
            it.copy(
                basicValues = BasicAdjustValues(),
                previewBitmap = null,
            )
        }
    }

    private fun scheduleAdjustPreview() {
        adjustPreviewJob?.cancel()
        adjustPreviewJob = viewModelScope.launch {
            delay(250)
            val currentUri = _uiState.value.imageUri ?: return@launch
            val values = _uiState.value.basicValues
            withContext(Dispatchers.IO) {
                when (val result = editEffectManager.applyBasicAdjustments(currentUri, values)) {
                    is EffectResult.Success -> {
                        _uiState.update { it.copy(previewBitmap = result.bitmap) }
                    }
                    is EffectResult.Error -> {
                        _uiState.update { it.copy(error = result.message) }
                    }
                }
            }
        }
    }

    fun applyBasicEdits() {
        val values = _uiState.value.basicValues
        if (values.isDefault) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            val before = editingStateManager.getCurrentState()
            editingStateManager.saveSnapshot(before.history.size)

            val parameters = mapOf(
                "brightness" to values.brightness.toString(),
                "contrast" to values.contrast.toString(),
                "saturation" to values.saturation.toString(),
                "shadows" to values.shadows.toString(),
                "highlights" to values.highlights.toString(),
                "temperature" to values.temperature.toString(),
                "vignette" to values.vignette.toString(),
            )

            var applyError: String? = null
            withContext(Dispatchers.IO) {
                val decoded = BitmapFactory.decodeFile(projectHandler.imageFile().absolutePath)
                if (decoded == null) {
                    applyError = "Failed to read image"
                    return@withContext
                }
                when (val result = editEffectManager.applyBasicAdjustments(decoded, values)) {
                    is EffectResult.Success -> {
                        FileOutputStream(projectHandler.imageFile()).use { out ->
                            result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        result.bitmap.recycle()
                    }
                    is EffectResult.Error -> {
                        applyError = result.message
                        decoded.recycle()
                        return@withContext
                    }
                }
                decoded.recycle()
            }

            if (applyError != null) {
                _uiState.update { it.copy(isProcessing = false, error = applyError) }
                return@launch
            }

            val next = editingStateManager.pushStep(
                EditStep(
                    id = System.currentTimeMillis(),
                    type = EditType.BASIC_ADJUST,
                    parameters = parameters,
                )
            )

            projectRepository.updateProject(
                stepCount = next.history.size,
                status = "edited",
            )
            projectRepository.regenerateThumbnail()

            loadImageDims()
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    previewBitmap = null,
                    basicValues = BasicAdjustValues(),
                    imageUri = imageFileUri(),
                    editHistory = next.history,
                )
            }
        }
    }

    // ── Basic Edit: Crop ───────────────────────────────────────────────

    fun selectCropAspect(aspect: CropAspectRatio) {
        _uiState.update { it.copy(cropAspectRatio = aspect) }
    }

    fun applyCrop(cropLeft: Int, cropTop: Int, cropRight: Int, cropBottom: Int) {
        val imageUri = _uiState.value.imageUri ?: return
        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop
        if (cropW <= 0 || cropH <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            val before = editingStateManager.getCurrentState()
            editingStateManager.saveSnapshot(before.history.size)

            val parameters = mapOf(
                "left" to cropLeft.toString(),
                "top" to cropTop.toString(),
                "width" to cropW.toString(),
                "height" to cropH.toString(),
            )

            when (val result = editEffectManager.crop(imageUri, cropLeft, cropTop, cropW, cropH)) {
                is EffectResult.Error -> {
                    _uiState.update { it.copy(isProcessing = false, error = result.message) }
                    return@launch
                }
                is EffectResult.Success -> {
                    withContext(Dispatchers.IO) {
                        FileOutputStream(projectHandler.imageFile()).use { out ->
                            result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        result.bitmap.recycle()
                    }

                    val next = editingStateManager.pushStep(
                        EditStep(
                            id = System.currentTimeMillis(),
                            type = EditType.CROP,
                            parameters = parameters,
                        )
                    )

                    projectRepository.updateProject(
                        stepCount = next.history.size,
                        status = "edited",
                    )
                    projectRepository.regenerateThumbnail()

                    loadImageDims()
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            cropActive = false,
                            imageUri = imageFileUri(),
                            editHistory = next.history,
                        )
                    }
                }
            }
        }
    }

    fun cancelCrop() {
        _uiState.update { it.copy(cropActive = false) }
    }

    // ── Basic Edit: Rotate / Flip ──────────────────────────────────────

    fun rotate90() {
        applySimpleTransform(EditType.ROTATE, mapOf("degrees" to "90")) { editEffectManager.rotate90(it) }
    }

    fun rotate270() {
        applySimpleTransform(EditType.ROTATE, mapOf("degrees" to "270")) { editEffectManager.rotate270(it) }
    }

    fun flipHorizontal() {
        applySimpleTransform(EditType.FLIP_HORIZONTAL) { editEffectManager.flipHorizontal(it) }
    }

    fun flipVertical() {
        applySimpleTransform(EditType.FLIP_VERTICAL) { editEffectManager.flipVertical(it) }
    }

    private fun applySimpleTransform(type: EditType, parameters: Map<String, String> = emptyMap(), operation: suspend (Uri) -> EffectResult) {
        val imageUri = _uiState.value.imageUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            val before = editingStateManager.getCurrentState()
            editingStateManager.saveSnapshot(before.history.size)

            when (val result = operation(imageUri)) {
                is EffectResult.Error -> {
                    _uiState.update { it.copy(isProcessing = false, error = result.message) }
                    return@launch
                }
                is EffectResult.Success -> {
                    withContext(Dispatchers.IO) {
                        FileOutputStream(projectHandler.imageFile()).use { out ->
                            result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        result.bitmap.recycle()
                    }

                    val next = editingStateManager.pushStep(
                        EditStep(
                            id = System.currentTimeMillis(),
                            type = type,
                            parameters = parameters,
                        )
                    )

                    projectRepository.updateProject(
                        stepCount = next.history.size,
                        status = "edited",
                    )
                    projectRepository.regenerateThumbnail()

                    loadImageDims()
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            imageUri = imageFileUri(),
                            editHistory = next.history,
                        )
                    }
                }
            }
        }
    }

    // ── Undo / Redo ────────────────────────────────────────────────────

    fun undo() {
        val state = editingStateManager.undo() ?: return
        editingStateManager.restoreSnapshot(state.history.size)

        viewModelScope.launch(Dispatchers.IO) {
            projectRepository.updateProject(
                status = "undo",
                stepCount = state.history.size
            )
        }

        _uiState.update {
            it.copy(
                imageUri = Uri.fromFile(projectHandler.imageFile()),
                resultBitmap = null,
                editHistory = state.history,
            )
        }
    }

    fun redo() {
        val state = editingStateManager.redo() ?: return

        _uiState.update { it.copy(editHistory = state.history) }

        viewModelScope.launch(Dispatchers.IO) {
            projectRepository.updateProject(
                stepCount = state.history.size,
                status = "redo"
            )
        }
    }

    private fun imageFileUri(): Uri {
        val file = projectHandler.imageFile()
        return "file://${file.absolutePath}?t=${System.currentTimeMillis()}".toUri()
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

    fun cancelDownload() {
        modelDownloadRepository.cancelDownload()
    }

    override fun onCleared() {
        super.onCleared()
        editingStateManager.closeProject()
        projectHandler.closeProject()
    }
}
