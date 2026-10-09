package app.yamy.privacy

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroRule
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.theme.RetroColors

@Composable
fun PrivacyScreen(audit: PrivacyAudit, onWipe: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val flags = (context as Activity).window.attributes.flags
    val checks = remember { audit.run(flags) }
    var confirming by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.displaySmall, color = RetroColors.Secondary)
            RetroTextButton(onClick = onClose, label = stringResource(R.string.close))
        }
        RetroRule()
        Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
        checks.forEach { CheckRow(it) }
        Text(stringResource(R.string.privacy_limits), style = MaterialTheme.typography.bodyMedium)
        RetroButton(onClick = { confirming = true }) { Text(stringResource(R.string.wipe_all)) }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.wipe_all)) },
            text = { Text(stringResource(R.string.wipe_confirm)) },
            confirmButton = {
                RetroButton(onClick = {
                    confirming = false
                    onWipe()
                }) { Text(stringResource(R.string.wipe_yes)) }
            },
            dismissButton = { RetroTextButton(onClick = { confirming = false }, label = stringResource(R.string.cancel)) },
            containerColor = RetroColors.Surface,
        )
    }
}

@Composable
private fun CheckRow(check: PrivacyCheck) {
    val status = if (check.passed) "vérifié" else "à corriger"
    RetroCard(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${check.label} : $status. ${check.detail}" },
        containerColor = if (check.passed) RetroColors.Surface else RetroColors.Warning,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text((if (check.passed) "✔ " else "✘ ") + check.label, style = MaterialTheme.typography.titleMedium)
            Text(check.detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
