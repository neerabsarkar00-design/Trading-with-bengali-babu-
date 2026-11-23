package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

@Immutable
data class TradingColors(
    val isDark: Boolean,
    val profit: Color,
    val profitContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val gold: Color,
    val goldContainer: Color,
    val electricBlue: Color,
    val purple: Color,
    val teal: Color,
    val cyan: Color,
    val cardBorder: Color,
    val subtleSurface: Color
)

private val DarkTradingColors = TradingColors(
    isDark = true,
    profit = EmeraldGreen,
    profitContainer = Color(0xFF064E3B),
    loss = CoralRed,
    lossContainer = Color(0xFF7F1D1D),
    gold = BrightGold,
    goldContainer = Color(0xFF452B03),
    electricBlue = ElectricBlue,
    purple = VibrantPurple,
    teal = TealAccent,
    cyan = CyanAccent,
    cardBorder = SubtleNavyBorder,
    subtleSurface = NavySurfaceElevated
)

private val LightTradingColors = TradingColors(
    isDark = false,
    profit = EmeraldDark,
    profitContainer = Color(0xFFD1FAE5),
    loss = CrimsonRed,
    lossContainer = Color(0xFFFFE4E6),
    gold = Color(0xFFB45309),
    goldContainer = Color(0xFFFEF3C7),
    electricBlue = RoyalBlue,
    purple = DeepPurple,
    teal = Color(0xFF0F766E),
    cyan = Color(0xFF0E7490),
    cardBorder = SubtleLightBorder,
    subtleSurface = SoftSlateSurface
)

val LocalTradingColors = staticCompositionLocalOf { DarkTradingColors }

private val DarkColorScheme = darkColorScheme(
    primary = RoyalBlue,
    onPrimary = CrispLightText,
    primaryContainer = NavySurfaceElevated,
    onPrimaryContainer = ElectricBlue,
    secondary = VibrantPurple,
    onSecondary = CrispLightText,
    secondaryContainer = Color(0xFF2D1B69),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = BrightGold,
    onTertiary = DeepNavy,
    tertiaryContainer = Color(0xFF452B03),
    onTertiaryContainer = BrightGold,
    background = DeepNavy,
    onBackground = CrispLightText,
    surface = MidnightBlue,
    onSurface = CrispLightText,
    surfaceVariant = NavySurface,
    onSurfaceVariant = SoftBlueGrayText,
    outline = SubtleNavyBorder,
    error = CoralRed,
    onError = CrispLightText
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = CrispWhite,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = DeepPurple,
    onSecondary = CrispWhite,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = Color(0xFFD97706),
    onTertiary = CrispWhite,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = OffWhiteBg,
    onBackground = DarkNavyText,
    surface = CrispWhite,
    onSurface = DarkNavyText,
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = MutedSlateText,
    outline = SubtleLightBorder,
    error = CrimsonRed,
    onError = CrispWhite
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TradingJournalTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val tradingColors = if (useDark) DarkTradingColors else LightTradingColors

    CompositionLocalProvider(LocalTradingColors provides tradingColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
