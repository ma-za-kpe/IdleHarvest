package com.maku.idleharvest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * IdleHarvest Design System — Light & Dark themes
 *
 * Built on Material 3 with the Bold & Symbolic brand palette.
 * Emerald Green primary, Vibrant Gold accents, Deep Navy depth.
 */

// ─── Light Color Scheme ──────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary = IdleHarvestColors.PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = IdleHarvestColors.PrimaryGreenLight.copy(alpha = 0.15f),
    onPrimaryContainer = IdleHarvestColors.PrimaryGreenDark,

    secondary = IdleHarvestColors.AccentGold,
    onSecondary = IdleHarvestColors.Navy,
    secondaryContainer = IdleHarvestColors.AccentGoldLight.copy(alpha = 0.2f),
    onSecondaryContainer = IdleHarvestColors.AccentGoldDark,

    tertiary = IdleHarvestColors.Navy,
    onTertiary = Color.White,
    tertiaryContainer = IdleHarvestColors.NavyLight.copy(alpha = 0.1f),
    onTertiaryContainer = IdleHarvestColors.Navy,

    background = IdleHarvestColors.LightBackground,
    onBackground = IdleHarvestColors.TextPrimaryLight,

    surface = IdleHarvestColors.LightSurface,
    onSurface = IdleHarvestColors.TextPrimaryLight,
    surfaceVariant = IdleHarvestColors.LightSurfaceVariant,
    onSurfaceVariant = IdleHarvestColors.TextSecondaryLight,

    error = IdleHarvestColors.Error,
    onError = Color.White,
    errorContainer = IdleHarvestColors.Error.copy(alpha = 0.1f),
    onErrorContainer = IdleHarvestColors.Error,

    outline = IdleHarvestColors.WarmGray,
    outlineVariant = IdleHarvestColors.WarmGray.copy(alpha = 0.5f),

    inverseSurface = IdleHarvestColors.Navy,
    inverseOnSurface = IdleHarvestColors.LightBackground,
    inversePrimary = IdleHarvestColors.PrimaryGreenLight,
)

// ─── Dark Color Scheme ───────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary = IdleHarvestColors.PrimaryGreenLight,
    onPrimary = IdleHarvestColors.NavyDark,
    primaryContainer = IdleHarvestColors.PrimaryGreen.copy(alpha = 0.25f),
    onPrimaryContainer = IdleHarvestColors.PrimaryGreenLight,

    secondary = IdleHarvestColors.AccentGold,
    onSecondary = IdleHarvestColors.NavyDark,
    secondaryContainer = IdleHarvestColors.AccentGoldDark.copy(alpha = 0.25f),
    onSecondaryContainer = IdleHarvestColors.AccentGoldLight,

    tertiary = IdleHarvestColors.WarmGray,
    onTertiary = IdleHarvestColors.Navy,
    tertiaryContainer = IdleHarvestColors.NavyLight.copy(alpha = 0.3f),
    onTertiaryContainer = IdleHarvestColors.WarmGray,

    background = IdleHarvestColors.DarkBackground,
    onBackground = IdleHarvestColors.TextPrimaryDark,

    surface = IdleHarvestColors.DarkSurface,
    onSurface = IdleHarvestColors.TextPrimaryDark,
    surfaceVariant = IdleHarvestColors.DarkSurfaceVariant,
    onSurfaceVariant = IdleHarvestColors.TextSecondaryDark,

    error = IdleHarvestColors.ErrorDark,
    onError = IdleHarvestColors.NavyDark,
    errorContainer = IdleHarvestColors.Error.copy(alpha = 0.2f),
    onErrorContainer = IdleHarvestColors.ErrorDark,

    outline = IdleHarvestColors.WarmGray600,
    outlineVariant = IdleHarvestColors.WarmGray800,

    inverseSurface = IdleHarvestColors.LightBackground,
    inverseOnSurface = IdleHarvestColors.Navy,
    inversePrimary = IdleHarvestColors.PrimaryGreen,
)

