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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.connect.NetworkMode
import app.yamy.connect.NetworkModeStore
import app.yamy.mcp.GmailCredentialStore
import app.yamy.mcp.gmail.GmailCredentials
import app.yamy.mcp.gmail.GmailMcpServer
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.components.RetroTextField
import app.yamy.ui.theme.RetroColors

/** Switch Hors ligne / Connecté et configuration du connecteur MCP Gmail. */
@Composable
fun ConnectorsSection(network: NetworkModeStore, credentials: GmailCredentialStore) {
    val mode by network.mode.collectAsState()
    var confirming by rememberSaveable { mutableStateOf(false) }
    var configured by remember { mutableStateOf(credentials.load()?.user) }
    var address by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val connected = mode == NetworkMode.CONNECTED

    RetroCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (connected) RetroColors.Warning else RetroColors.Surface,
    ) {
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

            val user = configured
            if (user != null) {
                Text(stringResource(R.string.gmail_configured, user), style = MaterialTheme.typography.bodyLarge)
                RetroTextButton(onClick = {
                    credentials.clear()
                    configured = null
                }, label = stringResource(R.string.gmail_remove))
            } else {
                RetroTextField(address, { address = it.trim() }, label = { Text(stringResource(R.string.gmail_address)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                RetroTextField(
                    password,
                    { password = it },
                    label = { Text(stringResource(R.string.gmail_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                RetroButton(
                    onClick = {
                        credentials.save(GmailCredentials(address, password))
                        configured = address
                        password = ""
                    },
                    enabled = GmailMcpServer.isValidAddress(address) && password.filterNot { it.isWhitespace() }.length >= 16,
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
