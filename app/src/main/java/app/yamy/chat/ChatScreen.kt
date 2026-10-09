package app.yamy.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.ui.components.RetroButton
import app.yamy.ui.components.RetroCard
import app.yamy.ui.components.RetroOutlinedButton
import app.yamy.ui.components.RetroRule
import app.yamy.ui.components.RetroTextButton
import app.yamy.ui.components.RetroTextField
import app.yamy.ui.theme.RetroColors
import app.yamy.ui.theme.TechnicalTextStyle

@Composable
fun ChatScreen(viewModel: ChatViewModel, modelPath: String, onOpenSkills: () -> Unit) {
    val ui by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::attachImage)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) viewModel.attachImage(uri)
    }
    val canAttach = ui.status == EngineStatus.READY

    val rows = remember(ui.messages, ui.draft) {
        if (ui.draft.isBlank() || ui.messages.lastOrNull()?.text == ui.draft) ui.messages else ui.messages + ChatMessage(id = -1, role = Role.YAMY, text = ui.draft)
    }
    LaunchedEffect(rows.size, ui.draft.length) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = RetroColors.Secondary)
            Row {
                RetroTextButton(onClick = onOpenSkills, label = stringResource(R.string.skills), enabled = ui.status == EngineStatus.READY)
                RetroTextButton(onClick = viewModel::clearConversation, label = stringResource(R.string.clear_conversation))
            }
        }
        RetroRule()
        if (ui.status == EngineStatus.LOADING || ui.status == EngineStatus.GENERATING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = RetroColors.Secondary,
                trackColor = RetroColors.Surface,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.status == EngineStatus.MODEL_MISSING -> Notice(stringResource(R.string.model_missing, modelPath), technical = true)
                ui.status == EngineStatus.ERROR -> Notice(ui.error.orEmpty(), technical = true, isError = true)
                rows.isEmpty() -> Notice(stringResource(R.string.empty_chat))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rows, key = { it.id.takeIf { id -> id != -1L } ?: Long.MIN_VALUE }) { MessageBubble(it) }
                }
            }
        }
        if (ui.status != EngineStatus.ERROR) {
            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = TechnicalTextStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        ui.pendingImage?.let { PendingImageChip(it, viewModel::discardImage) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RetroOutlinedButton(
                onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = canAttach,
            ) { Text(stringResource(R.string.attach_gallery)) }
            RetroOutlinedButton(
                onClick = {
                    val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                },
                enabled = canAttach,
            ) { Text(stringResource(R.string.attach_camera)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RetroTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Message pour Yamy" },
                placeholder = { Text(stringResource(R.string.input_hint)) },
                maxLines = 4,
            )
            RetroButton(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                enabled = ui.status == EngineStatus.READY && (input.isNotBlank() || ui.pendingImage != null),
            ) { Text(stringResource(R.string.send)) }
        }
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

@Composable
private fun MessageBubble(message: ChatMessage) {
    val mine = message.role == Role.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        RetroCard(
            modifier = Modifier.fillMaxWidth(0.88f),
            containerColor = if (mine) RetroColors.Primary else RetroColors.Surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (message.hasImage) {
                    Text(stringResource(R.string.photo_label), style = MaterialTheme.typography.labelMedium)
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
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
