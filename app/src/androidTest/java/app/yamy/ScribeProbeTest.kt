package app.yamy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yamy.chat.ChatMessage
import app.yamy.chat.Role
import app.yamy.memory.FileMemoryRepository
import app.yamy.memory.MemoryFormat
import app.yamy.memory.Scribe
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Le Scribe sur le vrai modèle, dans un dossier de test séparé (la vraie mémoire n'est pas touchée). */
@RunWith(AndroidJUnit4::class)
class ScribeProbeTest {
    @Test
    fun probe() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as YamyApp
        val dir = File(app.cacheDir, "memoire-test").apply { deleteRecursively() }
        val memory = FileMemoryRepository(dir, app.secretBox)
        app.engine.load()
        val messages = listOf(
            ChatMessage(role = Role.USER, text = "J'ai un examen de biologie jeudi et je stresse beaucoup."),
            ChatMessage(role = Role.YAMY, text = "Je comprends. Qu'est-ce qui t'inquiète le plus ?"),
            ChatMessage(role = Role.USER, text = "Les définitions. Ma sœur Léa m'aide à réviser le soir, elle est super."),
        )
        val start = System.nanoTime()
        val result = Scribe(app.engine, memory).remember(messages)
        val ms = (System.nanoTime() - start) / 1_000_000
        File(app.getExternalFilesDir(null), "scribe-probe.txt").writeText(
            "Durée : $ms ms\nNotes : ${result.written.size}\n--- brut ---\n${result.raw}\n--- notes ---\n" + result.written.joinToString("\n\n") { MemoryFormat.format(it) },
        )
        dir.deleteRecursively()
        app.engine.close()
    }
}
