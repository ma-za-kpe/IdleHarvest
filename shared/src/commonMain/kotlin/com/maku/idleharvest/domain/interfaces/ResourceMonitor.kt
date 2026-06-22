package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.MonitorConfig
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.domain.models.ThermalState
import kotlinx.coroutines.flow.StateFlow

/**
 * Detects and tracks idle device resources.
 * Produces a [ResourceProfile] at configurable intervals for agent consumption.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.5
 */
interface ResourceMonitor {
    /** Current resource profile, updated at the configured scan interval. */
    val resourceProfile: StateFlow<ResourceProfile>

    /** Start periodic resource monitoring with the given configuration. */
    fun startMonitoring(config: MonitorConfig)

    /** Stop periodic resource monitoring and release system resources. */
    fun stopMonitoring()

    /** Trigger an immediate resource scan, bypassing the interval timer. */
    fun forceRefresh(): ResourceProfile

    /** Adapt scan frequency and agent activity based on the device thermal state. */
    fun adaptToThermalState(state: ThermalState)
}
