package app.yamy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yamy.engine.ChatInput
import app.yamy.safety.TurnPlan
import app.yamy.safety.TurnPlanner
import java.io.File
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Banc de test du modèle : rejoue les messages de `bench_messages.tsv` avec le vrai moteur et la vraie
 * couche de sécurité, puis écrit `bench-out.tsv` dans le dossier de l'app (à relire et noter à la main).
 */
@RunWith(AndroidJUnit4::class)
class BenchTest {
    @Test
    fun runBench() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as YamyApp
        val messages = instrumentation.context.assets.open("bench_messages.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() }.map { it.split('\t') }

        app.engine.load()
        val persona = app.persona.systemPrompt()
        val planner = TurnPlanner()
        val out = File(app.getExternalFilesDir(null), "bench-out.tsv")
        out.writeText("id\tfamille\tchemin\tms\tmessage\treponse\n")

        for ((id, family, text) in messages) {
            val start = System.nanoTime()
            val (path, reply) = when (val plan = planner.plan(text)) {
                is TurnPlan.Crisis -> "code" to plan.reply
                is TurnPlan.Model -> {
                    app.engine.startConversation(persona, emptyList())
                    val answer = app.engine.send(ChatInput(plan.textForModel)).fold(StringBuilder()) { acc, piece -> acc.append(piece) }
                    "modèle(${plan.distress})" to answer.toString()
                }
            }
            val ms = (System.nanoTime() - start) / 1_000_000
            out.appendText("$id\t$family\t$path\t$ms\t$text\t${reply.replace('\n', ' ')}\n")
        }
        app.engine.close()
    }
}
