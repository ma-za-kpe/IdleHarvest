package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AgentState
import kotlinx.serialization.Serializable

/**
 * Traffic-light health status for the system dashboard.
 *
 * - GREEN: All agents healthy and connected.
 * - YELLOW: Some agents paused or degraded, or unstable/offline connectivity.
 * - RED: Agents in error state, prolonged offline, or critical failures.
 */
@Serializable
enum class HealthStatus { GREEN, YELLOW, RED }

/**
 * Derives a system-wide health indicator (green/yellow/red) based on:
 * - Current agent states (idle, evaluating, executing, paused, error)
 * - Network connectivity status
 * - Recent critical errors from the rolling error log
 *
 * Displayed on the main dashboard to summarize agent and connectivity status.
 *
 * Validates: Requirements 16.6
 */
class SystemHealthIndicator(
    private val agentStates: () -> Map<AgentId, AgentState>,
    private val isOnline: () -> Boolean,
    private val errorLog: RollingErrorLog,
    private val clock: () -> Long = { currentTimeMillis() },
) {
    companion object {
        /** Time window for "recent" critical errors (last 1 hour). */
        private const val RECENT_WINDOW_MS: Long = 60L * 60L * 1000L

        /** Threshold: if this many or more agents are in ERROR state, status is RED. */
        private const val RED_ERROR_AGENT_THRESHOLD = 1

        /** Threshold: if this many recent critical log entries exist, status is RED. */
        private const val RED_CRITICAL_LOG_THRESHOLD = 3
    }

    /**
     * Compute the current system health status.
     *
     * Decision rules:
     * - RED if:
     *   - Any agent is in ERROR state, OR
     *   - Device is offline AND there are recent critical errors, OR
     *   - Number of recent critical log entries >= RED_CRITICAL_LOG_THRESHOLD
     * - YELLOW if:
     *   - Any agent is PAUSED, OR
     *   - Device is offline (but no critical errors), OR
     *   - There are any recent errors (non-critical)
     * - GREEN if:
     *   - All agents are in healthy states (IDLE, EVALUATING, EXECUTING), AND
     *   - Device is online, AND
     *   - No recent critical errors
     */
    fun computeHealth(): HealthStatus {
        val states = agentStates()
        val online = isOnline()
        val recentCutoff = clock() - RECENT_WINDOW_MS
        val recentCriticals =
            errorLog
                .getEntriesSince(recentCutoff)
                .count { it.severity == LogSeverity.CRITICAL }
        val recentErrors =
            errorLog
                .getEntriesSince(recentCutoff)
                .count { it.severity == LogSeverity.ERROR || it.severity == LogSeverity.CRITICAL }

        // --- RED conditions ---
        // Any agent in ERROR state
        val hasErrorAgents = states.values.any { it == AgentState.ERROR }
        if (hasErrorAgents) return HealthStatus.RED

        // Many recent critical log entries
        if (recentCriticals >= RED_CRITICAL_LOG_THRESHOLD) return HealthStatus.RED

        // Offline with recent criticals
        if (!online && recentCriticals > 0) return HealthStatus.RED

        // --- YELLOW conditions ---
        // Any agent is PAUSED
        val hasPausedAgents = states.values.any { it == AgentState.PAUSED }
        if (hasPausedAgents) return HealthStatus.YELLOW

        // Device is offline
        if (!online) return HealthStatus.YELLOW

        // Recent non-zero errors
        if (recentErrors > 0) return HealthStatus.YELLOW

        // --- GREEN: all healthy ---
        return HealthStatus.GREEN
    }
}
