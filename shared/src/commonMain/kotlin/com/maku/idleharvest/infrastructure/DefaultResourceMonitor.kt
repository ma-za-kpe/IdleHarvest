package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.interfaces.ResourceMonitor
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.MonitorConfig
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.domain.models.ThermalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Default implementation of [ResourceMonitor].
 *
 * Uses [PlatformResourceScanner] for the actual hardware/OS queries and
 * manages the periodic scanning loop with coroutines.
 *
 * Key behaviors:
 * - Configurable scan interval (default 15 minutes)
 * - Graceful failure handling: individual metric failures are logged but don't block the scan
 * - Thermal state adaptation: scan frequency adjusts based on device temperature
 * - Battery-level-aware frequency reduction: scan interval increases when battery is low
 * - Cold-start scan completes quickly (just collects available data)
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
 */
class DefaultResourceMonitor(
    private val scanner: PlatformResourceScanner,
    private val scope: CoroutineScope,
    private val eventBus: AgentEventBus,
) : ResourceMonitor {

    private val _resourceProfile = MutableStateFlow(ResourceProfile.empty())
    override val resourceProfile: StateFlow<ResourceProfile> = _resourceProfile.asStateFlow()

    private var monitoringJob: Job? = null
    private var config: MonitorConfig = MonitorConfig()
    private var thermalState: ThermalState = ThermalState.COOL

    override fun startMonitoring(config: MonitorConfig) {
        this.config = config
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            // Initial cold-start scan — fast, just collects whatever is available
            val initialProfile = performScan()
            _resourceProfile.value = initialProfile
            eventBus.publish(AgentEvent.ResourceUpdated(initialProfile))

            // Periodic scanning loop
            while (isActive) {
                val interval = computeEffectiveInterval()
                delay(interval)
                val profile = performScan()
                _resourceProfile.value = profile
                eventBus.publish(AgentEvent.ResourceUpdated(profile))
            }
        }
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    override fun forceRefresh(): ResourceProfile {
        val profile = _resourceProfile.value
        // Launch async scan but return current value immediately for synchronous callers.
        // The StateFlow will be updated when the scan completes.
        scope.launch {
            val refreshed = performScan()
            _resourceProfile.value = refreshed
            eventBus.publish(AgentEvent.ResourceUpdated(refreshed))
        }
        return profile
    }

    override fun adaptToThermalState(state: ThermalState) {
        thermalState = state
        // If CRITICAL, pause scanning entirely by cancelling the job
        if (state == ThermalState.CRITICAL) {
            monitoringJob?.cancel()
            monitoringJob = null
        } else if (monitoringJob == null || monitoringJob?.isActive != true) {
            // Resume scanning if previously paused (e.g., recovering from CRITICAL)
            startMonitoring(config)
        }
    }

    /**
     * Performs a single resource scan, collecting all available metrics.
     * If any individual metric fails, it's logged and the scan continues
     * with default/null values for that metric.
     */
    internal suspend fun performScan(): ResourceProfile {
        val airtimeBalance = scanSafely("airtimeBalance") { scanner.scanAirtimeBalance() }
        val dataBundles = scanSafely("dataBundles") { scanner.scanDataBundles() } ?: emptyList()
        val bandwidth = scanSafely("bandwidth") { scanner.scanBandwidth() } ?: 0f
        val freeStorage = scanSafely("freeStorage") { scanner.scanFreeStorage() } ?: 0L
        val idleCompute = scanSafely("idleCompute") { scanner.scanIdleCompute() } ?: 0
        val batteryLevel = scanSafely("batteryLevel") { scanner.scanBatteryLevel() } ?: -1
        val isCharging = scanSafely("isCharging") { scanner.scanIsCharging() } ?: false
        val scannedThermalState = scanSafely("thermalState") { scanner.scanThermalState() } ?: ThermalState.COOL

        // Update internal thermal state from scanner if available
        if (scannedThermalState != thermalState) {
            thermalState = scannedThermalState
        }

        return ResourceProfile(
            airtimeBalance = airtimeBalance,
            dataBundles = dataBundles,
            availableBandwidthMbps = bandwidth,
            freeStorageMb = freeStorage,
            idleComputePercent = idleCompute,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            thermalState = scannedThermalState,
            timestamp = currentTimeMillis(),
        )
    }

    /**
     * Computes the effective scan interval taking into account:
     * 1. Base configured interval
     * 2. Thermal state multiplier (COOL=1x, WARM=1.5x, HOT=3x, CRITICAL=paused)
     * 3. Low battery multiplier (2x when below threshold)
     */
    internal fun computeEffectiveInterval(): Long {
        val baseInterval = config.scanIntervalMs

        // Apply thermal multiplier
        val thermalMultiplier = when (thermalState) {
            ThermalState.COOL -> 1.0
            ThermalState.WARM -> 1.5
            ThermalState.HOT -> 3.0
            ThermalState.CRITICAL -> return Long.MAX_VALUE // Should not reach here; scanning is paused
        }

        // Apply battery multiplier
        val currentBattery = _resourceProfile.value.batteryLevel
        val isCharging = _resourceProfile.value.isCharging
        val batteryMultiplier = if (!isCharging && currentBattery in 0 until config.lowBatteryThreshold) {
            2.0
        } else {
            1.0
        }

        return (baseInterval * thermalMultiplier * batteryMultiplier).toLong()
    }

    /**
     * Safely executes a scan operation, catching any exceptions.
     * Logs failures and returns null on error so monitoring continues.
     */
    private suspend inline fun <T> scanSafely(metricName: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            // Log the failure — in production this would go to the rolling error log
            println("[ResourceMonitor] Failed to scan $metricName: ${e.message}")
            null
        }
    }

    companion object {
        /** Default scan interval: 15 minutes */
        const val DEFAULT_SCAN_INTERVAL_MS = 900_000L
    }
}
