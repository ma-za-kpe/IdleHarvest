package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.ThermalState

/**
 * Platform-specific resource scanning abstraction.
 *
 * Each platform (Android, iOS) provides an actual implementation that queries
 * hardware/OS APIs for the current device resource state.
 *
 * Failures in individual scan methods should return null or empty collections
 * rather than throwing exceptions, allowing the ResourceMonitor to collect
 * partial results gracefully.
 */
expect class PlatformResourceScanner {
    /** Query the device for current airtime balance. Returns null if unavailable. */
    suspend fun scanAirtimeBalance(): AirtimeBalance?

    /** Query available data bundles. Returns empty list if unavailable. */
    suspend fun scanDataBundles(): List<DataBundle>

    /** Query current available bandwidth in Mbps. Returns 0f if unavailable. */
    suspend fun scanBandwidth(): Float

    /** Query free storage in megabytes. Returns 0L if unavailable. */
    suspend fun scanFreeStorage(): Long

    /** Query idle compute percentage (0–100). Returns 0 if unavailable. */
    suspend fun scanIdleCompute(): Int

    /** Query current battery level (0–100). Returns -1 if unavailable. */
    suspend fun scanBatteryLevel(): Int

    /** Query whether the device is currently charging. Returns false if unavailable. */
    suspend fun scanIsCharging(): Boolean

    /** Query the current device thermal state. Returns COOL if unavailable. */
    suspend fun scanThermalState(): ThermalState
}
