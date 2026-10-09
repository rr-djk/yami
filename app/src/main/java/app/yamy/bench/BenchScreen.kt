package app.yamy.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yamy.engine.ComputeBackend

private const val BENCH_PROMPT = "Salut Yamy, j'ai eu une journée longue. Peux-tu me remonter le moral en quelques phrases ?"

/** Écran temporaire de mesures (étape 3), remplacé par le chat à l'étape 4. */
@Composable
fun BenchScreen(viewModel: BenchViewModel) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Yamy — mesures du modèle", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (viewModel.modelPresent) "Modèle trouvé." else "Modèle absent : adb push vers ${viewModel.modelPath}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.run(ComputeBackend.CPU, BENCH_PROMPT) }, enabled = viewModel.modelPresent && state !is BenchState.Running) { Text("Test CPU") }
            Button(onClick = { viewModel.run(ComputeBackend.GPU, BENCH_PROMPT) }, enabled = viewModel.modelPresent && state !is BenchState.Running) { Text("Test GPU") }
        }
        when (val s = state) {
            BenchState.Idle -> Unit
            is BenchState.Running -> {
                CircularProgressIndicator()
                Text(s.label)
            }
            is BenchState.Done -> Text(s.result.summary(), style = MaterialTheme.typography.bodyMedium)
            is BenchState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
        }
    }
}
