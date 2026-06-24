package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * Configuration for the Resource_Monitor scanning behavior.
 */
@Serializable
data class MonitorConfig(
    /** Interval between resource scans in milliseconds. Default: 15 minutes. */
    val scanIntervalMs: Long = 900_000L,
    /** Battery percentage threshold below which monitoring is reduced. */
    val lowBatteryThreshold: Int = 20,
    /** Whether to adapt behavior when device is thermally throttled. */
    val thermalThrottleEnabled: Boolean = true,
)

/**
 * User-defined thresholds for resource sharing.
 * Controls how much of the device's resources can be contributed to DePIN networks
 * and what minimums to maintain for user experience.
 */
@Serializable
data class ResourceThreshold(
    /** Minimum bandwidth to maintain before contributing to DePIN (Mbps). */
    val bandwidthMinMbps: Float = 1.0f,
    /** Minimum free storage to maintain before contributing (MB). */
    val storageMinMb: Long = 500L,
    /** Maximum CPU percentage allowed for DePIN compute contribution. */
    val computeMaxCpuPercent: Int = 30,
)
