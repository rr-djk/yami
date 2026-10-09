package app.yamy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yamy.ui.theme.RetroColors

private val RetroShape = RoundedCornerShape(12.dp)
private val InkStroke = 2.dp
private val FocusStroke = 4.dp
private val ShadowOffset = 4.dp

/** Bordure d'encre ; épaissie et violette quand l'élément a le focus clavier (visible sur toute la palette). */
@Composable
private fun borderFor(focused: Boolean) =
    BorderStroke(if (focused) FocusStroke else InkStroke, if (focused) RetroColors.Secondary else RetroColors.Ink)

/** Carte façon affiche typographique : fond plein, bordure d'encre et ombre dure décalée. */
@Composable
fun RetroCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.padding(end = ShadowOffset, bottom = ShadowOffset)) {
        Box(Modifier.matchParentSize().offset(ShadowOffset, ShadowOffset).background(RetroColors.Ink, RetroShape))
        Surface(
            shape = RetroShape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(InkStroke, RetroColors.Ink),
            content = content,
        )
    }
}

@Composable
fun RetroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RetroShape,
        border = borderFor(focused),
        interactionSource = source,
        colors = ButtonDefaults.buttonColors(
            containerColor = RetroColors.Primary,
            contentColor = RetroColors.Ink,
            disabledContainerColor = RetroColors.Surface,
            disabledContentColor = RetroColors.Ink.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
fun RetroOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RetroShape,
        border = borderFor(focused),
        interactionSource = source,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = RetroColors.Surface,
            contentColor = RetroColors.Ink,
            disabledContentColor = RetroColors.Ink.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) { content() }
}

/** Bouton texte souligné, en encre (le bleu en texte sur blanc n'atteint pas AA). */
@Composable
fun RetroTextButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = RetroColors.Ink,
            disabledContentColor = RetroColors.Ink.copy(alpha = 0.6f),
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, textDecoration = TextDecoration.Underline)
    }
}

@Composable
fun RetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RetroShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = RetroColors.Surface,
            unfocusedContainerColor = RetroColors.Surface,
            disabledContainerColor = RetroColors.Surface,
            focusedTextColor = RetroColors.Ink,
            unfocusedTextColor = RetroColors.Ink,
            focusedIndicatorColor = RetroColors.Secondary,
            unfocusedIndicatorColor = RetroColors.Ink,
            focusedLabelColor = RetroColors.Ink,
            unfocusedLabelColor = RetroColors.Ink,
            focusedPlaceholderColor = RetroColors.Ink.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = RetroColors.Ink.copy(alpha = 0.7f),
            cursorColor = RetroColors.Ink,
        ),
    )
}

/** Filet d'encre horizontal, séparateur façon imprimerie. */
@Composable
fun RetroRule(modifier: Modifier = Modifier, thickness: Dp = 3.dp) {
    Box(modifier.fillMaxWidth().heightIn(min = thickness, max = thickness).background(RetroColors.Ink))
}
