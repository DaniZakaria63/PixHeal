package id.my.daniza.pixheal.ui.screens.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DownloadModelDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    viewModel: EditViewModel,
) {
    val downloadState by viewModel.downloadState.collectAsState()

    if (!showDialog) return

    AlertDialog(
        onDismissRequest = {
            if (!downloadState.isDownloading) onDismiss()
        },
        title = {
            Text(
                "Download AOT-GAN Model",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                when {
                    downloadState.error != null -> {
                        Text(
                            "Download failed: ${downloadState.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    downloadState.isComplete -> {
                        Text(
                            "Model downloaded successfully!",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    downloadState.isDownloading -> {
                        Text(
                            "Downloading AOT-GAN model...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${(downloadState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                downloadState.isComplete -> {
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
                downloadState.error != null -> {
                    Row {
                        TextButton(onClick = {
                            viewModel.cancelDownload()
                            onDismiss()
                        }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (downloadState.isDownloading) {
                TextButton(onClick = {
                    viewModel.cancelDownload()
                    onDismiss()
                }) {
                    Text("Cancel Download")
                }
            }
        },
    )
}
