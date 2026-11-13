package com.scrub.app.ui

import android.icu.util.ULocale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scrub.app.R
import java.util.Locale

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

/** Display font for Latin locales. Has no Cyrillic glyphs at all. */
val Bricolage = FontFamily(
    Font(R.font.bricolage_regular, FontWeight.Normal),
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

/**
 * Display font for Cyrillic locales (Macedonian, and any other Cyrillic-script phone language).
 * Onest (OFL, assets/onest_OFL.txt) covers Macedonian Ѓ Ќ Ѕ Џ Љ Њ Ј. One variable file
 * serves all four weights.
 */
@OptIn(ExperimentalTextApi::class)
val Onest = FontFamily(
    Font(R.font.onest_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.onest_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.onest_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.onest_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/** Headings and buttons in one display font. */
@Immutable
private class DisplayStyles(display: FontFamily) {
    val screenTitle = TextStyle(
        fontFamily = display, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp,
    )
    val sectionTitle = TextStyle(
        fontFamily = display, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    )
    val button = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp,
    )
    val stepNumber = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    )
}

private val LatinStyles = DisplayStyles(Bricolage)
private val CyrillicStyles = DisplayStyles(Onest)
private val LocalDisplayStyles = staticCompositionLocalOf { LatinStyles }

/** True when [locale] is written in Cyrillic: "mk", "ru", "sr" (Cyrillic by default), "sr-Cyrl"... */
fun isCyrillic(locale: Locale): Boolean {
    if (locale.script.isNotEmpty()) return locale.script == "Cyrl"
    return ULocale.addLikelySubtags(ULocale.forLocale(locale)).script == "Cyrl"
}

/**
 * Type scale. Headings and buttons use the display font: Bricolage for Latin locales, Onest for
 * Cyrillic ones, so Macedonian headings never fall back to the system font letter by letter.
 * Body text is the system sans in every language. The wordmark is always Bricolage.
 */
object ScrubType {
    /** "scrub" is never translated, so it keeps Bricolage in every language. */
    val Wordmark = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp, lineHeight = 64.sp, letterSpacing = (-2).sp,
    )
    val ScreenTitle: TextStyle
        @Composable @ReadOnlyComposable get() = LocalDisplayStyles.current.screenTitle
    val SectionTitle: TextStyle
        @Composable @ReadOnlyComposable get() = LocalDisplayStyles.current.sectionTitle
    val Body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp)
    val Small = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp)
    val Button: TextStyle
        @Composable @ReadOnlyComposable get() = LocalDisplayStyles.current.button
    /** Numbers in the 28dp step circles (Home steps, intro pages). */
    val StepNumber: TextStyle
        @Composable @ReadOnlyComposable get() = LocalDisplayStyles.current.stepNumber
}

private fun typography(s: DisplayStyles) = Typography(
    displayLarge = ScrubType.Wordmark,
    headlineMedium = s.screenTitle,
    titleLarge = s.sectionTitle,
    titleMedium = s.sectionTitle,
    bodyLarge = ScrubType.Body,
    bodyMedium = ScrubType.Small,
    bodySmall = ScrubType.Small,
    labelLarge = s.button,
)
private val LatinTypography = typography(LatinStyles)
private val CyrillicTypography = typography(CyrillicStyles)

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

/**
 * [animateChanges]: when [dark] flips (the Appearance switch), the colors cross-fade over
 * 300ms instead of jumping. Skipped when the system has animations turned off.
 */
@Composable
fun ScrubTheme(
    dark: Boolean = isSystemInDarkTheme(),
    animateChanges: Boolean = false,
    content: @Composable () -> Unit,
) {
    val target = if (dark) DarkPalette else LightPalette
    val palette = if (animateChanges && !rememberAnimationsOff()) animatedPalette(target) else target
    val locale = LocalConfiguration.current.locales[0]
    val cyrillic = remember(locale) { isCyrillic(locale) }
    CompositionLocalProvider(
        LocalScrubPalette provides palette,
        LocalDisplayStyles provides if (cyrillic) CyrillicStyles else LatinStyles,
    ) {
        MaterialTheme(
            colorScheme = scheme(palette, dark),
            typography = if (cyrillic) CyrillicTypography else LatinTypography,
            content = content,
        )
    }
}

@Composable
private fun animatedPalette(target: ScrubPalette): ScrubPalette {
    val spec = tween<Color>(durationMillis = 300)
    @Composable
    fun anim(c: Color) = animateColorAsState(c, spec, label = "palette").value
    return ScrubPalette(
        chalk = anim(target.chalk),
        paper = anim(target.paper),
        graphite = anim(target.graphite),
        pencil = anim(target.pencil),
        smudge = anim(target.smudge),
        eraser = target.eraser,
        onEraser = target.onEraser,
        inkRed = anim(target.inkRed),
    )
}
