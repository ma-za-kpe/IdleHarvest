package com.maku.idleharvest.infrastructure

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.ThermalState

/**
 * Android-specific resource scanner implementation.
 *
 * Uses real Android system APIs:
 * - [BatteryManager] for battery level and charging state
 * - [ConnectivityManager] for available bandwidth estimation
 * - [StatFs] for free storage on internal storage
 * - [ActivityManager] and /proc/stat for idle compute estimation
 * - [PowerManager] for thermal state (API 29+)
 */
actual class PlatformResourceScanner(
    private val context: Context,
) {
    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    actual suspend fun scanAirtimeBalance(): AirtimeBalance? {
        // Airtime balance requires carrier-specific USSD or user input.
        // Not available via standard Android APIs — returns null until
        // a VTU platform integration or user-input mechanism is wired.
        return null
    }

    actual suspend fun scanDataBundles(): List<DataBundle> {
        // Data bundle information requires carrier-specific APIs or user input.
        // Not available via standard Android APIs — returns empty until
        // carrier integration is established.
        return emptyList()
    }

    actual suspend fun scanBandwidth(): Float {
        return try {
            val network = connectivityManager.activeNetwork ?: return 0f
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return 0f

            // getLinkDownstreamBandwidthKbps returns estimated downstream in Kbps
            val bandwidthKbps = capabilities.linkDownstreamBandwidthKbps
            bandwidthKbps / 1000f // Convert to Mbps
        } catch (_: SecurityException) {
            0f
        } catch (_: Exception) {
            0f
        }
    }

    actual suspend fun scanFreeStorage(): Long = try {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        availableBytes / (1024L * 1024L) // Convert bytes to MB
    } catch (_: Exception) {
        0L
    }

    actual suspend fun scanIdleCompute(): Int = try {
        // Use runtime available processors and memory pressure as a proxy
        // for idle compute. Real CPU idle can be read from /proc/stat but
        // requires sequential reads with a delay.
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMem = memoryInfo.totalMem
        val availMem = memoryInfo.availMem

        // Approximate idle compute from available memory ratio
        // This is a simplified heuristic — more accurate CPU idle
        // requires sampling /proc/stat over time.
        val memoryIdlePercent =
            if (totalMem > 0) {
                ((availMem.toDouble() / totalMem.toDouble()) * 100).toInt()
            } else {
                0
            }
        memoryIdlePercent.coerceIn(0, 100)
    } catch (_: Exception) {
        0
    }

    actual suspend fun scanBatteryLevel(): Int = try {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 0..100) level else -1
    } catch (_: Exception) {
        -1
    }

    actual suspend fun scanIsCharging(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            batteryManager.isCharging
        } else {
            // Fallback for older APIs: read sticky broadcast
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }
    } catch (_: Exception) {
        false
    }

    actual suspend fun scanThermalState(): ThermalState = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT,
                -> ThermalState.COOL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.WARM
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.HOT
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN,
                -> ThermalState.CRITICAL
                else -> ThermalState.COOL
            }
        } else {
            // Thermal status API not available below API 29
            ThermalState.COOL
        }
    } catch (_: Exception) {
        ThermalState.COOL
    }
}
