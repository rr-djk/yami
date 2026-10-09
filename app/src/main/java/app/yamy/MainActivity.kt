package app.yamy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yamy.chat.ChatScreen
import app.yamy.chat.ChatViewModel
import app.yamy.skills.SkillsScreen
import app.yamy.skills.SkillsViewModel
import app.yamy.ui.theme.YamyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as YamyApp
        setContent {
            YamyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    var showSkills by rememberSaveable { mutableStateOf(false) }
                    val chat = viewModel<ChatViewModel>(factory = factory { ChatViewModel(app.engine, app.conversations, app.persona, app.modelLocator, app.imageSource, app.skills) })
                    if (showSkills) {
                        val skills = viewModel<SkillsViewModel>(factory = factory { SkillsViewModel(app.skills, app.skillCreator, app.engine) })
                        BackHandler { showSkills = false }
                        SkillsScreen(skills) { showSkills = false }
                    } else {
                        ChatScreen(chat, app.modelLocator.modelFile.path) { showSkills = true }
                    }
                }
            }
        }
    }

    private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
}
