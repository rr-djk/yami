package app.yamy.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.connect.NetworkMode
import app.yamy.connect.NetworkModeStore
import app.yamy.mcp.discord.DiscordConnector
import app.yamy.mcp.discord.DiscordTokenStore
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.semantics.Role
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroOutlinedButton
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.components.RetroTextField
import app.yamy.ui.theme.RetroColors

/** Switch Hors ligne / Connecté et configuration du connecteur MCP Discord (bot). */
@Composable
fun ConnectorsSection(
    network: NetworkModeStore,
    token: DiscordTokenStore,
    discord: DiscordConnector,
    onChanged: () -> Unit = {},
    onTest: ((String) -> Unit) -> Unit = {},
    onRemoved: () -> Unit = {},
) {
    val mode by network.mode.collectAsState()
    var confirming by rememberSaveable { mutableStateOf(false) }
    var configured by remember { mutableStateOf(token.load() != null) }
    // Jamais dans l'état sauvegardé du système : `remember`, pas `rememberSaveable`.
    var typed by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    // Relu après chaque test ou coche (les salons et approbations vivent dans le connecteur).
    var tick by remember { mutableStateOf(0) }
    val connected = mode == NetworkMode.CONNECTED

    RetroCard(modifier = Modifier.fillMaxWidth(), containerColor = if (connected) RetroColors.Warning else RetroColors.Surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.connectors_title), style = MaterialTheme.typography.titleLarge)
            val switchLabel = stringResource(R.string.connected_mode)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (connected) R.string.mode_connected else R.string.mode_offline), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = connected,
                    onCheckedChange = { on -> if (on) confirming = true else network.set(NetworkMode.OFFLINE) },
                    modifier = Modifier.semantics { contentDescription = switchLabel },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = RetroColors.Primary,
                        checkedThumbColor = RetroColors.Ink,
                        uncheckedTrackColor = RetroColors.Surface,
                        uncheckedThumbColor = RetroColors.Ink,
                        uncheckedBorderColor = RetroColors.Ink,
                    ),
                )
            }
            Text(stringResource(R.string.connected_mode_detail), style = MaterialTheme.typography.bodyMedium)

            Text(stringResource(R.string.discord_title), style = MaterialTheme.typography.titleMedium)
            if (configured) {
                Text(stringResource(R.string.discord_configured), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetroOutlinedButton(onClick = {
                        if (!connected) {
                            testResult = null
                            return@RetroOutlinedButton
                        }
                        testResult = "…"
                        onTest {
                            testResult = it
                            tick++
                        }
                    }) { Text(stringResource(R.string.discord_test)) }
                    RetroTextButton(onClick = {
                        onRemoved()
                        configured = false
                        testResult = null
                    }, label = stringResource(R.string.discord_remove))
                }
                if (!connected) Text(stringResource(R.string.discord_need_connected), style = MaterialTheme.typography.bodyMedium)
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                val found = remember(tick) { discord.channels }
                if (found.isNotEmpty()) {
                    Text(stringResource(R.string.discord_channels_title), style = MaterialTheme.typography.titleSmall)
                    found.forEach { channel ->
                        var checked by remember(tick, channel.id) { mutableStateOf(discord.isApproved(channel.id)) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox) { on ->
                                discord.setApproved(channel.id, on)
                                checked = on
                                onChanged()
                            },
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = RetroColors.Primary, checkmarkColor = RetroColors.Ink, uncheckedColor = RetroColors.Ink))
                            Text(channel.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.discord_guide), style = MaterialTheme.typography.bodyMedium)
                RetroTextField(
                    typed,
                    { typed = it.trim() },
                    label = { Text(stringResource(R.string.discord_token)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                RetroButton(
                    onClick = {
                        token.save(typed)
                        typed = ""
                        configured = true
                        onChanged()
                    },
                    enabled = DiscordTokenStore.looksValid(typed),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.connect_confirm_title)) },
            text = { Text(stringResource(R.string.connect_confirm_text)) },
            confirmButton = {
                RetroButton(onClick = {
                    confirming = false
                    network.set(NetworkMode.CONNECTED)
                }) { Text(stringResource(R.string.connect_yes)) }
            },
            dismissButton = { RetroTextButton(onClick = { confirming = false }, label = stringResource(R.string.cancel)) },
            containerColor = RetroColors.Surface,
        )
    }
}
