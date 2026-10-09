package app.yamy.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroRule
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.components.RetroTextField
import app.yamy.ui.theme.RetroColors

/** Onglet Mémoire : les notes reliées de Yamy, lisibles, modifiables et supprimables. */
@Composable
fun MemoryScreen(viewModel: MemoryViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsState()
    var confirmForget by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.memory_title), style = MaterialTheme.typography.displaySmall, color = RetroColors.Secondary, modifier = Modifier.weight(1f))
            if (ui.editing != null) {
                RetroTextButton(onClick = { viewModel.edit(null) }, label = stringResource(R.string.back))
            } else {
                RetroTextButton(onClick = onClose, label = stringResource(R.string.close))
            }
        }
        RetroRule()
        val editing = ui.editing
        if (editing != null) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("[[${editing.name}]]", style = MaterialTheme.typography.titleLarge)
                RetroTextField(editing.summary, { v -> viewModel.update { it.copy(summary = v.replace('\n', ' ')) } }, label = { Text(stringResource(R.string.memory_summary)) }, modifier = Modifier.fillMaxWidth())
                RetroTextField(editing.content, { v -> viewModel.update { it.copy(content = v) } }, label = { Text(stringResource(R.string.memory_content)) }, minLines = 5, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetroButton(onClick = viewModel::save) { Text(stringResource(R.string.save)) }
                    RetroTextButton(onClick = viewModel::delete, label = stringResource(R.string.delete))
                }
            }
            return@Column
        }
        Text(stringResource(R.string.memory_intro), style = MaterialTheme.typography.bodyLarge)
        if (ui.notes.isEmpty()) {
            Text(stringResource(R.string.memory_empty), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ui.notes, key = { it.name }) { note ->
                RetroCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.edit(note) }) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("[[${note.name}]]", style = MaterialTheme.typography.titleMedium)
                        Text(note.summary, style = MaterialTheme.typography.bodyMedium)
                        if (note.links.isNotEmpty()) {
                            Text(stringResource(R.string.links_label, note.links.joinToString(" · ")), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        RetroTextButton(onClick = { confirmForget = true }, label = stringResource(R.string.forget_all))
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.forget_all)) },
            text = { Text(stringResource(R.string.memory_intro)) },
            confirmButton = {
                RetroButton(onClick = {
                    confirmForget = false
                    viewModel.forgetAll()
                }) { Text(stringResource(R.string.forget_all)) }
            },
            dismissButton = { RetroTextButton(onClick = { confirmForget = false }, label = stringResource(R.string.cancel)) },
            containerColor = RetroColors.Surface,
        )
    }
}
