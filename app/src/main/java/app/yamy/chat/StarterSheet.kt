package app.yamy.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.ui.components.RetroCard
import app.yamy.ui.theme.RetroColors

/** Feuille « Nouvelle discussion » : une amorce de sujet par carte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterSheet(onPick: (Starter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RetroColors.Surface) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.choose_starter), style = MaterialTheme.typography.headlineSmall, color = RetroColors.Secondary)
            Starters.ALL.forEach { starter ->
                RetroCard(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(starter) }) {
                    Text("${starter.emoji}  ${starter.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
