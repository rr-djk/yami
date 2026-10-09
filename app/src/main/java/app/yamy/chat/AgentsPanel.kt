package app.yamy.chat

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yamy.agents.AgentStep
import app.yamy.agents.StepStatus
import app.yamy.ui.components.RetroCard
import app.yamy.ui.theme.RetroColors

/** Les agents au travail : chaque carte s'allume quand son agent réfléchit, puis garde sa réponse. */
@Composable
fun AgentsPanel(steps: List<AgentStep>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { step ->
            RetroCard(
                modifier = Modifier.fillMaxWidth(0.92f),
                containerColor = if (step.status == StepStatus.WORKING) RetroColors.Primary else RetroColors.Surface,
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${step.agent.emoji} ${step.agent.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            when (step.status) {
                                StepStatus.WAITING -> "attend son tour"
                                StepStatus.WORKING -> "réfléchit…"
                                StepStatus.DONE -> "✓"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (step.output.isNotBlank()) {
                        Text(
                            step.output,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
    }
}
