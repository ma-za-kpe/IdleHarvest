package com.maku.idleharvest.ui.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/** Formats an amount as "X.XX USDC" with optional local currency equivalent. */
fun formatUsdc(amount: Double): String = "${formatFixed(amount, 2)} USDC"

/**
 * Formats a dual-currency string: local currency + USDC equivalent.
 * localSymbol: e.g. "₦" (NGN), "KSh" (KES), "XOF" (CFA), "GH₵" (GHS)
 */
fun formatDualCurrency(
    usdcAmount: Double,
    localRate: Double,
    localSymbol: String,
): String {
    val local = usdcAmount * localRate
    return "$localSymbol${formatFixed(local, 0)} (${formatUsdc(usdcAmount)})"
}

/** Simple multiplatform numeric formatter that avoids platform-specific `String.format`. */
private fun formatFixed(
    value: Double,
    decimals: Int,
): String {
    val factor = 10.0.pow(decimals)
    val rounded = round(value * factor).toLong()
    val sign = if (rounded < 0L) "-" else ""
    val abs = abs(rounded)
    val intPart = abs / factor.toLong()
    val fracPart = abs % factor.toLong()
    return if (decimals == 0) {
        "$sign$intPart"
    } else {
        "$sign$intPart.${fracPart.toString().padStart(decimals, '0')}"
    }
}

/** Common local-currency configs for target markets. */
object LocalCurrencyConfig {
    data class Config(
        val symbol: String,
        val usdRate: Double,
    )

    val NGN = Config("₦", 1600.0)
    val KES = Config("KSh", 130.0)
    val GHS = Config("GH₵", 15.0)
    val XOF = Config("CFA", 615.0)

    fun forLocale(languageTag: String): Config = when {
        languageTag.startsWith("ha") -> NGN
        languageTag.startsWith("sw") -> KES
        languageTag.startsWith("fr") -> XOF
        else -> Config("$", 1.0)
    }
}
