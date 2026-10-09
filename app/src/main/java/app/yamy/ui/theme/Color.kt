package app.yamy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette rétro (skill retro-style). Contrastes mesurés sur blanc :
 * texte 17,7:1 ; bleu 3,7:1 ; violet 4,2:1 ; rouge 4,8:1.
 * Le bleu sert donc de fond (texte encre dessus, 4,8:1), le violet de grands titres et de contours,
 * le rouge de texte d'erreur sur blanc.
 */
object RetroColors {
    val Primary = Color(0xFF3B82F6)
    val Secondary = Color(0xFF8B5CF6)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFD97706)
    val Danger = Color(0xFFDC2626)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111827)
}
