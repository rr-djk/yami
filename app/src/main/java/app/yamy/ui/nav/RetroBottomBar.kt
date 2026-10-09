package app.yamy.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.yamy.R
import app.yamy.ui.components.RetroRule
import app.yamy.ui.theme.RetroColors

enum class Tab(val emoji: String, val label: Int) {
    SESSIONS("💬", R.string.tab_sessions),
    TOOLS("🛠️", R.string.tab_tools),
    MEMORY("🧠", R.string.tab_memory),
    PRIVACY("🔒", R.string.tab_privacy),
}

/** Barre de navigation façon affiche : filet d'encre en haut, onglet actif sur fond bleu. */
@Composable
fun RetroBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column {
        RetroRule()
        NavigationBar(containerColor = RetroColors.Surface) {
            Tab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = { Text(tab.emoji, style = MaterialTheme.typography.titleMedium) },
                    label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = RetroColors.Primary,
                        selectedTextColor = RetroColors.Ink,
                        unselectedTextColor = RetroColors.Ink,
                    ),
                )
            }
        }
    }
}
