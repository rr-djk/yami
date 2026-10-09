package app.yamy.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroRule
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.theme.RetroColors
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionsScreen(viewModel: ChatViewModel, onOpenChat: () -> Unit, modifier: Modifier = Modifier) {
    val sessions by viewModel.sessionList.collectAsState()
    val ui by viewModel.state.collectAsState()
    var choosing by rememberSaveable { mutableStateOf(false) }
    var toDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sessions_title), style = MaterialTheme.typography.displaySmall, color = RetroColors.Secondary)
        RetroRule()
        RetroButton(onClick = { choosing = !choosing }) { Text(stringResource(R.string.new_session)) }
        if (choosing) {
            Text(stringResource(R.string.choose_starter), style = MaterialTheme.typography.bodyLarge)
            Starters.ALL.forEach { starter ->
                RetroCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        choosing = false
                        val title = if (starter.topic.isEmpty()) Session.DEFAULT_TITLE else starter.title
                        viewModel.newSession(title, starter.topic, onOpenChat)
                    },
                ) {
                    Text("${starter.emoji}  ${starter.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sessions, key = { it.id }) { session ->
                val current = session.id == ui.session?.id
                RetroCard(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openSession(session.id, onOpenChat) },
                    containerColor = if (current) RetroColors.Primary else RetroColors.Surface,
                ) {
                    Row(modifier = Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.updatedAt)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        RetroTextButton(onClick = { toDelete = session.id }, label = stringResource(R.string.delete))
                    }
                }
            }
        }
    }

    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_session)) },
            text = { Text(stringResource(R.string.delete_session_confirm)) },
            confirmButton = {
                RetroButton(onClick = {
                    viewModel.deleteSession(id)
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { RetroTextButton(onClick = { toDelete = null }, label = stringResource(R.string.cancel)) },
            containerColor = RetroColors.Surface,
        )
    }
}
