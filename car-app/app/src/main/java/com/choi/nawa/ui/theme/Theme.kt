package com.choi.nawa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The only place colors are written down. Same palette as the passenger web (web/src/index.css).
 * Screens use these names (or MaterialTheme.colorScheme, which is built from them), never raw values.
 */
object Palette {
    /** Screen background */
    val Ground = Color(0xFFF2F4F6)
    /** Cards and panels */
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1A2433)
    val Muted = Color(0xFF6B7684)
    val Line = Color(0xFFE3E7EC)
    /** Emphasis: the current stop, the main button. Text on it is Ink. */
    val Signal = Color(0xFFFFB21E)
    val SignalSoft = Color(0xFFFFF3D6)
    /** Route and information */
    val Route = Color(0xFF2E62F5)
    val Ok = Color(0xFF2E9E5B)
    val Danger = Color(0xFFD93A3A)
}

/** Every Material role mapped onto the palette, so no default purple/pink shows anywhere. */
private val NawaColors = lightColorScheme(
    primary = Palette.Signal,
    onPrimary = Palette.Ink,
    primaryContainer = Palette.SignalSoft,
    onPrimaryContainer = Palette.Ink,
    inversePrimary = Palette.Signal,
    secondary = Palette.Route,
    onSecondary = Palette.Card,
    secondaryContainer = Palette.SignalSoft,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Route,
    onTertiary = Palette.Card,
    tertiaryContainer = Palette.Ground,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Ground,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Ground,
    onSurfaceVariant = Palette.Muted,
    surfaceTint = Palette.Card,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Card,
    error = Palette.Danger,
    onError = Palette.Card,
    errorContainer = Palette.SignalSoft,
    onErrorContainer = Palette.Danger,
    outline = Palette.Muted,
    outlineVariant = Palette.Line,
    scrim = Palette.Ink,
    surfaceBright = Palette.Card,
    surfaceDim = Palette.Ground,
    surfaceContainerLowest = Palette.Card,
    surfaceContainerLow = Palette.Card,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.Card,
    surfaceContainerHighest = Palette.Ground,
)

private fun style(size: Int, weight: FontWeight, line: Float = 1.3f) =
    TextStyle(fontFamily = FontFamily.Default, fontWeight = weight, fontSize = size.sp, lineHeight = (size * line).sp)

/**
 * Sized for a car screen read at arm's length: body 24sp+, titles 32sp+, the panel's destination and ETA 44sp.
 *  - headlineLarge: the trip panel's current destination and ETA
 *  - headlineMedium / headlineSmall: screen titles
 *  - titleLarge: main button labels, emphasized rows (the current stop)
 *  - titleMedium: section titles, secondary buttons, list item names
 *  - bodyLarge / bodyMedium: body text (bodyMedium for secondary lines like addresses)
 *  - labelLarge: small meta (timestamps, upcoming ETAs, connection line)
 *  - labelSmall: diagnostic log
 */
private val NawaTypography = Typography(
    headlineLarge = style(44, FontWeight.ExtraBold, 1.2f),
    headlineMedium = style(34, FontWeight.ExtraBold, 1.2f),
    headlineSmall = style(32, FontWeight.Bold, 1.2f),
    titleLarge = style(28, FontWeight.Bold),
    titleMedium = style(24, FontWeight.SemiBold),
    titleSmall = style(24, FontWeight.Medium),
    bodyLarge = style(24, FontWeight.Normal),
    bodyMedium = style(24, FontWeight.Normal),
    bodySmall = style(22, FontWeight.Normal),
    labelLarge = style(22, FontWeight.Medium),
    labelMedium = style(22, FontWeight.Medium),
    labelSmall = style(18, FontWeight.Normal),
)

/** Always the light car palette: no dynamic color, no dark scheme. */
@Composable
fun NawaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NawaColors, typography = NawaTypography, content = content)
}
