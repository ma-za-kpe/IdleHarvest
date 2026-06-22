package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId

/**
 * Watchdog timer that monitors agent loop responsiveness.
 *
 * Key behaviors:
 * - **Registration**: Agents register with a heartbeat function used to restart them.
 * - **Heartbeat**: Agents call [heartbeat] each loop iteration to report liveness.
 * - **Timeout detection**: If an agent's last heartbeat is older than [timeoutMs], it is
 *   considered unresponsive.
 * - **Restart**: Unresponsive agents are restarted via their registered heartbeat function
 *   and the restart event is logged.
 * - **60-second default**: The timeout is 60 seconds per requirement 16.5.
 *
 * Validates: Requirements 16.5
 */
class AgentWatchdog(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long = { currentTimeMillis() },
) {
    /**
     * Registered agents with their restart functions.
     */
    private val registeredAgents = mutableMapOf<AgentId, AgentEntry>()

    /**
     * Log of restart events for diagnostic purposes.
     */
    private val _restartLog = mutableListOf<RestartEvent>()

    /**
     * Read-only list of all restart events.
     */
    val restartLog: List<RestartEvent> get() = _restartLog.toList()

    /**
     * Register an agent for watchdog monitoring.
     *
     * @param agentId Unique identifier for the agent.
     * @param restartFn Suspend function to call when the agent needs to be restarted.
     */
    fun registerAgent(agentId: AgentId, restartFn: suspend () -> Unit) {
        registeredAgents[agentId] = AgentEntry(
            agentId = agentId,
            restartFn = restartFn,
            lastHeartbeat = clock(),
        )
    }

    /**
     * Unregister an agent from watchdog monitoring.
     */
    fun unregisterAgent(agentId: AgentId) {
        registeredAgents.remove(agentId)
    }

    /**
     * Report that an agent is alive. Call this from the agent's main loop on each iteration.
     */
    fun heartbeat(agentId: AgentId) {
        registeredAgents[agentId]?.let { entry ->
            registeredAgents[agentId] = entry.copy(lastHeartbeat = clock())
        }
    }

    /**
     * Check all registered agents for timeout. Returns the list of agent IDs that
     * have exceeded the timeout threshold and are considered unresponsive.
     */
    fun checkTimeouts(): List<AgentId> {
        val now = clock()
        return registeredAgents.values
            .filter { entry -> (now - entry.lastHeartbeat) > timeoutMs }
            .map { it.agentId }
    }

    /**
     * Restart a specific agent by invoking its registered restart function.
     * Logs the restart event and resets the agent's heartbeat timestamp.
     */
    suspend fun restartAgent(agentId: AgentId) {
        val entry = registeredAgents[agentId] ?: return

        val restartEvent = RestartEvent(
            agentId = agentId,
            timestamp = clock(),
            reason = "Agent unresponsive for >${timeoutMs}ms",
            lastHeartbeat = entry.lastHeartbeat,
        )
        _restartLog.add(restartEvent)

        // Invoke the restart function
        entry.restartFn()

        // Reset the heartbeat after restart
        registeredAgents[agentId] = entry.copy(lastHeartbeat = clock())
    }

    /**
     * Check all agents for timeouts and restart any that are unresponsive.
     * Returns the list of agents that were restarted.
     */
    suspend fun checkAndRestart(): List<AgentId> {
        val timedOut = checkTimeouts()
        for (agentId in timedOut) {
            restartAgent(agentId)
        }
        return timedOut
    }

    /**
     * Get the number of registered agents.
     */
    fun registeredCount(): Int = registeredAgents.size

    /**
     * Check whether a specific agent is registered.
     */
    fun isRegistered(agentId: AgentId): Boolean = agentId in registeredAgents

    /**
     * Get the timestamp of the last heartbeat for a specific agent, or null if not registered.
     */
    fun lastHeartbeatFor(agentId: AgentId): Long? = registeredAgents[agentId]?.lastHeartbeat

    /**
     * Clear the restart log.
     */
    fun clearRestartLog() {
        _restartLog.clear()
    }

    companion object {
        /** Default watchdog timeout: 60 seconds. */
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}

/**
 * Internal tracking entry for a registered agent.
 */
internal data class AgentEntry(
    val agentId: AgentId,
    val restartFn: suspend () -> Unit,
    val lastHeartbeat: Long,
)

/**
 * Record of a watchdog-triggered agent restart.
 */
data class RestartEvent(
    /** The agent that was restarted. */
    val agentId: AgentId,
    /** Timestamp when the restart was triggered. */
    val timestamp: Long,
    /** Reason for the restart. */
    val reason: String,
    /** The agent's last heartbeat timestamp before the restart. */
    val lastHeartbeat: Long,
)
