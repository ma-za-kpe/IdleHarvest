package com.maku.idleharvest.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.maku.idleharvest.MainActivity
import com.maku.idleharvest.R
import com.maku.idleharvest.domain.models.MonitorConfig
import com.maku.idleharvest.domain.models.ThermalState
import com.maku.idleharvest.infrastructure.PlatformResourceScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the Resource Monitor running persistently.
 *
 * Provides:
 * - Persistent foreground notification for background execution (Requirement 10.1)
 * - Battery threshold monitoring with activity reduction (Requirement 10.3)
 * - Charger-connected resume for full operation (Requirement 10.4)
 * - Doze mode adaptation via reduced scan frequency (Requirement 10.7)
 * - Thermal throttling adaptation (Requirement 10.6)
 *
 * If the OS kills this service, [ResourceMonitorWorker] will restart it.
 */
class ResourceMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null

    private lateinit var scanner: PlatformResourceScanner
    private var config: MonitorConfig = MonitorConfig()
    private var currentThermalState: ThermalState = ThermalState.COOL
    private var isLowBatteryMode: Boolean = false

    override fun onCreate() {
        super.onCreate()
        scanner = PlatformResourceScanner(applicationContext)
        createNotificationChannel()
        registerThermalListener()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val notification = buildNotification("Monitoring idle resources...")
        startForeground(NOTIFICATION_ID, notification)

        startMonitoringLoop()

        // If the system kills the service, request restart
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitoringJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Starts the periodic resource scanning loop.
     * Adapts scan frequency based on battery level and thermal state.
     */
    private fun startMonitoringLoop() {
        monitoringJob?.cancel()
        monitoringJob =
            serviceScope.launch {
                while (isActive) {
                    try {
                        performScan()
                    } catch (e: Exception) {
                        Log.w(TAG, "Resource scan failed: ${e.message}")
                    }

                    val interval = computeEffectiveInterval()
                    delay(interval)
                }
            }
    }

    /**
     * Performs a single resource scan and checks battery/thermal thresholds.
     */
    private suspend fun performScan() {
        val batteryLevel = scanner.scanBatteryLevel()
        val isCharging = scanner.scanIsCharging()
        val thermalState = scanner.scanThermalState()

        // Update thermal state
        currentThermalState = thermalState

        // Battery threshold check (Requirement 10.3)
        val wasLowBattery = isLowBatteryMode
        isLowBatteryMode = !isCharging && batteryLevel in 0 until config.lowBatteryThreshold

        // Resume full operation when charger connected (Requirement 10.4)
        if (wasLowBattery && isCharging) {
            isLowBatteryMode = false
            updateNotification("Charging — full monitoring active")
        } else if (isLowBatteryMode) {
            updateNotification("Low battery — reduced monitoring")
        } else if (thermalState == ThermalState.CRITICAL) {
            updateNotification("Device hot — monitoring paused")
        } else {
            updateNotification("Monitoring idle resources...")
        }

        // Pause scanning entirely when thermal state is critical
        if (thermalState == ThermalState.CRITICAL) {
            monitoringJob?.cancel()
            monitoringJob = null
        }
    }

    /**
     * Computes the effective scan interval considering:
     * - Base configured interval (default 15 minutes)
     * - Thermal state multiplier (warm = 1.5x, hot = 3x)
     * - Low battery multiplier (2x when below threshold)
     * - Doze mode awareness (interval already long enough to work with setAndAllowWhileIdle)
     */
    private fun computeEffectiveInterval(): Long {
        val baseInterval = config.scanIntervalMs

        val thermalMultiplier =
            when (currentThermalState) {
                ThermalState.COOL -> 1.0
                ThermalState.WARM -> 1.5
                ThermalState.HOT -> 3.0
                ThermalState.CRITICAL -> return Long.MAX_VALUE
            }

        val batteryMultiplier = if (isLowBatteryMode) 2.0 else 1.0

        return (baseInterval * thermalMultiplier * batteryMultiplier).toLong()
    }

    /**
     * Registers a thermal status listener (API 29+) to react to thermal changes.
     */
    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.addThermalStatusListener { status ->
                currentThermalState =
                    when (status) {
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

                // If critical, pause monitoring immediately
                if (currentThermalState == ThermalState.CRITICAL) {
                    monitoringJob?.cancel()
                    monitoringJob = null
                    updateNotification("Device hot — monitoring paused")
                } else if (monitoringJob == null || monitoringJob?.isActive != true) {
                    // Resume monitoring after recovering from critical
                    startMonitoringLoop()
                    updateNotification("Monitoring idle resources...")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Resource Monitor",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Persistent notification for idle resource monitoring"
                    setShowBadge(false)
                }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("IdleHarvest")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "ResourceMonitorService"
        private const val CHANNEL_ID = "idle_harvest_monitor"
        private const val NOTIFICATION_ID = 1001

        /**
         * Starts the foreground service.
         */
        fun start(context: Context) {
            val intent = Intent(context, ResourceMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the foreground service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ResourceMonitorService::class.java)
            context.stopService(intent)
        }
    }
}
