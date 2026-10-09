package app.yamy.chat

import android.Manifest
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.connect.NetworkMode
import app.yamy.vision.ContentResolverImageSource
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroOutlinedButton
import app.yamy.ui.components.RetroRule
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.components.RetroTextField
import app.yamy.ui.theme.RetroColors
import app.yamy.ui.theme.TechnicalTextStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modelPath: String,
    onOpenDrawer: () -> Unit,
    onOpenConnectors: () -> Unit,
) {
    val ui by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::attachImage)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri?.let(Uri::parse)
        cameraUri = null
        if (uri != null) {
            if (saved) viewModel.attachImage(uri) else viewModel.discardCapture(uri)
        }
    }
    val canAttach = ui.status == EngineStatus.READY
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val speaking by viewModel.speaking.collectAsState()
    val scribe by viewModel.scribeState.collectAsState()
    val network by viewModel.networkMode.collectAsState()
    val pendingMessage by viewModel.pendingMessage.collectAsState()
    val sending by viewModel.sending.collectAsState()
    var micDenied by rememberSaveable { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) viewModel.startListening()
    }
    fun startVoice() {
        keyboard?.hide()
        focusManager.clearFocus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    val canSend = ui.status == EngineStatus.READY && (input.isNotBlank() || ui.pendingImage != null)

    /** Envoie puis ferme le clavier, pour laisser toute la place à la réponse. */
    fun submit() {
        if (!canSend) return
        viewModel.send(input)
        input = ""
        keyboard?.hide()
        focusManager.clearFocus()
    }

    val rows = remember(ui.messages, ui.draft) {
        if (ui.draft.isBlank() || ui.messages.lastOrNull()?.text == ui.draft) ui.messages else ui.messages + ChatMessage(id = -1, role = Role.YAMY, text = ui.draft)
    }
    val run = ui.chainRun?.takeIf { it.summary == null || ui.messages.lastOrNull()?.text != it.summary }
    val thinking = ui.status == EngineStatus.GENERATING && ui.draft.isBlank() && run == null
    val chains by viewModel.chains.collectAsState()
    var showStarters by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    // Liste inversée (index 0 = bas) : la réponse qui s'allonge reste collée en bas de l'écran.
    // On ne suit le flux que si l'utilisateur est déjà en bas, pour ne pas l'empêcher de remonter lire.
    LaunchedEffect(rows.size, thinking, run?.steps?.size) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        val canAct = ui.status == EngineStatus.READY
        ChatTopBar(
            title = ui.session?.title ?: stringResource(R.string.app_name),
            network = network,
            onOpenDrawer = onOpenDrawer,
            onOpenConnectors = onOpenConnectors,
            onNewChat = { showStarters = true },
            actions = listOf(
                TopBarAction(stringResource(R.string.remember), enabled = canAct && ui.messages.isNotEmpty(), onClick = viewModel::rememberSession),
                TopBarAction(stringResource(R.string.clear_conversation), onClick = viewModel::clearConversation),
                TopBarAction(stringResource(R.string.delete_session), onClick = { confirmDelete = true }),
            ),
        )
        RetroRule()
        if (ui.status == EngineStatus.LOADING || ui.status == EngineStatus.GENERATING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = RetroColors.Secondary,
                trackColor = RetroColors.Surface,
            )
        }
        // Toucher la conversation ferme le clavier.
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                detectTapGestures(onTap = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                })
            },
        ) {
            when {
                ui.status == EngineStatus.MODEL_MISSING -> Notice(stringResource(R.string.model_missing, modelPath), technical = true)
                ui.status == EngineStatus.ERROR -> Notice(ui.error.orEmpty(), technical = true, isError = true)
                rows.isEmpty() -> Welcome()
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (run != null) item(key = AGENTS_KEY) { AgentsPanel(run.steps) }
                    if (thinking) item(key = THINKING_KEY) { ThinkingBubble() }
                    items(rows.asReversed(), key = { it.id.takeIf { id -> id != -1L } ?: Long.MIN_VALUE }) { MessageBubble(it) }
                }
            }
        }
        if (ui.status != EngineStatus.ERROR) {
            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = TechnicalTextStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        pendingMessage?.let {
            Box(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                DiscordConfirmCard(
                    it,
                    connected = network == NetworkMode.CONNECTED,
                    sending = sending,
                    generating = ui.status == EngineStatus.GENERATING,
                    onSend = { viewModel.confirmMessage(it) },
                    onCancel = viewModel::cancelMessage,
                )
            }
        }
        scribe?.let { ScribeBanner(it, onUndo = viewModel::undoRemember, onDismiss = viewModel::dismissScribe) }
        if (micDenied) {
            Text(stringResource(R.string.mic_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (speaking) {
            RetroTextButton(onClick = viewModel::stopSpeaking, label = stringResource(R.string.stop_speaking), modifier = Modifier.padding(start = 4.dp))
        }
        val voice = ui.voice
        if (voice != null) {
            ListeningPanel(voice, onFinish = viewModel::stopListening, onCancel = viewModel::cancelListening)
            return@Column
        }
        Composer(
            input = input,
            onInputChange = { input = it },
            hasImage = ui.pendingImage != null,
            enabled = canAttach,
            chains = chains,
            focusRequester = inputFocus,
            maxLines = if (imeVisible) 3 else 5,
            onCamera = {
                val file = File(ContentResolverImageSource.cameraDir(context).apply { mkdirs() }, "capture-${System.nanoTime()}.jpg")
                val uri = FileProvider.getUriForFile(context, ContentResolverImageSource.cameraAuthority(context), file)
                cameraUri = uri.toString()
                cameraLauncher.launch(uri)
            },
            onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onChain = { chain ->
                input = "/${chain.name} "
                inputFocus.requestFocus()
            },
            onMic = ::startVoice,
            onSend = ::submit,
            attachment = { ui.pendingImage?.let { PendingImageChip(it, viewModel::discardImage) } },
        )
        if (!imeVisible) {
            Text(
                stringResource(R.string.ai_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
        }
    }

    if (showStarters) {
        StarterSheet(
            onPick = { starter ->
                showStarters = false
                val title = if (starter.topic.isEmpty()) Session.DEFAULT_TITLE else starter.title
                viewModel.newSession(title, starter.topic)
            },
            onDismiss = { showStarters = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_session)) },
            text = { Text(stringResource(R.string.delete_session_confirm)) },
            confirmButton = {
                RetroButton(onClick = {
                    confirmDelete = false
                    ui.session?.let { viewModel.deleteSession(it.id) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { RetroTextButton(onClick = { confirmDelete = false }, label = stringResource(R.string.cancel)) },
            containerColor = RetroColors.Surface,
        )
    }
}

@Composable
private fun Welcome() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.displaySmall, color = RetroColors.Secondary)
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun Notice(text: String, technical: Boolean = false, isError: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = if (technical) TechnicalTextStyle else MaterialTheme.typography.headlineSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val THINKING_KEY = "thinking"
private const val AGENTS_KEY = "agents"

/** Affiché entre l'envoi et le premier mot de la réponse. */
@Composable
private fun ThinkingBubble() {
    val label = stringResource(R.string.thinking)
    Row(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        RetroCard {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val mine = message.role == Role.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        // La bulle s'ajuste au texte, sans dépasser 88 % de la largeur.
        Box(modifier = Modifier.fillMaxWidth(0.88f), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        RetroCard(
            containerColor = if (mine) RetroColors.Primary else RetroColors.Surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (message.hasImage) {
                    Text(stringResource(R.string.photo_label), style = MaterialTheme.typography.labelMedium)
                }
                if (message.viaVoice) {
                    Text(stringResource(R.string.voice_label), style = MaterialTheme.typography.labelMedium)
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        }
    }
}

@Composable
private fun PendingImageChip(image: PendingImage, onRemove: () -> Unit) {
    val thumbnail = remember(image) { BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)?.asImageBitmap() }
    val description = stringResource(R.string.pending_image_description)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        RetroTextButton(onClick = onRemove, label = stringResource(R.string.remove_image))
    }
}

/** Bandeau du Scribe : transparence sur ce que Yamy retient, avec Annuler. */
@Composable
private fun ScribeBanner(scribe: ScribeUi, onUndo: () -> Unit, onDismiss: () -> Unit) {
    RetroCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            val written = scribe.result?.written.orEmpty()
            Text(
                when {
                    scribe.working -> stringResource(R.string.scribe_working)
                    written.isEmpty() -> stringResource(R.string.scribe_nothing)
                    else -> stringResource(R.string.scribe_done, written.joinToString(", ") { it.name })
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!scribe.working) {
                Row {
                    if (written.isNotEmpty()) RetroTextButton(onClick = onUndo, label = stringResource(R.string.cancel))
                    RetroTextButton(onClick = onDismiss, label = stringResource(R.string.ok))
                }
            }
        }
    }
}
