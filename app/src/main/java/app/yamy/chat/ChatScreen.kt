package app.yamy.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yamy.R

@Composable
fun ChatScreen(viewModel: ChatViewModel, modelPath: String) {
    val ui by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    val rows = remember(ui.messages, ui.draft) {
        if (ui.draft.isBlank()) ui.messages else ui.messages + ChatMessage(id = -1, role = Role.YAMY, text = ui.draft)
    }
    LaunchedEffect(rows.size, ui.draft.length) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = viewModel::clearConversation) { Text(stringResource(R.string.clear_conversation)) }
        }
        if (ui.status == EngineStatus.LOADING || ui.status == EngineStatus.GENERATING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.status == EngineStatus.MODEL_MISSING -> Notice(stringResource(R.string.model_missing, modelPath))
                ui.status == EngineStatus.ERROR -> Notice(ui.error.orEmpty())
                rows.isEmpty() -> Notice(stringResource(R.string.empty_chat))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.id.takeIf { id -> id != -1L } ?: Long.MIN_VALUE }) { MessageBubble(it) }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Message pour Yamy" },
                placeholder = { Text(stringResource(R.string.input_hint)) },
                maxLines = 4,
            )
            Button(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                enabled = ui.status == EngineStatus.READY && input.isNotBlank(),
            ) { Text(stringResource(R.string.send)) }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val mine = message.role == Role.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
