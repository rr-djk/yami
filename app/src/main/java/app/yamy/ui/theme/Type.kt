package app.yamy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.yamy.R

/** Macondo pour tout le texte ; JetBrains Mono réservé au technique (erreurs, chemins). */
val Macondo = FontFamily(Font(R.font.macondo_regular, FontWeight.Normal))
val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private fun macondo(sizeSp: Int, lineHeightSp: Int) = TextStyle(
    fontFamily = Macondo,
    fontWeight = FontWeight.Normal,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
)

/** La hiérarchie vient de la taille et de la couleur, jamais du graissage. Taille minimale : 14 sp. */
val RetroTypography = Typography(
    displayLarge = macondo(56, 68),
    displayMedium = macondo(44, 56),
    displaySmall = macondo(36, 48),
    headlineLarge = macondo(32, 44),
    headlineMedium = macondo(28, 40),
    headlineSmall = macondo(24, 36),
    titleLarge = macondo(24, 36),
    titleMedium = macondo(20, 30),
    titleSmall = macondo(18, 28),
    bodyLarge = macondo(18, 28),
    bodyMedium = macondo(16, 26),
    bodySmall = macondo(14, 22),
    labelLarge = macondo(17, 24),
    labelMedium = macondo(15, 22),
    labelSmall = macondo(14, 20),
)

val TechnicalTextStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, lineHeight = 22.sp)
