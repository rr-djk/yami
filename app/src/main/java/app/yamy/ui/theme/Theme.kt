package app.yamy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Thème rétro : papier blanc, encre sombre, bleu et violet à pleine saturation.
 * Volontairement clair uniquement et sans couleurs dynamiques (choix de style assumé).
 */
private val RetroColorScheme = lightColorScheme(
    primary = RetroColors.Primary,
    onPrimary = RetroColors.Ink,
    primaryContainer = RetroColors.Primary,
    onPrimaryContainer = RetroColors.Ink,
    secondary = RetroColors.Secondary,
    onSecondary = RetroColors.Ink,
    secondaryContainer = RetroColors.Surface,
    onSecondaryContainer = RetroColors.Ink,
    tertiary = RetroColors.Secondary,
    onTertiary = RetroColors.Ink,
    background = RetroColors.Surface,
    onBackground = RetroColors.Ink,
    surface = RetroColors.Surface,
    onSurface = RetroColors.Ink,
    surfaceVariant = RetroColors.Surface,
    onSurfaceVariant = RetroColors.Ink,
    surfaceContainer = RetroColors.Surface,
    surfaceContainerHighest = RetroColors.Surface,
    error = RetroColors.Danger,
    onError = RetroColors.Surface,
    outline = RetroColors.Ink,
    outlineVariant = RetroColors.Ink,
)

@Composable
fun YamyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RetroColorScheme, typography = RetroTypography, content = content)
}
