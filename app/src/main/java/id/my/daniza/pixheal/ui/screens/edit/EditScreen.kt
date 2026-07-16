package id.my.daniza.pixheal.ui.screens.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import id.my.daniza.pixheal.data.ui.BasicAdjustType
import id.my.daniza.pixheal.data.ui.BasicEditSubTool
import id.my.daniza.pixheal.data.ui.BgRemovalMode
import id.my.daniza.pixheal.data.ui.CropAspectRatio
import id.my.daniza.pixheal.data.ui.EditTool
import id.my.daniza.pixheal.data.ui.EditUiState
import id.my.daniza.pixheal.viewmodel.edit.EditViewModel
import kotlin.math.abs

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
                EditTool.ENHANCER -> EnhancerContent(viewModel = viewModel, uiState = uiState)
                EditTool.OBJ_REMOVAL -> ObjRemovalContent(viewModel = viewModel, uiState = uiState)
                EditTool.BASIC_EDIT -> BasicEditContent(viewModel = viewModel, uiState = uiState)
                EditTool.BG_REMOVAL -> BgRemovalContent(viewModel = viewModel, uiState = uiState)
                EditTool.EXPORT -> ExportContent(viewModel = viewModel, uiState = uiState)
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
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, MAX_SCALE)
                    val previousCentroid = centroid - pan
                    val imagePoint = (previousCentroid - offset) / oldScale
                    offset = centroid - imagePoint * newScale
                    scale = newScale
                    offset = clampOffsetToFrame(
                        offset, scale, containerSize.width, containerSize.height,
                        uiState.imageWidth, uiState.imageHeight,
                    )
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
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, MAX_SCALE)
                    val previousCentroid = centroid - pan
                    val imagePoint = (previousCentroid - offset) / oldScale
                    offset = centroid - imagePoint * newScale
                    scale = newScale
                    offset = clampOffsetToFrame(
                        offset, scale, containerSize.width, containerSize.height,
                        uiState.imageWidth, uiState.imageHeight,
                    )
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
private fun ColumnScope.BasicEditContent(viewModel: EditViewModel, uiState: EditUiState) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val imageW = uiState.imageWidth
    val imageH = uiState.imageHeight
    val cropActive = uiState.cropActive

    var cropLeft by remember(imageW, imageH) { mutableFloatStateOf(0f) }
    var cropTop by remember(imageW, imageH) { mutableFloatStateOf(0f) }
    var cropRight by remember(imageW, imageH) { mutableFloatStateOf(imageW.toFloat()) }
    var cropBottom by remember(imageW, imageH) { mutableFloatStateOf(imageH.toFloat()) }

    if (cropActive && imageW > 0 && imageH > 0) {
        val targetRatio = uiState.cropAspectRatio.ratio
        if (targetRatio != null) {
            val currentWidth = cropRight - cropLeft
            val currentHeight = cropBottom - cropTop
            if (currentHeight > 0) {
                val currentRatio = currentWidth / currentHeight
                if (abs(currentRatio - targetRatio) > 0.001f) {
                    val cx = (cropLeft + cropRight) / 2f
                    val cy = (cropTop + cropBottom) / 2f
                    if (currentRatio > targetRatio) {
                        val newW = currentHeight * targetRatio
                        cropLeft = (cx - newW / 2f).coerceAtLeast(0f)
                        cropRight = (cx + newW / 2f).coerceAtMost(imageW.toFloat())
                    } else {
                        val newH = currentWidth / targetRatio
                        cropTop = (cy - newH / 2f).coerceAtLeast(0f)
                        cropBottom = (cy + newH / 2f).coerceAtMost(imageH.toFloat())
                    }
                }
            }
        }
    }

    val cropOverlayBitmap = remember(cropLeft, cropTop, cropRight, cropBottom, imageW, imageH) {
        if (imageW <= 0 || imageH <= 0) return@remember null
        val bmp = Bitmap.createBitmap(imageW, imageH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint().apply { isAntiAlias = true }

        p.color = Color.argb(136, 0, 0, 0)
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, imageW.toFloat(), imageH.toFloat(), p)

        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        p.color = 0
        c.drawRect(cropLeft, cropTop, cropRight, cropBottom, p)

        p.xfermode = null
        p.color = Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f
        c.drawRect(cropLeft, cropTop, cropRight, cropBottom, p)

        p.strokeWidth = 0.8f
        p.alpha = 80
        for (i in 1..2) {
            val x = cropLeft + (cropRight - cropLeft) * i / 3f
            val y = cropTop + (cropBottom - cropTop) * i / 3f
            c.drawLine(x, cropTop, x, cropBottom, p)
            c.drawLine(cropLeft, y, cropRight, y, p)
        }

        bmp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onGloballyPositioned { containerSize = it.size }
            .then(
                if (cropActive && imageW > 0 && imageH > 0) {
                    Modifier.pointerInput(imageW, imageH) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val imgCoord = screenToImageCoord(
                                centroid.x, centroid.y,
                                containerSize.width, containerSize.height,
                                imageW, imageH, 1f, 0f, 0f,
                            ) ?: return@detectTransformGestures

                            if (abs(zoom - 1f) > 0.005f) {
                                val cx = (cropLeft + cropRight) / 2f
                                val cy = (cropTop + cropBottom) / 2f
                                val halfW = (cropRight - cropLeft) / 2f * zoom
                                val halfH = (cropBottom - cropTop) / 2f * zoom
                                var nl = cx - halfW
                                var nt = cy - halfH
                                var nr = cx + halfW
                                var nb = cy + halfH
                                val ratio = uiState.cropAspectRatio.ratio
                                if (ratio != null) {
                                    val nw = nr - nl
                                    val nh = nb - nt
                                    if (nh > 0 && nw / nh > ratio) {
                                        val aw = nh * ratio
                                        nl = cx - aw / 2f
                                        nr = cx + aw / 2f
                                    } else if (nw > 0) {
                                        val ah = nw / ratio
                                        nt = cy - ah / 2f
                                        nb = cy + ah / 2f
                                    }
                                }
                                cropLeft = nl.coerceIn(0f, (imageW - 1).toFloat())
                                cropTop = nt.coerceIn(0f, (imageH - 1).toFloat())
                                cropRight = nr.coerceIn(1f, imageW.toFloat())
                                cropBottom = nb.coerceIn(1f, imageH.toFloat())
                            }

                            if (pan != Offset.Zero) {
                                val (ix, iy) = imgCoord
                                val inside = ix >= cropLeft && ix <= cropRight &&
                                             iy >= cropTop && iy <= cropBottom
                                if (inside) {
                                    val fitRect = imageFitRect(
                                        containerSize.width, containerSize.height, imageW, imageH,
                                    )
                                    val imgPanX = if (fitRect.width() > 0) pan.x / fitRect.width() * imageW else 0f
                                    val imgPanY = if (fitRect.height() > 0) pan.y / fitRect.height() * imageH else 0f
                                    val cropW = cropRight - cropLeft
                                    val cropH = cropBottom - cropTop
                                    cropLeft = (cropLeft + imgPanX).coerceIn(0f, imageW - cropW)
                                    cropTop = (cropTop + imgPanY).coerceIn(0f, imageH - cropH)
                                    cropRight = cropLeft + cropW
                                    cropBottom = cropTop + cropH
                                }
                            }
                        }
                    }
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (oldScale * zoom).coerceIn(1f, MAX_SCALE)
                            val previousCentroid = centroid - pan
                            val imagePoint = (previousCentroid - offset) / oldScale
                            offset = centroid - imagePoint * newScale
                            scale = newScale
                            offset = clampOffsetToFrame(
                                offset, scale, containerSize.width, containerSize.height,
                                imageW, imageH,
                            )
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        val displayBitmap = uiState.previewBitmap ?: uiState.resultBitmap
        val imageUri = uiState.imageUri

        if (displayBitmap != null) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = "Edited image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (cropActive) 1f else scale
                        scaleY = if (cropActive) 1f else scale
                        translationX = if (cropActive) 0f else offset.x
                        translationY = if (cropActive) 0f else offset.y
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
                        scaleX = if (cropActive) 1f else scale
                        scaleY = if (cropActive) 1f else scale
                        translationX = if (cropActive) 0f else offset.x
                        translationY = if (cropActive) 0f else offset.y
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

        if (cropActive && cropOverlayBitmap != null) {
            Image(
                bitmap = cropOverlayBitmap.asImageBitmap(),
                contentDescription = "Crop overlay",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1f
                        scaleY = 1f
                        translationX = 0f
                        translationY = 0f
                        alpha = 1f
                    },
                contentScale = ContentScale.Fit,
            )
        }

        if (uiState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0x64000000)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicEditSubTool.entries.forEach { subTool ->
            val selected = uiState.basicSubTool == subTool
            AssistChip(
                onClick = { viewModel.selectBasicSubTool(subTool) },
                label = { Text(subTool.displayName(), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.background(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ),
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (uiState.basicSubTool) {
        BasicEditSubTool.ADJUST -> AdjustPanel(viewModel = viewModel, uiState = uiState)
        BasicEditSubTool.CROP -> CropPanel(
            viewModel = viewModel,
            uiState = uiState,
            cropLeft = cropLeft.toInt(),
            cropTop = cropTop.toInt(),
            cropRight = cropRight.toInt(),
            cropBottom = cropBottom.toInt(),
        )
        BasicEditSubTool.ROTATE -> RotatePanel(viewModel = viewModel, uiState = uiState)
    }
}

@Composable
private fun ColumnScope.AdjustPanel(viewModel: EditViewModel, uiState: EditUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.45f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicAdjustType.entries.forEach { adjustType ->
            val value = when (adjustType) {
                BasicAdjustType.BRIGHTNESS -> uiState.basicValues.brightness
                BasicAdjustType.CONTRAST -> uiState.basicValues.contrast
                BasicAdjustType.SATURATION -> uiState.basicValues.saturation
                BasicAdjustType.SHADOWS -> uiState.basicValues.shadows
                BasicAdjustType.HIGHLIGHTS -> uiState.basicValues.highlights
                BasicAdjustType.TEMPERATURE -> uiState.basicValues.temperature
                BasicAdjustType.VIGNETTE -> uiState.basicValues.vignette
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    adjustType.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(80.dp),
                )
                Slider(
                    value = value,
                    onValueChange = { viewModel.updateAdjustment(adjustType, it) },
                    valueRange = adjustType.min..adjustType.max,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    value.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                )
                IconButton(
                    onClick = { viewModel.updateAdjustment(adjustType, adjustType.default) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Reset ${adjustType.label}",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { viewModel.applyBasicEdits() },
                enabled = !uiState.isProcessing && !uiState.basicValues.isDefault,
                modifier = Modifier.weight(1f),
            ) {
                Text("Apply", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = { viewModel.resetAdjustments() },
                enabled = !uiState.isProcessing && !uiState.basicValues.isDefault,
            ) {
                Text("Reset All", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CropPanel(
    viewModel: EditViewModel,
    uiState: EditUiState,
    cropLeft: Int,
    cropTop: Int,
    cropRight: Int,
    cropBottom: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Aspect Ratio", style = MaterialTheme.typography.labelSmall)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropAspectRatio.entries.forEach { aspect ->
                val selected = uiState.cropAspectRatio == aspect
                AssistChip(
                    onClick = { viewModel.selectCropAspect(aspect) },
                    label = {
                        Text(
                            aspect.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                )
            }
        }

        Text(
            "${cropRight - cropLeft} x ${cropBottom - cropTop}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { viewModel.applyCrop(cropLeft, cropTop, cropRight, cropBottom) },
                enabled = !uiState.isProcessing && (cropRight - cropLeft) > 0 && (cropBottom - cropTop) > 0,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Apply Crop", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = { viewModel.cancelCrop() },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RotatePanel(viewModel: EditViewModel, uiState: EditUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Rotate", style = MaterialTheme.typography.labelSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { viewModel.rotate270() },
                enabled = !uiState.isProcessing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Rotate90DegreesCw, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("90\u00B0 CCW", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick = { viewModel.rotate90() },
                enabled = !uiState.isProcessing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Rotate90DegreesCw, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("90\u00B0 CW", style = MaterialTheme.typography.labelSmall)
            }
        }

        Text("Flip", style = MaterialTheme.typography.labelSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { viewModel.flipHorizontal() },
                enabled = !uiState.isProcessing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Flip H", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
                onClick = { viewModel.flipVertical() },
                enabled = !uiState.isProcessing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.FlipToBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Flip V", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
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

private fun imageFitRect(
    containerW: Int,
    containerH: Int,
    imageW: Int,
    imageH: Int,
): android.graphics.RectF {
    val containerAspect = containerW.toFloat() / containerH
    val imageAspect = imageW.toFloat() / imageH
    val fitW: Float
    val fitH: Float
    if (imageAspect > containerAspect) {
        fitW = containerW.toFloat()
        fitH = fitW / imageAspect
    } else {
        fitH = containerH.toFloat()
        fitW = fitH * imageAspect
    }
    val left = (containerW - fitW) / 2f
    val top = (containerH - fitH) / 2f
    return android.graphics.RectF(left, top, left + fitW, top + fitH)
}

private const val MAX_SCALE = 5f

/**
 * Clamps the translation [offset] so the scaled (and letterboxed) bitmap stays
 * inside the container on both axes. At [scale] <= 1 the bitmap already fits the
 * frame on its constraining axis, so the clamp pins it centered — preventing any
 * overflow when zoomed out to 1:1.
 */
private fun clampOffsetToFrame(
    offset: Offset,
    scale: Float,
    containerW: Int,
    containerH: Int,
    imageW: Int,
    imageH: Int,
): Offset {
    if (containerW <= 0 || containerH <= 0 || imageW <= 0 || imageH <= 0) return offset
    val fit = imageFitRect(containerW, containerH, imageW, imageH)
    val scaledW = fit.width() * scale
    val scaledH = fit.height() * scale
    val offsetX = if (scaledW <= containerW) {
        0f
    } else {
        // Symmetric range keeping the scaled image within the frame on both edges.
        offset.x.coerceIn((containerW - scaledW) / 2f, (scaledW - containerW) / 2f)
    }
    val offsetY = if (scaledH <= containerH) {
        0f
    } else {
        offset.y.coerceIn((containerH - scaledH) / 2f, (scaledH - containerH) / 2f)
    }
    return Offset(offsetX, offsetY)
}

private fun BasicEditSubTool.displayName(): String = when (this) {
    BasicEditSubTool.ADJUST -> "Adjust"
    BasicEditSubTool.CROP -> "Crop"
    BasicEditSubTool.ROTATE -> "Rotate"
}

private fun EditStep.displayName(): String = when (type) {
    EditType.ESRGAN_ENHANCE -> "Enhanced"
    EditType.INPAINTING -> "Inpainted"
    EditType.CROP -> "Cropped"
    EditType.ROTATE -> "Rotated"
    EditType.BASIC_ADJUST -> "Adjusted"
    EditType.FLIP_HORIZONTAL -> "Flipped H"
    EditType.FLIP_VERTICAL -> "Flipped V"
    EditType.BG_REMOVAL -> "BG Removed"
}

@Composable
private fun ColumnScope.BgRemovalContent(viewModel: EditViewModel, uiState: EditUiState) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(uiState.bgRemovalMode) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, MAX_SCALE)
                    val previousCentroid = centroid - pan
                    val imagePoint = (previousCentroid - offset) / oldScale
                    offset = centroid - imagePoint * newScale
                    scale = newScale
                    offset = clampOffsetToFrame(
                        offset, scale, containerSize.width, containerSize.height,
                        uiState.imageWidth, uiState.imageHeight,
                    )
                }
            }
            .pointerInput(uiState.bgRemovalMode, uiState.isProcessing, uiState.isSegmenting) {
                if (uiState.isProcessing || uiState.isSegmenting) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val position = change.position

                        val imgW = uiState.imageWidth
                        val imgH = uiState.imageHeight
                        if (imgW <= 0 || imgH <= 0) continue

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
                                viewModel.handleBgTouchStart(imageCoord.first, imageCoord.second)
                            } else {
                                viewModel.handleBgTouchMove(imageCoord.first, imageCoord.second)
                            }
                            change.consume()
                        } else if (change.previousPressed) {
                            viewModel.handleBgTouchEnd()
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val imageUri = uiState.imageUri

        if (imageUri != null) {
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

            val overlay = uiState.segmentationOverlay
            if (overlay != null) {
                Image(
                    bitmap = overlay.asImageBitmap(),
                    contentDescription = "Segmentation overlay",
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
            }

            val maskBitmap = uiState.maskBitmap
            if (uiState.bgRemovalMode == BgRemovalMode.MANUAL && maskBitmap != null) {
                Image(
                    bitmap = maskBitmap.asImageBitmap(),
                    contentDescription = "Manual mask overlay",
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

        if (uiState.isSegmenting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0x64000000)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BgRemovalMode.entries.forEach { mode ->
            val selected = uiState.bgRemovalMode == mode
            AssistChip(
                onClick = { viewModel.selectBgRemovalMode(mode) },
                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.background(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ),
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (uiState.bgRemovalMode) {
        BgRemovalMode.AUTO -> {
            if (uiState.segmentationOverlay != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Threshold", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = uiState.bgThreshold,
                        onValueChange = { viewModel.setBgThreshold(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    Text("Hardness", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = uiState.bgHardness,
                        onValueChange = { viewModel.setBgHardness(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    Text("Edge Soften", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = uiState.bgEdgeSoften,
                        onValueChange = { viewModel.setBgEdgeSoften(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.applyBgRemoval() },
                            enabled = !uiState.isProcessing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Remove Background", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = { viewModel.cancelBgRemoval() },
                            enabled = !uiState.isProcessing,
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Tap anywhere to detect the person and remove background automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { viewModel.runAutoSegment() },
                        enabled = !uiState.isSegmenting,
                    ) {
                        if (uiState.isSegmenting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Segmenting...")
                        } else {
                            Text("Auto-Select Person")
                        }
                    }
                }
            }

            if (uiState.isProcessing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Removing background...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        BgRemovalMode.MANUAL -> {
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                IconButton(
                    onClick = { viewModel.clearManualMask() },
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
                    modifier = Modifier.clickable(enabled = !uiState.isProcessing) { viewModel.clearManualMask() },
                )
            }

            Text(
                "Draw on the area you want to keep (the rest will be removed)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = { viewModel.applyManualBgRemoval() },
                enabled = uiState.maskBitmap != null && !uiState.isProcessing,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                if (uiState.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Removing...")
                } else {
                    Text("Remove Background")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
