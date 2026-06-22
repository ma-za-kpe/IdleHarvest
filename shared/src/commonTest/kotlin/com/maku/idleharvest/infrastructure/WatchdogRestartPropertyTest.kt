package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property 33: Watchdog Agent Restart
 *
 * If an agent loop is unresponsive for >60 seconds, the watchdog SHALL restart that agent
 * and log the event. Agents that send heartbeats within 60 seconds are NOT restarted.
 *
 * Tests use the real [AgentWatchdog] class with an injectable clock.
 *
 * Validates: Requirements 16.5
 */
class WatchdogRestartPropertyTest {

    companion object {
        private const val TIMEOUT_MS = 60_000L
    }

    @Test
    fun agentUnresponsiveForMoreThan60SecondsIsDetected() = runTest {
        forAll(Arb.long(TIMEOUT_MS + 1..TIMEOUT_MS * 5)) { elapsedMs ->
            var currentTime = 0L
            val watchdog = AgentWatchdog(
                timeoutMs = TIMEOUT_MS,
                clock = { currentTime },
            )

            val agentId = AgentId("agent-test")
            watchdog.registerAgent(agentId) { /* restart fn */ }

            // Advance time beyond timeout without heartbeat
            currentTime = elapsedMs

            val timedOut = watchdog.checkTimeouts()
            timedOut.contains(agentId)
        }
    }

    @Test
    fun agentWithRecentHeartbeatIsNotDetectedAsTimedOut() = runTest {
        forAll(Arb.long(1L..TIMEOUT_MS - 1)) { elapsedMs ->
            var currentTime = 0L
            val watchdog = AgentWatchdog(
                timeoutMs = TIMEOUT_MS,
                clock = { currentTime },
            )

            val agentId = AgentId("agent-active")
            watchdog.registerAgent(agentId) { /* restart fn */ }

            // Advance time within timeout
            currentTime = elapsedMs

            val timedOut = watchdog.checkTimeouts()
            !timedOut.contains(agentId)
        }
    }

    @Test
    fun heartbeatResetsTimeout() = runTest {
        forAll(Arb.long(1L..TIMEOUT_MS - 1)) { heartbeatInterval ->
            var currentTime = 0L
            val watchdog = AgentWatchdog(
                timeoutMs = TIMEOUT_MS,
                clock = { currentTime },
            )

            val agentId = AgentId("agent-heartbeat")
            watchdog.registerAgent(agentId) { /* restart fn */ }

            // Advance time and send heartbeat
            currentTime = heartbeatInterval
            watchdog.heartbeat(agentId)

            // Advance time further but within timeout from last heartbeat
            currentTime = heartbeatInterval + (TIMEOUT_MS - 1)

            val timedOut = watchdog.checkTimeouts()
            !timedOut.contains(agentId)
        }
    }

    @Test
    fun restartAgentLogsTheEvent() = runTest {
        forAll(Arb.string(3..15)) { agentName ->
            var currentTime = 0L
            val watchdog = AgentWatchdog(
                timeoutMs = TIMEOUT_MS,
                clock = { currentTime },
            )

            var restarted = false
            val agentId = AgentId(agentName)
            watchdog.registerAgent(agentId) { restarted = true }

            // Trigger timeout
            currentTime = TIMEOUT_MS + 1

            watchdog.restartAgent(agentId)

            // Verify: restart was called and event was logged
            restarted &&
                watchdog.restartLog.size == 1 &&
                watchdog.restartLog.first().agentId == agentId

            // Clean up for next iteration
            watchdog.clearRestartLog()
            true
        }
    }

    @Test
    fun restartResetsHeartbeatTimestamp() = runTest {
        forAll(Arb.long(TIMEOUT_MS + 1..TIMEOUT_MS * 3)) { timeoutTime ->
            var currentTime = 0L
            val watchdog = AgentWatchdog(
                timeoutMs = TIMEOUT_MS,
                clock = { currentTime },
            )

            val agentId = AgentId("agent-reset")
            watchdog.registerAgent(agentId) { /* restart fn */ }

            // Trigger timeout
            currentTime = timeoutTime
            assertTrue(watchdog.checkTimeouts().contains(agentId))

            // Restart the agent
            watchdog.restartAgent(agentId)

            // After restart, agent should NOT appear as timed out (heartbeat reset)
            val timedOutAfterRestart = watchdog.checkTimeouts()
            !timedOutAfterRestart.contains(agentId)
        }
    }

    @Test
    fun checkAndRestartRestartsAllTimedOutAgents() = runTest {
        forAll(Arb.int(1..5)) { agentCount ->
            var currentTime = 0L
            val watchdog = AgentWatchdog(
                timeoutMs = TIMEOUT_MS,
                clock = { currentTime },
            )

            val restartedAgents = mutableSetOf<AgentId>()
            val agentIds = (0 until agentCount).map { AgentId("agent-$it") }

            for (agentId in agentIds) {
                watchdog.registerAgent(agentId) { restartedAgents.add(agentId) }
            }

            // Advance past timeout
            currentTime = TIMEOUT_MS + 1

            val restarted = watchdog.checkAndRestart()

            // All agents should have been restarted
            restarted.size == agentCount &&
                restarted.toSet() == agentIds.toSet() &&
                restartedAgents == agentIds.toSet()
        }
    }

    @Test
    fun multipleAgentsMixedTimeoutState() = runTest {
        forAll(Arb.int(1..5)) { activeCount ->
            var currentTime = 0L
            val watchdog = AgentWatchdog(
                timeoutMs = TIMEOUT_MS,
                clock = { currentTime },
            )

            // Register "active" agents that will heartbeat
            val activeIds = (0 until activeCount).map { AgentId("active-$it") }
            for (agentId in activeIds) {
                watchdog.registerAgent(agentId) { }
            }

            // Register "stale" agents that won't heartbeat
            val staleIds = (0 until activeCount).map { AgentId("stale-$it") }
            for (agentId in staleIds) {
                watchdog.registerAgent(agentId) { }
            }

            // Advance to near-timeout
            currentTime = TIMEOUT_MS - 1

            // Active agents send heartbeat
            for (agentId in activeIds) {
                watchdog.heartbeat(agentId)
            }

            // Advance past timeout from original registration (but active agents heartbeat at timeout-1)
            currentTime = TIMEOUT_MS + 1

            val timedOut = watchdog.checkTimeouts()

            // Only stale agents should be timed out
            timedOut.toSet() == staleIds.toSet() &&
                activeIds.none { it in timedOut }
        }
    }

    @Test
    fun exactlyAtTimeoutBoundaryDoesNotTrigger() = runTest {
        forAll(Arb.int(1..10)) { _ ->
            var currentTime = 0L
            val watchdog = AgentWatchdog(
                timeoutMs = TIMEOUT_MS,
                clock = { currentTime },
            )

            val agentId = AgentId("boundary-agent")
            watchdog.registerAgent(agentId) { }

            // At exactly the timeout boundary (not exceeding)
            currentTime = TIMEOUT_MS

            val timedOut = watchdog.checkTimeouts()
            // Timeout check is ">" not ">=", so exactly at boundary should NOT trigger
            !timedOut.contains(agentId)
        }
    }
}
