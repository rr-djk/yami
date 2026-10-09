package app.yamy

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yamy.engine.ChatInput
import app.yamy.voice.Wav
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mesure : Gemma 4 E2B écoute un message vocal (voix de synthèse hors ligne du téléphone) et répond.
 * Écrit `audio-probe.tsv` (durée audio, temps avant le premier mot, temps total, réponse).
 */
@RunWith(AndroidJUnit4::class)
class AudioProbeTest {
    private val phrases = listOf(
        "Salut Yamy, j'ai eu une journée vraiment longue au travail.",
        "Salut Yamy. Demain j'ai un examen de biologie très important. J'ai révisé toute la semaine, mais j'ai peur d'oublier les définitions. Tu peux m'aider à me calmer et à organiser ma soirée ?",
    )

    @Test
    fun probe() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as YamyApp
        val out = File(app.getExternalFilesDir(null), "audio-probe.tsv")
        out.writeText("audio_s\tmode\tpremier_morceau_ms\ttotal_ms\treponse\n")

        val ready = CountDownLatch(1)
        val tts = TextToSpeech(context) { ready.countDown() }
        ready.await(10, TimeUnit.SECONDS)
        tts.language = Locale.FRENCH

        app.engine.load()
        for ((index, phrase) in phrases.withIndex()) {
            val file = File(context.cacheDir, "probe-$index.wav")
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = done.countDown()

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = done.countDown()
            })
            tts.synthesizeToFile(phrase, null, file, "p$index")
            done.await(30, TimeUnit.SECONDS)
            val samples = Wav.decodeToMono(file.readBytes())
            file.delete()
            val wav = Wav.encode(samples)
            val seconds = samples.size / Wav.SAMPLE_RATE.toDouble()

            for (mode in listOf("repondre", "transcrire")) {
                val prompt = if (mode == "repondre") {
                    "Réponds à ce message vocal."
                } else {
                    "Transcris cet audio."
                }
                val system = if (mode == "repondre") {
                    app.persona.systemPrompt()
                } else {
                    "Tu es un outil de transcription. Écris uniquement, mot pour mot, ce qui est dit dans l'audio. Aucune réponse, aucun commentaire."
                }
                app.engine.startConversation(system, emptyList())
                val start = System.nanoTime()
                var first = -1L
                val reply = StringBuilder()
                app.engine.send(ChatInput(prompt, audio = wav)).collect { piece ->
                    if (first < 0) first = (System.nanoTime() - start) / 1_000_000
                    reply.append(piece)
                }
                val total = (System.nanoTime() - start) / 1_000_000
                out.appendText("${"%.1f".format(seconds)}\t$mode\t$first\t$total\t${reply.toString().replace('\n', ' ')}\n")
            }
        }
        tts.stop()
        app.engine.close()
    }
}
