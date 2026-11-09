package com.scrub.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scrub.app.R

/**
 * "Pencil and eraser": paper, graphite and one pink eraser.
 * These are the only colors the UI uses. Read them through [ScrubTheme.colors].
 */
@Immutable
data class ScrubPalette(
    /** App background. */
    val chalk: Color,
    /** Raised surfaces, photo tiles. */
    val paper: Color,
    /** Main text, icons, secondary buttons. */
    val graphite: Color,
    /** Secondary text. */
    val pencil: Color,
    /** Hairlines, borders, empty slots, disabled fills. */
    val smudge: Color,
    /** The accent. Fills only, never text. */
    val eraser: Color,
    /** Text/icons drawn on top of [eraser]. */
    val onEraser: Color,
    /** Permanent delete only. */
    val inkRed: Color,
    /** Scrim behind marks drawn on top of photos (same in both themes). */
    val photoScrim: Color = GraphiteInk,
    /** Marks drawn on top of photos (same in both themes). */
    val onPhoto: Color = PaperWhite,
)

private val Eraser = Color(0xFFF2A0AE)
private val GraphiteInk = Color(0xFF23252A)
private val PaperWhite = Color(0xFFFFFFFF)

val LightPalette = ScrubPalette(
    chalk = Color(0xFFF2F3EF),
    paper = PaperWhite,
    graphite = GraphiteInk,
    pencil = Color(0xFF6B6F76),
    smudge = Color(0xFFD9DBD5),
    eraser = Eraser,
    onEraser = GraphiteInk,
    inkRed = Color(0xFFB93A34),
)

val DarkPalette = ScrubPalette(
    chalk = Color(0xFF1A1B1E),
    paper = Color(0xFF24262A),
    graphite = Color(0xFFECEDE8),
    pencil = Color(0xFF9A9EA5),
    smudge = Color(0xFF34373C),
    eraser = Eraser,
    onEraser = GraphiteInk,
    inkRed = Color(0xFFE0675F),
)

private val LocalScrubPalette = staticCompositionLocalOf { LightPalette }

val Bricolage = FontFamily(
    Font(R.font.bricolage_regular, FontWeight.Normal),
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

/** Type scale. Bricolage for headings and buttons, system sans for body. */
object ScrubType {
    val Wordmark = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp, lineHeight = 64.sp, letterSpacing = (-2).sp,
    )
    val ScreenTitle = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp,
    )
    val SectionTitle = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    )
    val Body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp)
    val Small = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp)
    val Button = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp,
    )
    /** Numbers in the 28dp step circles (Home steps, intro pages). */
    val StepNumber = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    )
}

private val Type = Typography(
    displayLarge = ScrubType.Wordmark,
    headlineMedium = ScrubType.ScreenTitle,
    titleLarge = ScrubType.SectionTitle,
    titleMedium = ScrubType.SectionTitle,
    bodyLarge = ScrubType.Body,
    bodyMedium = ScrubType.Small,
    bodySmall = ScrubType.Small,
    labelLarge = ScrubType.Button,
)

private fun scheme(p: ScrubPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.eraser, onPrimary = p.onEraser,
        secondary = p.graphite, onSecondary = p.chalk,
        background = p.chalk, onBackground = p.graphite,
        surface = p.paper, onSurface = p.graphite,
        surfaceVariant = p.paper, onSurfaceVariant = p.pencil,
        surfaceContainerHigh = p.paper, surfaceContainer = p.paper,
        outline = p.smudge, outlineVariant = p.smudge,
        error = p.inkRed, onError = p.chalk,
    )
} else {
    lightColorScheme(
        primary = p.eraser, onPrimary = p.onEraser,
        secondary = p.graphite, onSecondary = p.chalk,
        background = p.chalk, onBackground = p.graphite,
        surface = p.paper, onSurface = p.graphite,
        surfaceVariant = p.paper, onSurfaceVariant = p.pencil,
        surfaceContainerHigh = p.paper, surfaceContainer = p.paper,
        outline = p.smudge, outlineVariant = p.smudge,
        error = p.inkRed, onError = p.paper,
    )
}

object ScrubTheme {
    val colors: ScrubPalette
        @Composable @ReadOnlyComposable get() = LocalScrubPalette.current
}

@Composable
fun ScrubTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalScrubPalette provides palette) {
        MaterialTheme(colorScheme = scheme(palette, dark), typography = Type, content = content)
    }
}
