package app.yamy.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.connect.NetworkMode
import app.yamy.mcp.discord.DiscordDraft
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.theme.RetroColors

/** Bandeau toujours visible : Hors ligne ou Connecté. Le toucher ouvre le réglage. */
@Composable
fun NetworkIndicator(mode: NetworkMode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (mode == NetworkMode.CONNECTED) R.string.mode_connected else R.string.mode_offline),
            style = MaterialTheme.typography.labelLarge,
            color = RetroColors.Ink,
        )
    }
}

/** Gemma propose, la personne dispose : rien ne part sans « Publier ». Le message est affiché en entier. */
@Composable
fun DiscordConfirmCard(draft: DiscordDraft, connected: Boolean, sending: Boolean, generating: Boolean, onSend: () -> Unit, onCancel: () -> Unit) {
    RetroCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), containerColor = RetroColors.Surface) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.discord_card_title), style = MaterialTheme.typography.titleMedium, color = RetroColors.Secondary)
            Text(stringResource(R.string.discord_channel, draft.channel.label), style = MaterialTheme.typography.bodyLarge)
            Text(draft.content, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.message_size, draft.content.lines().size, draft.content.length), style = MaterialTheme.typography.bodySmall)
            if (!connected) Text(stringResource(R.string.message_offline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroButton(onClick = onSend, enabled = connected && !sending && !generating) {
                    Text(stringResource(if (sending) R.string.message_sending else R.string.message_send))
                }
                RetroTextButton(onClick = onCancel, label = stringResource(R.string.cancel), enabled = !sending)
            }
        }
    }
}
