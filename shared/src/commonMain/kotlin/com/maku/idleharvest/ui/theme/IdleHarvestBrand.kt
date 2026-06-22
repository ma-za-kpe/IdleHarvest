package com.maku.idleharvest.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * IdleHarvest Brand Identity Object
 *
 * Central reference for brand constants used across all platforms (Android, iOS, Web).
 * Provides quick access to core brand colors, styles, and configuration.
 *
 * Style: Bold & Symbolic — stylized phone + glowing harvest elements
 * Tone: Modern, trustworthy, empowering, slightly vibrant (Africa-friendly energy)
 */
object IdleHarvestBrand {

    // ─── Core Brand Colors ───────────────────────────────────────────────
    val PrimaryGreen = Color(0xFF0A7C6B)
    val AccentGold = Color(0xFFFFB300)
    val Navy = Color(0xFF0F1C3A)
    val LightBg = Color(0xFFF8F9FA)
    val WarmGray = Color(0xFFE5E7EB)

    // ─── Logo Text Style ─────────────────────────────────────────────────
    val LogoTextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = 1.5.sp,
    )

    // ─── Brand Name ──────────────────────────────────────────────────────
    const val APP_NAME = "IdleHarvest"
    const val APP_TAGLINE = "Harvest Your Idle Power"
    const val APP_DESCRIPTION = "Turn unused phone resources into real earnings — privately, autonomously."

    // ─── Hex Values (for platform-native usage: XML, CSS, SwiftUI) ───────
    object Hex {
        const val PRIMARY_GREEN = "#0A7C6B"
        const val PRIMARY_GREEN_LIGHT = "#3DA896"
        const val PRIMARY_GREEN_DARK = "#065A4E"
        const val ACCENT_GOLD = "#FFB300"
        const val ACCENT_GOLD_LIGHT = "#FFCC4D"
        const val ACCENT_GOLD_DARK = "#CC8F00"
        const val NAVY = "#0F1C3A"
        const val NAVY_LIGHT = "#1A2E5A"
        const val NAVY_DARK = "#080F20"
        const val LIGHT_BG = "#F8F9FA"
        const val DARK_BG = "#0B1120"
        const val WARM_GRAY = "#E5E7EB"
    }

    // ─── Social Media / Marketing Colors ─────────────────────────────────
    object Social {
        /** Play Store feature graphic background */
        val PlayStoreBackground = Color(0xFF0F1C3A)

        /** Open Graph image background */
        val OgImageBackground = Color(0xFF0A7C6B)

        /** Twitter/X header background */
        val SocialHeaderBackground = Color(0xFF0F1C3A)
    }

    // ─── Platform-Specific Sizing ────────────────────────────────────────
    object IconSizes {
        // Android
        const val ANDROID_MDPI = 48
        const val ANDROID_HDPI = 72
        const val ANDROID_XHDPI = 96
        const val ANDROID_XXHDPI = 144
        const val ANDROID_XXXHDPI = 192
        const val ANDROID_ADAPTIVE = 108 // Adaptive icon viewport

        // iOS
        const val IOS_APP_ICON = 1024
        const val IOS_SPOTLIGHT = 120
        const val IOS_SETTINGS = 87
        const val IOS_NOTIFICATION = 60

        // Web
        const val WEB_FAVICON_16 = 16
        const val WEB_FAVICON_32 = 32
        const val WEB_APPLE_TOUCH = 180
        const val WEB_OG_IMAGE_WIDTH = 1200
        const val WEB_OG_IMAGE_HEIGHT = 630

        // Play Store
        const val PLAY_STORE_ICON = 512
        const val PLAY_STORE_FEATURE_WIDTH = 1024
        const val PLAY_STORE_FEATURE_HEIGHT = 500
    }
}
