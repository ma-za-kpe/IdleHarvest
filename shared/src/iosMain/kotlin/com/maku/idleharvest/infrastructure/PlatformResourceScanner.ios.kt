package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.ThermalState

/**
 * iOS-specific resource scanner implementation.
 *
 * This is a stub implementation that returns dummy values.
 * Real implementation will use UIDevice, NSProcessInfo, and
 * other iOS system APIs for resource detection.
 */
actual class PlatformResourceScanner {

    actual suspend fun scanAirtimeBalance(): AirtimeBalance? {
        // TODO: Implement via carrier API or user input
        return null
    }

    actual suspend fun scanDataBundles(): List<DataBundle> {
        // TODO: Implement via carrier API or user input
        return emptyList()
    }

    actual suspend fun scanBandwidth(): Float {
        // TODO: Implement via NWPathMonitor / Network framework
        return 0f
    }

    actual suspend fun scanFreeStorage(): Long {
        // TODO: Implement via FileManager.attributesOfFileSystem
        return 0L
    }

    actual suspend fun scanIdleCompute(): Int {
        // TODO: Implement via host_statistics / mach_host_self
        return 0
    }

    actual suspend fun scanBatteryLevel(): Int {
        // TODO: Implement via UIDevice.current.batteryLevel
        return -1
    }

    actual suspend fun scanIsCharging(): Boolean {
        // TODO: Implement via UIDevice.current.batteryState
        return false
    }

    actual suspend fun scanThermalState(): ThermalState {
        // TODO: Implement via ProcessInfo.thermalState
        return ThermalState.COOL
    }
}
