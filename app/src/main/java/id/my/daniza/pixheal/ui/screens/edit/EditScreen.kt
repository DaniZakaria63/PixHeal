package id.my.daniza.pixheal.ui.screens.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.automirrored.filled.ListAlt
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import id.my.daniza.pixheal.data.editing.EditStep
import id.my.daniza.pixheal.data.editing.EditType

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
                        // ── History toggle ─────────────────────────
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
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = "Enhancer") },
                    label = { Text("Enhancer") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Filled.BackHand, contentDescription = "Obj Removal") },
                    label = { Text("Obj Removal") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Filled.Crop, contentDescription = "Basic Edit") },
                    label = { Text("Basic Edit") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Filled.LayersClear, contentDescription = "BG Removal") },
                    label = { Text("BG Removal") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Filled.FileDownload, contentDescription = "Export") },
                    label = { Text("Export") },
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(bottom = paddingValues.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Image canvas ─────────────────────────────────────────
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val imageUri = uiState.imageUri

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
                        Text(
                            "Loading project…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Enhance button ───────────────────────────────────────
            FilledTonalButton(
                onClick = { viewModel.enhance() },
                enabled = !uiState.isProcessing && uiState.imageUri != null,
                modifier = Modifier
                    .padding(horizontal = 12.dp),
            ) {
                if (uiState.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing…")
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

            // ── Quality mode toggle ──────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text(
                    "Mode:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Fast",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (!uiState.qualityMode)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (!uiState.qualityMode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable(enabled = !uiState.isProcessing) { viewModel.toggleQualityMode() },
                )
                Text(
                    "Quality",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.qualityMode)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (uiState.qualityMode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable(enabled = !uiState.isProcessing) { viewModel.toggleQualityMode() },
                )
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun EditStep.displayName(): String = when (type) {
    EditType.ESRGAN_ENHANCE -> "Enhanced"
    EditType.INPAINTING -> "Inpainted"
    EditType.CROP -> "Cropped"
    EditType.ROTATE -> "Rotated"
    EditType.ADJUST_BRIGHTNESS -> "Brightness"
    EditType.ADJUST_CONTRAST -> "Contrast"
}