// ─── Extended Brand Colors (accessible via LocalIdleHarvestColors) ───────────
data class IdleHarvestExtendedColors(
    val accentGold: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val healthGreen: Color,
    val healthYellow: Color,
    val healthRed: Color,
    val earningAirtime: Color,
    val earningDePIN: Color,
    val earningMesh: Color,
    val earningNanopayment: Color,
    val textTertiary: Color,
    val cardBackground: Color,
    val divider: Color,
)

val LocalIdleHarvestColors = staticCompositionLocalOf {
    IdleHarvestExtendedColors(
        accentGold = IdleHarvestColors.AccentGold,
        success = IdleHarvestColors.Success,
        warning = IdleHarvestColors.Warning,
        info = IdleHarvestColors.Info,
        healthGreen = IdleHarvestColors.HealthGreen,
        healthYellow = IdleHarvestColors.HealthYellow,
        healthRed = IdleHarvestColors.HealthRed,
        earningAirtime = IdleHarvestColors.EarningAirtime,
        earningDePIN = IdleHarvestColors.EarningDePIN,
        earningMesh = IdleHarvestColors.EarningMesh,
        earningNanopayment = IdleHarvestColors.EarningNanopayment,
        textTertiary = IdleHarvestColors.TextTertiaryLight,
        cardBackground = IdleHarvestColors.LightSurface,
        divider = IdleHarvestColors.WarmGray,
    )
}

private val LightExtendedColors = IdleHarvestExtendedColors(
    accentGold = IdleHarvestColors.AccentGold,
    success = IdleHarvestColors.Success,
    warning = IdleHarvestColors.Warning,
    info = IdleHarvestColors.Info,
    healthGreen = IdleHarvestColors.HealthGreen,
    healthYellow = IdleHarvestColors.HealthYellow,
    healthRed = IdleHarvestColors.HealthRed,
    earningAirtime = IdleHarvestColors.EarningAirtime,
    earningDePIN = IdleHarvestColors.EarningDePIN,
    earningMesh = IdleHarvestColors.EarningMesh,
    earningNanopayment = IdleHarvestColors.EarningNanopayment,
    textTertiary = IdleHarvestColors.TextTertiaryLight,
    cardBackground = IdleHarvestColors.LightSurface,
    divider = IdleHarvestColors.WarmGray,
)

private val DarkExtendedColors = IdleHarvestExtendedColors(
    accentGold = IdleHarvestColors.AccentGold,
    success = IdleHarvestColors.SuccessDark,
    warning = IdleHarvestColors.WarningDark,
    info = IdleHarvestColors.InfoDark,
    healthGreen = IdleHarvestColors.HealthGreen,
    healthYellow = IdleHarvestColors.HealthYellow,
    healthRed = IdleHarvestColors.HealthRed,
    earningAirtime = IdleHarvestColors.PrimaryGreenLight,
    earningDePIN = Color(0xFFA78BFA),
    earningMesh = IdleHarvestColors.InfoDark,
    earningNanopayment = IdleHarvestColors.AccentGoldLight,
    textTertiary = IdleHarvestColors.TextTertiaryDark,
    cardBackground = IdleHarvestColors.DarkSurface,
    divider = IdleHarvestColors.WarmGray800,
)

// ─── Theme Composable ────────────────────────────────────────────────────────
@Composable
fun IdleHarvestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalIdleHarvestColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IdleHarvestTypography,
            shapes = IdleHarvestShapes,
            content = content
        )
    }
}

/**
 * Access extended brand colors from anywhere in the compose tree.
 *
 * Usage: `IdleHarvestTheme.extendedColors.accentGold`
 */
object IdleHarvestTheme {
    val extendedColors: IdleHarvestExtendedColors
        @Composable
        get() = LocalIdleHarvestColors.current
}
