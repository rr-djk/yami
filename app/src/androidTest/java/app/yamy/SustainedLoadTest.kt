package app.yamy

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yamy.engine.ChatInput
import java.io.File
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Charge soutenue : génère des réponses en continu pendant [DURATION_MS] et relève la température de la
 * batterie, le niveau de charge et le débit. Résultat dans `sustained-out.tsv` (mesures du pitch).
 */
@RunWith(AndroidJUnit4::class)
class SustainedLoadTest {
    @Test
    fun sustainedGeneration() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as YamyApp
        val out = File(app.getExternalFilesDir(null), "sustained-out.tsv")
        out.writeText("t_s\ttemp_C\tbatterie_%\tcharge\treponses\tmorceaux_par_s\n")

        fun battery(): Triple<Float, Int, Boolean> {
            val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10f
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            return Triple(temp, level, plugged)
        }

        app.engine.load()
        val persona = app.persona.systemPrompt()
        val prompts = listOf(
            "Raconte-moi une petite histoire sur un chat qui découvre la mer.",
            "Donne-moi trois idées pour me détendre après le travail, avec un peu de détails.",
            "Explique-moi comment organiser une semaine de révisions avant un examen.",
            "Parle-moi d'un souvenir d'enfance heureux que tu inventerais pour moi.",
        )
        val start = SystemClock.elapsedRealtime()
        var nextSample = 0L
        var replies = 0
        var chunks = 0
        var chunkWindowStart = start
        var i = 0
        while (SystemClock.elapsedRealtime() - start < DURATION_MS) {
            app.engine.startConversation(persona, emptyList())
            app.engine.send(ChatInput(prompts[i++ % prompts.size])).fold(0) { n, _ -> chunks++; n + 1 }
            replies++
            val elapsed = SystemClock.elapsedRealtime() - start
            if (elapsed >= nextSample) {
                val (temp, level, plugged) = battery()
                val rate = chunks * 1000.0 / (SystemClock.elapsedRealtime() - chunkWindowStart)
                out.appendText("${elapsed / 1000}\t$temp\t$level\t${if (plugged) "oui" else "non"}\t$replies\t${"%.1f".format(rate)}\n")
                nextSample += SAMPLE_EVERY_MS
                chunks = 0
                chunkWindowStart = SystemClock.elapsedRealtime()
            }
        }
        app.engine.close()
    }

    private companion object {
        const val DURATION_MS = 5 * 60 * 1000L
        const val SAMPLE_EVERY_MS = 30_000L
    }
}
