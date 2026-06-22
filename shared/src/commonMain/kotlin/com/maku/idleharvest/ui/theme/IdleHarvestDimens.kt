package com.maku.idleharvest.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * IdleHarvest Spacing & Dimension System
 *
 * Consistent spacing scale based on 4dp grid.
 * All touch targets respect WCAG minimum (48dp).
 */
object IdleHarvestDimens {
    // ─── Spacing Scale (4dp base grid) ───────────────────────────────────
    val SpaceXXS: Dp = 2.dp
    val SpaceXS: Dp = 4.dp
    val SpaceSM: Dp = 8.dp
    val SpaceMD: Dp = 12.dp
    val SpaceLG: Dp = 16.dp
    val SpaceXL: Dp = 24.dp
    val SpaceXXL: Dp = 32.dp
    val SpaceXXXL: Dp = 48.dp

    // ─── Screen Padding ──────────────────────────────────────────────────
    val ScreenPaddingHorizontal: Dp = 16.dp
    val ScreenPaddingVertical: Dp = 24.dp

    // ─── Touch Targets (WCAG compliant — minimum 48dp) ──────────────────
    val MinTouchTarget: Dp = 48.dp
    val ButtonHeight: Dp = 52.dp
    val IconButtonSize: Dp = 48.dp

    // ─── Cards ───────────────────────────────────────────────────────────
    val CardPadding: Dp = 16.dp
    val CardElevation: Dp = 2.dp
    val CardElevationHovered: Dp = 8.dp

    // ─── Component Sizes ─────────────────────────────────────────────────
    val AppBarHeight: Dp = 64.dp
    val BottomNavHeight: Dp = 80.dp
    val AgentStatusDotSize: Dp = 10.dp
    val HealthIndicatorSize: Dp = 12.dp
    val AvatarSizeSM: Dp = 32.dp
    val AvatarSizeMD: Dp = 40.dp
    val AvatarSizeLG: Dp = 56.dp

    // ─── Icon Sizes ──────────────────────────────────────────────────────
    val IconSizeSM: Dp = 16.dp
    val IconSizeMD: Dp = 24.dp
    val IconSizeLG: Dp = 32.dp
    val IconSizeXL: Dp = 48.dp

    // ─── Dividers ────────────────────────────────────────────────────────
    val DividerThickness: Dp = 1.dp

    // ─── Border ──────────────────────────────────────────────────────────
    val BorderThin: Dp = 1.dp
    val BorderMedium: Dp = 2.dp

    // ─── Logo Sizes ──────────────────────────────────────────────────────
    val LogoSplash: Dp = 120.dp
    val LogoAppBar: Dp = 32.dp
    val LogoOnboarding: Dp = 80.dp
}
