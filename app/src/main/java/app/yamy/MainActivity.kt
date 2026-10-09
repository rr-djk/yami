package app.yamy

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yamy.chat.ChatScreen
import app.yamy.chat.ChatViewModel
import app.yamy.chat.SessionsScreen
import app.yamy.memory.MemoryScreen
import app.yamy.memory.MemoryViewModel
import app.yamy.ui.nav.RetroBottomBar
import app.yamy.ui.nav.Tab
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import app.yamy.privacy.LockScreen
import app.yamy.privacy.PrivacyScreen
import app.yamy.privacy.ConnectorsSection
import app.yamy.skills.SkillsScreen
import app.yamy.skills.SkillsViewModel
import app.yamy.ui.theme.YamyTheme

class MainActivity : FragmentActivity() {
    private val app get() = application as YamyApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pas de capture d'écran, et aperçu masqué dans les applications récentes.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val light = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = light, navigationBarStyle = light)
        setContent {
            YamyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val locked by app.appLock.locked.collectAsState()
                    LaunchedEffect(locked) { if (locked) app.appLock.authenticate(this@MainActivity) }
                    if (locked) {
                        LockScreen { app.appLock.authenticate(this@MainActivity) }
                    } else {
                        Content()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            app.appLock.onAppLeft()
            // Rien ne doit continuer à écouter ou à lire une réponse à voix haute derrière l'écran de verrouillage.
            app.voiceRecorder.stop()
            app.speaker.stop()
        }
    }

    override fun onStart() {
        super.onStart()
        app.appLock.onAppReturned()
    }

    @androidx.compose.runtime.Composable
    private fun Content() {
        // Le chat s'ouvre en plein écran (place pour le clavier) ; les autres écrans ont la barre de navigation.
        var inChat by rememberSaveable { mutableStateOf(true) }
        var tab by rememberSaveable { mutableStateOf(Tab.SESSIONS) }
        val chat = viewModel<ChatViewModel>(
            factory = factory {
                ChatViewModel(app.engine, app.conversations, app.persona, app.modelLocator, app.imageSource, app.skills, app.dataWiper, app.sessions, app.voiceRecorder, app.speaker, app.chainRunner, app.memory, app.scribe, app.networkMode, app.discord)
            },
        )
        if (inChat) {
            BackHandler { inChat = false }
            ChatScreen(
                chat,
                app.modelLocator.modelFile.path,
                onBack = { inChat = false },
                onOpenConnectors = {
                    tab = Tab.PRIVACY
                    inChat = false
                },
            )
            return
        }
        BackHandler(enabled = tab != Tab.SESSIONS) { tab = Tab.SESSIONS }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = { RetroBottomBar(tab) { tab = it } },
        ) { inner ->
            val content = Modifier.padding(inner).consumeWindowInsets(inner)
            Box(modifier = content) {
                when (tab) {
                    Tab.SESSIONS -> SessionsScreen(chat, onOpenChat = { inChat = true })
                    Tab.TOOLS -> {
                        val skills = viewModel<SkillsViewModel>(factory = factory { SkillsViewModel(app.skills, app.skillCreator, app.engine) })
                        LaunchedEffect(Unit) { skills.refresh() }
                        SkillsScreen(skills) { tab = Tab.SESSIONS }
                    }
                    Tab.MEMORY -> {
                        val memoryVm = viewModel<MemoryViewModel>(factory = factory { MemoryViewModel(app.memory, app.engine) })
                        LaunchedEffect(Unit) { memoryVm.refresh() }
                        MemoryScreen(memoryVm)
                    }
                    Tab.PRIVACY -> PrivacyScreen(
                        audit = app.privacyAudit,
                        connectors = { ConnectorsSection(app.networkMode, app.discordToken, onChanged = chat::invalidateEngine, onTest = chat::refreshDiscordChannels) },
                        onWipe = { chat.wipeAll { tab = Tab.SESSIONS } },
                        onClose = { tab = Tab.SESSIONS },
                    )
                }
            }
        }
    }

    private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
}
