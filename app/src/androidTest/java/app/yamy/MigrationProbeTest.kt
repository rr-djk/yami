package app.yamy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Ouvre la vraie base de l'appareil (déclenche la migration 2 → 3) sans rien effacer. */
@RunWith(AndroidJUnit4::class)
class MigrationProbeTest {
    @Test
    fun openRealDatabase() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as YamyApp
        val sessions = app.sessions.observe().first()
        val first = app.sessions.mostRecentOrCreate()
        val messages = app.conversations.recent(first.id, 500)
        println("YAMY_MIGRATION sessions=${sessions.size} first=${first.title} messages=${messages.size}")
        assertTrue(sessions.isNotEmpty() || messages.isEmpty())
    }
}
