package com.scrub.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object ScrubColors {
    val Background = Color(0xFF0E0E14)
    val Surface = Color(0xFF17161F)
    val SurfaceHigh = Color(0xFF221F2E)
    val Outline = Color(0xFF2E2B3B)
    val Coral = Color(0xFFFF6B6B)
    val Violet = Color(0xFF8B5CF6)
    val TextPrimary = Color(0xFFF4F2FA)
    val TextSecondary = Color(0xFFA19DB3)

    val Accent = Brush.linearGradient(listOf(Coral, Violet))
}

private val Colors = darkColorScheme(
    primary = ScrubColors.Coral,
    onPrimary = Color.White,
    secondary = ScrubColors.Violet,
    onSecondary = Color.White,
    background = ScrubColors.Background,
    onBackground = ScrubColors.TextPrimary,
    surface = ScrubColors.Surface,
    onSurface = ScrubColors.TextPrimary,
    surfaceVariant = ScrubColors.SurfaceHigh,
    onSurfaceVariant = ScrubColors.TextSecondary,
    outline = ScrubColors.Outline,
    error = Color(0xFFFF5470),
)

private val Type = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ScrubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
