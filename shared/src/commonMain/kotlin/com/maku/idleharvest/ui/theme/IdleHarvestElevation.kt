package com.maku.idleharvest.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * IdleHarvest Elevation System
 *
 * Subtle elevation for light mode, minimal for dark mode (relies on surface tint).
 */
object IdleHarvestElevation {
    /** Flat — no elevation (background, full-bleed sections) */
    val Level0: Dp = 0.dp

    /** Subtle lift — cards at rest */
    val Level1: Dp = 1.dp

    /** Default card — earning cards, agent status */
    val Level2: Dp = 2.dp

    /** Raised — hovered cards, dropdowns */
    val Level3: Dp = 4.dp

    /** Prominent — dialogs, bottom sheets */
    val Level4: Dp = 8.dp

    /** Maximum — floating action button, modal overlays */
    val Level5: Dp = 12.dp
}
