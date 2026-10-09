package app.yamy.bench

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.yamy.engine.ChatEngine
import app.yamy.engine.ComputeBackend
import app.yamy.engine.LiteRtChatEngine
import app.yamy.engine.ModelLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

sealed interface BenchState {
    data object Idle : BenchState
    data class Running(val label: String) : BenchState
    data class Done(val result: BenchResult) : BenchState
    data class Failed(val message: String) : BenchState
}

/** Écran de mesures de l'étape 3 : charge le modèle, génère, chronomètre. */
class BenchViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = ModelLocator(app)
    private val probe = DeviceProbe(app)
    private val engine: ChatEngine = LiteRtChatEngine(app, locator)

    private val _state = MutableStateFlow<BenchState>(BenchState.Idle)
    val state: StateFlow<BenchState> = _state.asStateFlow()

    val modelPath: String get() = locator.modelFile.path
    val modelPresent: Boolean get() = locator.isPresent()

    fun run(backend: ComputeBackend, prompt: String) {
        viewModelScope.launch {
            try {
                _state.value = BenchState.Running("Chargement du modèle (${backend.name})…")
                val loadMs = measureTimeMillis { engine.load(backend) }

                engine.startConversation(systemPrompt = "Tu es Yamy, un ami bienveillant. Réponds en français, brièvement.")
                _state.value = BenchState.Running("Génération…")
                val tempBefore = probe.batteryTempC()

                val reply = StringBuilder()
                var chunks = 0
                var firstChunkMs = -1L
                val start = System.nanoTime()
                engine.send(prompt).collect { piece ->
                    if (firstChunkMs < 0) firstChunkMs = (System.nanoTime() - start) / 1_000_000
                    chunks++
                    reply.append(piece)
                }
                val totalMs = (System.nanoTime() - start) / 1_000_000

                _state.value = BenchState.Done(
                    BenchResult(
                        backend = backend.name,
                        loadMs = loadMs,
                        firstChunkMs = firstChunkMs.coerceAtLeast(0),
                        totalMs = totalMs,
                        chunks = chunks,
                        chars = reply.length,
                        batteryTempBeforeC = tempBefore,
                        batteryTempAfterC = probe.batteryTempC(),
                        nativeHeapMb = probe.nativeHeapMb(),
                        reply = reply.toString(),
                    ),
                )
            } catch (e: Throwable) {
                _state.value = BenchState.Failed("${e::class.simpleName}: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        engine.close()
    }
}
