package app.yamy.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.theme.RetroColors

/** Panneau d'écoute : onde du micro, durée, et les mots transcrits qui apparaissent pendant qu'on parle. */
@Composable
fun ListeningPanel(voice: VoiceUi, onFinish: () -> Unit, onCancel: () -> Unit) {
    RetroCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.listening), style = MaterialTheme.typography.headlineSmall, color = RetroColors.Secondary, modifier = Modifier.weight(1f))
                Text("%d:%02d".format(voice.seconds / 60, voice.seconds % 60), style = MaterialTheme.typography.titleMedium)
            }
            WaveBars(voice.levels)
            Text(
                voice.transcript.ifBlank { stringResource(R.string.listening_hint) },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroButton(onClick = onFinish) { Text(stringResource(R.string.finish_listening)) }
                RetroTextButton(onClick = onCancel, label = stringResource(R.string.cancel))
            }
        }
    }
}

/** Barres façon vumètre d'atelier : bleu plein, filet d'encre. */
@Composable
private fun WaveBars(levels: List<Float>, bars: Int = 24) {
    val padded = List(bars - levels.size.coerceAtMost(bars)) { 0f } + levels.takeLast(bars)
    Canvas(modifier = Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = "Niveau du micro" }) {
        val slot = size.width / bars
        val barWidth = slot * 0.6f
        padded.forEachIndexed { i, level ->
            val h = (0.08f + level * 0.92f) * size.height
            val topLeft = Offset(i * slot + (slot - barWidth) / 2, (size.height - h) / 2)
            drawRoundRect(RetroColors.Ink, topLeft, Size(barWidth, h), CornerRadius(4f, 4f))
            drawRoundRect(
                RetroColors.Primary,
                topLeft + Offset(2f, 2f),
                Size((barWidth - 4f).coerceAtLeast(0f), (h - 4f).coerceAtLeast(0f)),
                CornerRadius(3f, 3f),
            )
        }
    }
}
