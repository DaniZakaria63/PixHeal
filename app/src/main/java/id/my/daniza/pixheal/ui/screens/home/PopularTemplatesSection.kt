package id.my.daniza.pixheal.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class TemplateItem(
    val id: Int,
    val label: String,
    val iconRes: Int
)

private val popularTemplates = listOf(
    TemplateItem(1, "Super Resolution", android.R.drawable.ic_menu_gallery),
    TemplateItem(2, "Inpainting", android.R.drawable.ic_menu_edit),
    TemplateItem(3, "Photo Restore", android.R.drawable.ic_menu_camera),
    TemplateItem(4, "Colorize", android.R.drawable.ic_menu_slideshow),
    TemplateItem(5, "Denoise", android.R.drawable.ic_menu_sort_by_size),
    TemplateItem(6, "Face Enhance", android.R.drawable.ic_menu_myplaces),
)

@Composable
fun PopularTemplatesSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Popular Templates",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            popularTemplates.chunked(3).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowItems.forEach { template ->
                        TemplateCard(
                            template = template,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: TemplateItem, modifier: Modifier = Modifier) {
    Card(
        onClick = { /* TODO: navigate to template editor */ },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(template.iconRes),
                contentDescription = template.label,
                modifier = Modifier.padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = template.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
