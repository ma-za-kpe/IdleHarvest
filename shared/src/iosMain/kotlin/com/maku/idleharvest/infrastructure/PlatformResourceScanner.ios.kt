package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.ThermalState
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoThermalState
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState

/**
 * iOS resource scanner backed by real Foundation/UIKit APIs.
 *
 * Per the [PlatformResourceScanner] contract, each method degrades gracefully
 * (null/empty/sentinel) instead of throwing, so the ResourceMonitor can collect
 * partial results.
 *
 * Notes:
 * - Airtime/data bundles are carrier-private on iOS (no public API), so they remain
 *   null/empty and are expected to come from user input or a carrier integration.
 * - Bandwidth has no synchronous public API; reachability via NWPathMonitor only
 *   yields connectivity type, not a Mbps figure, so 0f is returned here.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual class PlatformResourceScanner {
    actual suspend fun scanAirtimeBalance(): AirtimeBalance? = null // No public carrier API on iOS.

    actual suspend fun scanDataBundles(): List<DataBundle> = emptyList() // No public carrier API on iOS.

    actual suspend fun scanBandwidth(): Float = 0f // No public synchronous throughput API on iOS.

    actual suspend fun scanFreeStorage(): Long {
        val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(NSHomeDirectory(), null)
        val freeBytes = (attrs?.get(NSFileSystemFreeSize) as? platform.Foundation.NSNumber)?.longLongValue ?: return 0L
        return freeBytes / (1024L * 1024L) // bytes → MB
    }

    actual suspend fun scanIdleCompute(): Int {
        // No public per-core utilization API on iOS; approximate idle headroom from
        // active vs. total logical cores (a conservative proxy).
        val info = NSProcessInfo.processInfo
        val total = info.processorCount.toInt().coerceAtLeast(1)
        val active = info.activeProcessorCount.toInt().coerceIn(0, total)
        return ((total - active) * 100) / total
    }

    actual suspend fun scanBatteryLevel(): Int {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        val level = device.batteryLevel // -1.0 if unknown, else 0.0..1.0
        return if (level < 0f) -1 else (level * 100).toInt()
    }

    actual suspend fun scanIsCharging(): Boolean {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        return when (device.batteryState) {
            UIDeviceBatteryState.UIDeviceBatteryStateCharging,
            UIDeviceBatteryState.UIDeviceBatteryStateFull,
            -> true
            else -> false
        }
    }

    actual suspend fun scanThermalState(): ThermalState = when (NSProcessInfo.processInfo.thermalState) {
        NSProcessInfoThermalState.NSProcessInfoThermalStateNominal -> ThermalState.COOL
        NSProcessInfoThermalState.NSProcessInfoThermalStateFair -> ThermalState.WARM
        NSProcessInfoThermalState.NSProcessInfoThermalStateSerious -> ThermalState.HOT
        NSProcessInfoThermalState.NSProcessInfoThermalStateCritical -> ThermalState.CRITICAL
        else -> ThermalState.COOL
    }
}
