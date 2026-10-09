package app.yamy.chat

import android.Manifest
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
fun ChatScreen(viewModel: ChatViewModel, modelPath: String, onBack: () -> Unit) {
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
    var agentsMenu by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    // Liste inversée (index 0 = bas) : la réponse qui s'allonge reste collée en bas de l'écran.
    // On ne suit le flux que si l'utilisateur est déjà en bas, pour ne pas l'empêcher de remonter lire.
    LaunchedEffect(rows.size, thinking, run?.steps?.size) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RetroTextButton(onClick = onBack, label = stringResource(R.string.back_to_sessions))
            Text(
                ui.session?.title ?: stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = RetroColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            RetroTextButton(onClick = viewModel::rememberSession, label = stringResource(R.string.remember), enabled = ui.status == EngineStatus.READY && ui.messages.isNotEmpty())
            RetroTextButton(onClick = viewModel::clearConversation, label = stringResource(R.string.clear_conversation))
        }
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
        ui.pendingImage?.let { PendingImageChip(it, viewModel::discardImage) }
        // Clavier ouvert : on masque les boutons photo et la mention pour garder la conversation visible.
        if (!imeVisible) Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RetroOutlinedButton(
                onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = canAttach,
            ) { Text(stringResource(R.string.attach_gallery)) }
            RetroOutlinedButton(
                onClick = {
                    val file = File(ContentResolverImageSource.cameraDir(context).apply { mkdirs() }, "capture-${System.nanoTime()}.jpg")
                    val uri = FileProvider.getUriForFile(context, ContentResolverImageSource.cameraAuthority(context), file)
                    cameraUri = uri.toString()
                    cameraLauncher.launch(uri)
                },
                enabled = canAttach,
            ) { Text(stringResource(R.string.attach_camera)) }
            if (chains.isNotEmpty()) {
                Box {
                    RetroOutlinedButton(onClick = { agentsMenu = true }, enabled = canAttach) { Text(stringResource(R.string.agents)) }
                    DropdownMenu(expanded = agentsMenu, onDismissRequest = { agentsMenu = false }, containerColor = RetroColors.Surface) {
                        chains.forEach { chain ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(chain.agents.joinToString(" ") { it.emoji } + "  /" + chain.name, style = MaterialTheme.typography.titleSmall)
                                        Text(chain.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    agentsMenu = false
                                    input = "/${chain.name} "
                                    inputFocus.requestFocus()
                                },
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RetroTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).focusRequester(inputFocus).semantics { contentDescription = "Message pour Yamy" },
                placeholder = { Text(stringResource(R.string.input_hint)) },
                maxLines = if (imeVisible) 3 else 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            val micLabel = stringResource(R.string.mic_description)
            RetroOutlinedButton(
                onClick = ::startVoice,
                enabled = ui.status == EngineStatus.READY,
                modifier = Modifier.semantics { contentDescription = micLabel },
            ) { Text(stringResource(R.string.mic)) }
            RetroButton(onClick = ::submit, enabled = canSend) { Text(stringResource(R.string.send)) }
        }
        if (imeVisible) {
            RetroTextButton(
                onClick = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
                label = stringResource(R.string.hide_keyboard),
                modifier = Modifier.padding(start = 4.dp),
            )
        } else {
            Text(
                stringResource(R.string.ai_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
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
