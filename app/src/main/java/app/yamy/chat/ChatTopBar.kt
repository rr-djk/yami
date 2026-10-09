package app.yamy.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.connect.NetworkMode
import app.yamy.ui.theme.RetroColors

/** Action du menu ⋮ de la discussion. */
data class TopBarAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * Barre du haut, à la manière de Gemini / Claude : ☰ · titre · mode réseau · ＋ · ⋮.
 * Chaque bouton fait au moins 48 dp et porte une description pour TalkBack.
 */
@Composable
fun ChatTopBar(
    title: String,
    network: NetworkMode,
    onOpenDrawer: () -> Unit,
    onOpenConnectors: () -> Unit,
    onNewChat: () -> Unit,
    actions: List<TopBarAction>,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconText("☰", stringResource(R.string.open_menu), onClick = onOpenDrawer)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = RetroColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        val connected = network == NetworkMode.CONNECTED
        IconText(if (connected) "🌐" else "🔒", stringResource(if (connected) R.string.mode_connected else R.string.mode_offline), onClick = onOpenConnectors)
        IconText("＋", stringResource(R.string.new_session), onClick = onNewChat)
        Box {
            IconText("⋮", stringResource(R.string.more_actions)) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = RetroColors.Surface) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label, style = MaterialTheme.typography.bodyLarge) },
                        enabled = action.enabled,
                        onClick = {
                            menu = false
                            action.onClick()
                        },
                    )
                }
            }
        }
    }
}

/** Bouton-icône en texte (emoji ou glyphe), cible tactile de 48 dp. */
@Composable
fun IconText(glyph: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = description },
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge, color = RetroColors.Ink)
    }
}
