package com.maku.idleharvest.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * IdleHarvest Shape System
 *
 * Rounded but not overly soft — modern and trustworthy.
 * Cards and containers use 16dp, buttons use 12dp, chips use full rounding.
 */
val IdleHarvestShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Additional shape tokens specific to IdleHarvest UI components.
 */
object IdleHarvestShapeTokens {
    /** Earning cards on the dashboard */
    val EarningCard = RoundedCornerShape(16.dp)

    /** Agent status cards */
    val AgentCard = RoundedCornerShape(12.dp)

    /** Bottom sheet handle area */
    val BottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** Onboarding page indicator dots */
    val Dot = RoundedCornerShape(50)

    /** Action buttons (primary CTA) */
    val Button = RoundedCornerShape(12.dp)

    /** Pill-shaped chips and badges */
    val Chip = RoundedCornerShape(50)

    /** Navigation bar item indicator */
    val NavIndicator = RoundedCornerShape(50)

    /** Toast / snackbar */
    val Snackbar = RoundedCornerShape(8.dp)
}
