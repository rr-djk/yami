package app.yamy.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.yamy.R
import app.yamy.agents.Chain
import app.yamy.ui.theme.RetroColors

/**
 * Barre de saisie unique, à la manière de Gemini / Claude : ＋ (photo, galerie, agents) · texte ·
 * 🎙️ quand le champ est vide, ➤ dès qu'il y a quelque chose à envoyer.
 */
@Composable
fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    hasImage: Boolean,
    enabled: Boolean,
    chains: List<Chain>,
    focusRequester: FocusRequester,
    maxLines: Int,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onChain: (Chain) -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    attachment: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val canSend = enabled && (input.isNotBlank() || hasImage)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = RetroColors.Surface,
        border = BorderStroke(2.dp, RetroColors.Ink),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column {
            attachment()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Box {
                    IconText("＋", stringResource(R.string.add_menu), enabled = enabled) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = RetroColors.Surface) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.take_photo)) }, onClick = { menu = false; onCamera() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.pick_gallery)) }, onClick = { menu = false; onGallery() })
                        if (chains.isNotEmpty()) {
                            HorizontalDivider(color = RetroColors.Ink)
                            Text(stringResource(R.string.agents), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            chains.forEach { chain ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(chain.agents.joinToString(" ") { it.emoji } + "  /" + chain.name, style = MaterialTheme.typography.titleSmall)
                                            Text(chain.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = { menu = false; onChain(chain) },
                                )
                            }
                        }
                    }
                }
                val hint = stringResource(R.string.input_hint)
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text(hint) },
                    maxLines = maxLines,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = RetroColors.Ink,
                        unfocusedTextColor = RetroColors.Ink,
                        cursorColor = RetroColors.Ink,
                        focusedPlaceholderColor = RetroColors.Ink.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = RetroColors.Ink.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).semantics { contentDescription = hint },
                )
                if (input.isBlank() && !hasImage) {
                    IconText("🎙️", stringResource(R.string.mic_description), enabled = enabled, onClick = onMic)
                } else {
                    SendButton(enabled = canSend, onClick = onSend)
                }
            }
        }
    }
}

/** ➤ sur un rond bleu (texte encre : contraste 4,8:1). */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = if (enabled) RetroColors.Primary else RetroColors.Surface,
        border = BorderStroke(2.dp, RetroColors.Ink),
        modifier = Modifier.padding(4.dp),
    ) {
        val label = stringResource(R.string.send)
        Text("➤", style = MaterialTheme.typography.titleLarge, color = RetroColors.Ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).semantics { contentDescription = label })
    }
}
