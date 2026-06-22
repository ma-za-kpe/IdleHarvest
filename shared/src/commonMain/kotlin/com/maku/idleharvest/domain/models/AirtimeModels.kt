package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * An airtime or data bundle that can be evaluated for monetization.
 */
@Serializable
data class AirtimeBundle(
    val id: String,
    val carrier: String,
    val type: AirtimeBundleType,
    val amountUnits: Long,
    val currency: String,
    val remainingMb: Long?,
    val expiryTimestamp: Long,
    val purchasedAt: Long,
)

/**
 * Type of airtime bundle.
 */
@Serializable
enum class AirtimeBundleType { AIRTIME, DATA, COMBO }

/**
 * Monetization recommendation produced by the Airtime_Agent.
 */
@Serializable
sealed class MonetizationRecommendation {
    /** Sell the bundle on a VTU platform at the estimated value. */
    @Serializable
    data class Sell(
        val amount: Long,
        val platform: VtuPlatform,
        val confidence: Float,
    ) : MonetizationRecommendation()

    /** Transfer the bundle to a designated recipient. */
    @Serializable
    data class Transfer(
        val recipient: String,
        val amount: Long,
    ) : MonetizationRecommendation()

    /** Hold the bundle — monetization not recommended at this time. */
    @Serializable
    data object Hold : MonetizationRecommendation()
}

/**
 * Supported VTU (Virtual Top-Up) platforms for airtime monetization.
 */
@Serializable
enum class VtuPlatform { PRESTMIT, VTU_NG, RELOADLY }

/**
 * An action to execute on a bundle (sell or transfer).
 */
@Serializable
data class MonetizationAction(
    val bundleId: String,
    val recommendation: MonetizationRecommendation,
    val approvedBy: ApprovalSource,
    val timestamp: Long,
)

/**
 * Source of action approval — manual user confirmation or policy auto-approval.
 */
@Serializable
enum class ApprovalSource { USER_MANUAL, POLICY_AUTO }

/**
 * Result of executing a monetization action.
 */
@Serializable
data class TransactionResult(
    val transactionId: String,
    val success: Boolean,
    val amountSettled: Long?,
    val errorMessage: String?,
    val timestamp: Long,
)
