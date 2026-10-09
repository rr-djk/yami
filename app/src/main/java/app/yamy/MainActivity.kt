package app.yamy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yamy.chat.ChatScreen
import app.yamy.chat.ChatViewModel
import app.yamy.ui.theme.YamyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as YamyApp
        setContent {
            YamyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val chatViewModel = viewModel<ChatViewModel>(factory = chatFactory(app))
                    ChatScreen(chatViewModel, app.modelLocator.modelFile.path)
                }
            }
        }
    }

    private fun chatFactory(app: YamyApp) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(app.engine, app.conversations, app.persona, app.modelLocator, app.imageSource) as T
    }
}
