package app.yamy.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.ui.components.RetroRule
import app.yamy.ui.theme.RetroColors

/** Onglet Mémoire : rempli au bloc 4 (notes liées façon Obsidian). */
@Composable
fun MemoryScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.memory_title), style = MaterialTheme.typography.displaySmall, color = RetroColors.Secondary)
        RetroRule()
        Text(stringResource(R.string.memory_soon), style = MaterialTheme.typography.bodyLarge)
    }
}
