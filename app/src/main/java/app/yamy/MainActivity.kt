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
import app.yamy.privacy.LockScreen
import app.yamy.privacy.PrivacyScreen
import app.yamy.skills.SkillsScreen
import app.yamy.skills.SkillsViewModel
import app.yamy.ui.theme.YamyTheme

private enum class Screen { CHAT, SKILLS, PRIVACY }

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
        if (!isChangingConfigurations) app.appLock.onAppLeft()
    }

    override fun onStart() {
        super.onStart()
        app.appLock.onAppReturned()
    }

    @androidx.compose.runtime.Composable
    private fun Content() {
        var screen by rememberSaveable { mutableStateOf(Screen.CHAT) }
        val chat = viewModel<ChatViewModel>(
            factory = factory { ChatViewModel(app.engine, app.conversations, app.persona, app.modelLocator, app.imageSource, app.skills, app.dataWiper) },
        )
        when (screen) {
            Screen.CHAT -> ChatScreen(
                chat,
                app.modelLocator.modelFile.path,
                onOpenSkills = { screen = Screen.SKILLS },
                onOpenPrivacy = { screen = Screen.PRIVACY },
            )
            Screen.SKILLS -> {
                val skills = viewModel<SkillsViewModel>(factory = factory { SkillsViewModel(app.skills, app.skillCreator, app.engine) })
                LaunchedEffect(Unit) { skills.refresh() }
                BackHandler { screen = Screen.CHAT }
                SkillsScreen(skills) { screen = Screen.CHAT }
            }
            Screen.PRIVACY -> {
                BackHandler { screen = Screen.CHAT }
                PrivacyScreen(
                    audit = app.privacyAudit,
                    onWipe = { chat.wipeAll { screen = Screen.CHAT } },
                    onClose = { screen = Screen.CHAT },
                )
            }
        }
    }

    private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
}
