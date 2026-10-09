package app.yamy.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.chat.Session
import app.yamy.connect.NetworkMode
import app.yamy.ui.components.RetroRule
import app.yamy.ui.theme.RetroColors

/** Destinations hors du chat, ouvertes depuis le tiroir. */
enum class Destination { TOOLS, MEMORY, PRIVACY }

/**
 * Tiroir ☰, à la manière de Gemini / Claude : nouvelle discussion, discussions récentes, puis
 * outils, mémoire et vie privée ; le mode réseau reste visible en bas.
 */
@Composable
fun YamyDrawer(
    sessions: List<Session>,
    currentId: Long?,
    network: NetworkMode,
    onNewChat: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpen: (Destination) -> Unit,
) {
    val colors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = RetroColors.Primary,
        unselectedContainerColor = RetroColors.Surface,
        selectedTextColor = RetroColors.Ink,
        unselectedTextColor = RetroColors.Ink,
    )
    ModalDrawerSheet(drawerContainerColor = RetroColors.Surface) {
        Column(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = RetroColors.Secondary, modifier = Modifier.padding(16.dp))
            NavigationDrawerItem(label = { Text(stringResource(R.string.new_session)) }, selected = false, onClick = onNewChat, colors = colors)
            RetroRule(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.sessions_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions, key = { it.id }) { session ->
                    NavigationDrawerItem(
                        label = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = session.id == currentId,
                        onClick = { onOpenSession(session.id) },
                        colors = colors,
                    )
                }
            }
            RetroRule(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(label = { Text(stringResource(R.string.drawer_tools)) }, selected = false, onClick = { onOpen(Destination.TOOLS) }, colors = colors)
            NavigationDrawerItem(label = { Text(stringResource(R.string.drawer_memory)) }, selected = false, onClick = { onOpen(Destination.MEMORY) }, colors = colors)
            NavigationDrawerItem(label = { Text(stringResource(R.string.drawer_privacy)) }, selected = false, onClick = { onOpen(Destination.PRIVACY) }, colors = colors)
            Text(
                stringResource(if (network == NetworkMode.CONNECTED) R.string.mode_connected else R.string.mode_offline),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
