package com.maku.idleharvest.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker that acts as a backup restart mechanism.
 *
 * If the OS kills the [ResourceMonitorService] foreground service, this worker
 * periodically checks whether the service is running and restarts it if needed.
 *
 * Validates: Requirement 10.5 — "IF the operating system terminates the background
 * service, THEN THE Resource_Monitor SHALL reschedule restart using WorkManager (Android)"
 */
class ResourceMonitorWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {
    override fun doWork(): Result = try {
        Log.d(TAG, "WorkManager heartbeat — ensuring ResourceMonitorService is running")
        ResourceMonitorService.start(context)
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to restart ResourceMonitorService: ${e.message}")
        Result.retry()
    }

    companion object {
        private const val TAG = "ResourceMonitorWorker"
        private const val WORK_NAME = "idle_harvest_monitor_restart"

        /**
         * Minimum periodic work interval allowed by WorkManager (15 minutes).
         * This aligns with the default scan interval.
         */
        private const val REPEAT_INTERVAL_MINUTES = 15L

        /**
         * Enqueues the periodic work request as a backup for the foreground service.
         * Uses [ExistingPeriodicWorkPolicy.KEEP] to avoid replacing an already-running worker.
         */
        fun enqueue(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<ResourceMonitorWorker>(
                    REPEAT_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
            Log.d(TAG, "Enqueued periodic WorkManager heartbeat")
        }

        /**
         * Cancels the periodic work request. Called when monitoring is explicitly stopped.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic WorkManager heartbeat")
        }
    }
}
