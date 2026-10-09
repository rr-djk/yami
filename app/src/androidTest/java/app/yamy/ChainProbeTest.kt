package app.yamy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yamy.agents.ChainCatalog
import app.yamy.agents.ChainFormat
import app.yamy.agents.ChainRunner
import java.io.File
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Fait tourner les chaînes livrées sur le vrai modèle ; écrit `chain-probe.txt` (durées et sorties). */
@RunWith(AndroidJUnit4::class)
class ChainProbeTest {
    @Test
    fun probe() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as YamyApp
        ChainCatalog.installDefaults(app.assets, app.skills)
        app.engine.load()
        val out = File(app.getExternalFilesDir(null), "chain-probe.txt")
        out.writeText("")
        val requests = mapOf(
            "le-conseil" to "Dois-je quitter mon travail pour reprendre mes études ?",
            "revision" to "La photosynthèse transforme la lumière en énergie chimique dans les chloroplastes. Elle consomme du CO2 et de l'eau et produit du glucose et de l'oxygène.",
        )
        for ((name, request) in requests) {
            val chain = ChainFormat.parse(app.skills.get(name)!!)!!
            val start = System.nanoTime()
            val steps = ChainRunner(app.engine).run(chain, request).last()
            val ms = (System.nanoTime() - start) / 1_000_000
            out.appendText("=== $name (${ms} ms)\n${ChainRunner.summary(steps)}\n\n")
        }
        app.engine.close()
    }
}
