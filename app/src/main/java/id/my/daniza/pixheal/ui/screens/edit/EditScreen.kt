package id.my.daniza.pixheal.ui.screens.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import id.my.daniza.pixheal.data.editing.EditStep
import id.my.daniza.pixheal.data.editing.EditType
import id.my.daniza.pixheal.data.ui.EditTool
import id.my.daniza.pixheal.data.ui.EditUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.initProject(projectId)
    }

    DownloadModelDialog(
        showDialog = uiState.showDownloadDialog,
        onDismiss = { viewModel.dismissDownloadDialog() },
        viewModel = viewModel,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = uiState.canUndo,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = uiState.canRedo,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Box {
                            IconButton(onClick = { viewModel.toggleHistory() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ListAlt,
                                    contentDescription = "Edit history",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = uiState.showHistory,
                                onDismissRequest = { viewModel.toggleHistory() },
                            ) {
                                if (uiState.editHistory.isEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "No edits yet",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        onClick = { },
                                        enabled = false,
                                    )
                                } else {
                                    Text(
                                        "${uiState.editHistory.size} edit(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                    HorizontalDivider()
                                    uiState.editHistory
                                        .asReversed()
                                        .forEachIndexed { i, step ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        Text(
                                                            "${i + 1}.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                        Text(
                                                            step.displayName(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                },
                                                onClick = { },
                                                enabled = false,
                                            )
                                        }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
        bottomBar = {
            BottomToolBar(viewModel = viewModel, selectedTool = uiState.selectedTool)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(bottom = paddingValues.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState.selectedTool) {
                EditTool.OBJ_REMOVAL -> ObjRemovalContent(viewModel = viewModel, uiState = uiState)
                else -> EnhancerContent(viewModel = viewModel, uiState = uiState)
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomToolBar(viewModel: EditViewModel, selectedTool: EditTool) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTool == EditTool.ENHANCER,
            onClick = { viewModel.selectTool(EditTool.ENHANCER) },
            icon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null) },
            label = { Text("Enhancer") },
        )
        NavigationBarItem(
            selected = selectedTool == EditTool.OBJ_REMOVAL,
            onClick = { viewModel.selectTool(EditTool.OBJ_REMOVAL) },
            icon = { Icon(Icons.Filled.BackHand, contentDescription = null) },
            label = { Text("Obj Removal") },
        )
        NavigationBarItem(
            selected = selectedTool == EditTool.BASIC_EDIT,
            onClick = { viewModel.selectTool(EditTool.BASIC_EDIT) },
            icon = { Icon(Icons.Filled.Crop, contentDescription = null) },
            label = { Text("Basic Edit") },
        )
        NavigationBarItem(
            selected = selectedTool == EditTool.BG_REMOVAL,
            onClick = { viewModel.selectTool(EditTool.BG_REMOVAL) },
            icon = { Icon(Icons.Filled.LayersClear, contentDescription = null) },
            label = { Text("BG Removal") },
        )
        NavigationBarItem(
            selected = selectedTool == EditTool.EXPORT,
            onClick = { viewModel.selectTool(EditTool.EXPORT) },
            icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
            label = { Text("Export") },
        )
    }
}

@Composable
private fun ColumnScope.EnhancerContent(viewModel: EditViewModel, uiState: EditUiState) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, 5f)
                    val previousCentroid = centroid - pan
                    val imagePoint = (previousCentroid - offset) / oldScale
                    offset = centroid - imagePoint * newScale
                    scale = newScale
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val resultBitmap = uiState.resultBitmap
        val imageUri = uiState.imageUri

        if (resultBitmap != null) {
            Image(
                bitmap = resultBitmap.asImageBitmap(),
                contentDescription = "Enhanced image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
        } else if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Image preview",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading project...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    FilledTonalButton(
        onClick = { viewModel.enhance() },
        enabled = !uiState.isProcessing && uiState.imageUri != null,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        if (uiState.isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Processing...")
        } else {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_gallery),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enhance with ESRGAN")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    QualityModeToggle(viewModel = viewModel, uiState = uiState)
}

@Composable
private fun ColumnScope.ObjRemovalContent(viewModel: EditViewModel, uiState: EditUiState) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    if (uiState.isCheckingModel) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Checking model...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
            }
            .pointerInput(uiState.modelAvailable) {
                if (!uiState.modelAvailable) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                }
            }
            .pointerInput(uiState.modelAvailable, uiState.isProcessing) {
                if (!uiState.modelAvailable || uiState.isProcessing) return@pointerInput
                val mask = uiState.maskBitmap ?: return@pointerInput
                val imgW = mask.width
                val imgH = mask.height
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val position = change.position

                        val imageCoord = screenToImageCoord(
                            screenX = position.x,
                            screenY = position.y,
                            containerW = containerSize.width,
                            containerH = containerSize.height,
                            imageW = imgW,
                            imageH = imgH,
                            scale = scale,
                            offsetX = offset.x,
                            offsetY = offset.y,
                        ) ?: continue

                        if (change.pressed) {
                            if (!change.previousPressed) {
                                viewModel.onMaskDrawStart(imageCoord.first, imageCoord.second)
                            } else {
                                viewModel.onMaskDrawMove(imageCoord.first, imageCoord.second)
                            }
                            change.consume()
                        } else if (change.previousPressed) {
                            viewModel.onMaskDrawEnd()
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val imageUri = uiState.imageUri

        if (!uiState.modelAvailable) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AOT-GAN model required", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Download the model to use object removal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { viewModel.triggerModelDownload() }) {
                    Text("Download Model")
                }
            }
        } else if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )

            val maskBitmap = uiState.maskBitmap
            if (maskBitmap != null) {
                Image(
                    bitmap = maskBitmap.asImageBitmap(),
                    contentDescription = "Mask overlay",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                            alpha = 0.4f
                        },
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading project...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (uiState.modelAvailable) {
        if (uiState.showDrawGuide) {
            Text(
                "Draw on the object you want to remove",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            TextButton(onClick = { viewModel.dismissDrawGuide() }) {
                Text("Got it", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Brush:", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = uiState.brushRadius,
                onValueChange = { viewModel.setBrushRadius(it) },
                valueRange = 10f..100f,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${uiState.brushRadius.toInt()}px",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (uiState.isProcessing) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Removing object...", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            IconButton(
                onClick = { viewModel.clearMask() },
                enabled = !uiState.isProcessing,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Clear mask",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                "Clear mask",
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.isProcessing) MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(enabled = !uiState.isProcessing) { viewModel.clearMask() },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalButton(
            onClick = { viewModel.triggerInpainting() },
            enabled = uiState.maskBitmap != null && !uiState.isProcessing,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            if (uiState.isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Removing...")
            } else {
                Text("Remove Object")
            }
        }
    }
}

@Composable
private fun QualityModeToggle(viewModel: EditViewModel, uiState: EditUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Text("Mode:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Fast",
            style = MaterialTheme.typography.labelMedium,
            color = if (!uiState.qualityMode) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(
                    if (!uiState.qualityMode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable(enabled = !uiState.isProcessing) { viewModel.toggleQualityMode() },
        )
        Text(
            "Quality",
            style = MaterialTheme.typography.labelMedium,
            color = if (uiState.qualityMode) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(
                    if (uiState.qualityMode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable(enabled = !uiState.isProcessing) { viewModel.toggleQualityMode() },
        )
    }
}

private fun screenToImageCoord(
    screenX: Float,
    screenY: Float,
    containerW: Int,
    containerH: Int,
    imageW: Int,
    imageH: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): Pair<Float, Float>? {
    if (containerW <= 0 || containerH <= 0 || imageW <= 0 || imageH <= 0) return null

    val containerAspect = containerW.toFloat() / containerH
    val imageAspect = imageW.toFloat() / imageH

    val (fitW, fitH) = if (imageAspect > containerAspect) {
        val w = containerW.toFloat()
        val h = w / imageAspect
        w to h
    } else {
        val h = containerH.toFloat()
        val w = h * imageAspect
        w to h
    }

    val fitOffsetX = (containerW - fitW) / 2f
    val fitOffsetY = (containerH - fitH) / 2f

    val imgX = ((screenX - fitOffsetX - offsetX) / (fitW * scale)) * imageW
    val imgY = ((screenY - fitOffsetY - offsetY) / (fitH * scale)) * imageH

    if (imgX < 0 || imgX > imageW || imgY < 0 || imgY > imageH) return null

    return Pair(imgX, imgY)
}

private fun EditStep.displayName(): String = when (type) {
    EditType.ESRGAN_ENHANCE -> "Enhanced"
    EditType.INPAINTING -> "Inpainted"
    EditType.CROP -> "Cropped"
    EditType.ROTATE -> "Rotated"
    EditType.ADJUST_BRIGHTNESS -> "Brightness"
    EditType.ADJUST_CONTRAST -> "Contrast"
}
