package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/** Snapshot of current device idle resources. */
@Serializable
data class ResourceProfile(
    val airtimeBalance: AirtimeBalance?,
    val dataBundles: List<DataBundle>,
    val availableBandwidthMbps: Float,
    val freeStorageMb: Long,
    val idleComputePercent: Int,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val thermalState: ThermalState,
    val timestamp: Long,
) {
    companion object {
        /** Creates an empty resource profile with default/zero values. */
        fun empty(): ResourceProfile = ResourceProfile(
            airtimeBalance = null,
            dataBundles = emptyList(),
            availableBandwidthMbps = 0f,
            freeStorageMb = 0L,
            idleComputePercent = 0,
            batteryLevel = -1,
            isCharging = false,
            thermalState = ThermalState.COOL,
            timestamp = 0L,
        )
    }
}

/** Airtime balance for a specific carrier. */
@Serializable
data class AirtimeBalance(
    val carrier: String,
    val amountUnits: Long,
    val currency: String,
    val expiryTimestamp: Long?,
)

/** Data bundle allocation. */
@Serializable
data class DataBundle(
    val carrier: String,
    val remainingMb: Long,
    val totalMb: Long,
    val expiryTimestamp: Long,
    val bundleType: String,
)

/** Record of an airtime/data transaction. */
@Serializable
data class AirtimeTransaction(
    val id: String,
    val type: TransactionType,
    val amount: Long,
    val currency: String,
    val counterparty: String?,
    val platform: String,
    val outcome: TransactionOutcome,
    val timestamp: Long,
    val complianceCheckId: String,
)
