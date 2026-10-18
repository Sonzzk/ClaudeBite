package com.sonzzk.claudebite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Claude-inspired warm palette with a cute pastel twist.
object Palette {
    val ClaudeOrange = Color(0xFFD97757)
    val ClaudeOrangeDark = Color(0xFFB85C3E)
    val Peach = Color(0xFFFFD9C7)
    val Cream = Color(0xFFFAF3EC)
    val Ivory = Color(0xFFFFFCF8)
    val Ink = Color(0xFF2B2420)
    val InkSoft = Color(0xFF6B5E57)
    val SakuraPink = Color(0xFFF7A8B8)
    val Mint = Color(0xFF8FD3B6)
    val Sky = Color(0xFF8EC5F0)
    val Butter = Color(0xFFFFD66B)
    val Lavender = Color(0xFFC3B1F0)

    // Macro colors used everywhere (rings, bars, charts) so users learn them.
    val Protein = Color(0xFFE8738A)
    val Carbs = Color(0xFFF2B544)
    val Fat = Color(0xFF7FB8E6)
    val Water = Color(0xFF5DADE2)

    val NightBg = Color(0xFF1E1A18)
    val NightSurface = Color(0xFF2A2421)
}

private val LightColors = lightColorScheme(
    primary = Palette.ClaudeOrange,
    onPrimary = Color.White,
    primaryContainer = Palette.Peach,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.SakuraPink,
    onSecondary = Palette.Ink,
    secondaryContainer = Color(0xFFFFE4EA),
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Mint,
    onTertiary = Palette.Ink,
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Palette.Ivory,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFF3E7DC),
    onSurfaceVariant = Palette.InkSoft,
    outline = Color(0xFFD8C6B8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE88B6C),
    onPrimary = Palette.Ink,
    primaryContainer = Palette.ClaudeOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = Palette.SakuraPink,
    onSecondary = Palette.Ink,
    secondaryContainer = Color(0xFF5A3640),
    tertiary = Palette.Mint,
    background = Palette.NightBg,
    onBackground = Color(0xFFF3E9E2),
    surface = Palette.NightSurface,
    onSurface = Color(0xFFF3E9E2),
    surfaceVariant = Color(0xFF3A322E),
    onSurfaceVariant = Color(0xFFCDBFB6),
    outline = Color(0xFF5C514B),
)

private val AppTypography = Typography().let { t ->
    val rounded = FontFamily.SansSerif
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = rounded, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = rounded, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = rounded, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = rounded, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontFamily = rounded, fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontFamily = rounded, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun ClaudeBiteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
