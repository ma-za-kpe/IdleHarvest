package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AgentState
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 34: System Health Indicator Derivation
 *
 * System health indicator derivation is correct based on agent states, connectivity,
 * and errors. The indicator is GREEN when all agents are healthy and online with no
 * errors, YELLOW when some agents are paused or connectivity is unstable, and RED
 * when agents are in error state or critical failures exist.
 *
 * Tests use the real [SystemHealthIndicator] class.
 *
 * Validates: Requirements 16.6
 */
class SystemHealthPropertyTest {
    companion object {
        private const val NOW = 10_000_000L
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
    }

    private fun createIndicator(
        agentStates: Map<AgentId, AgentState>,
        isOnline: Boolean,
        errorLog: RollingErrorLog,
    ): SystemHealthIndicator = SystemHealthIndicator(
        agentStates = { agentStates },
        isOnline = { isOnline },
        errorLog = errorLog,
        clock = { NOW },
    )

    private fun createErrorLog(): RollingErrorLog = RollingErrorLog(maxAgeDays = 7, clock = { NOW })

    private fun addRecentError(
        log: RollingErrorLog,
        severity: LogSeverity,
        minutesAgo: Int = 30,
    ) {
        log.log(
            ErrorLogEntry(
                id = "err-$minutesAgo-${severity.name}",
                agentId = AgentId("test-agent"),
                severity = severity,
                message = "Test error: ${severity.name}",
                timestamp = NOW - (minutesAgo * 60_000L),
                stackTrace = null,
            ),
        )
    }

    @Test
    fun greenWhenAllAgentsHealthyAndOnlineWithNoErrors() = runTest {
        val healthyStates = listOf(AgentState.IDLE, AgentState.EVALUATING, AgentState.EXECUTING)
        val stateArb = Arb.of(healthyStates)

        forAll(Arb.int(1..5), stateArb) { agentCount, state ->
            val log = createErrorLog()
            val states = (0 until agentCount).associate { AgentId("agent-$it") to state }

            val indicator =
                createIndicator(
                    agentStates = states,
                    isOnline = true,
                    errorLog = log,
                )

            indicator.computeHealth() == HealthStatus.GREEN
        }
    }

    @Test
    fun redWhenAnyAgentIsInErrorState() = runTest {
        val otherStates = listOf(AgentState.IDLE, AgentState.EVALUATING, AgentState.EXECUTING, AgentState.PAUSED)
        val otherStateArb = Arb.of(otherStates)

        forAll(Arb.int(1..4), otherStateArb) { healthyCount, otherState ->
            val log = createErrorLog()
            val states = mutableMapOf<AgentId, AgentState>()

            // Add healthy agents
            repeat(healthyCount) { i ->
                states[AgentId("healthy-$i")] = otherState
            }
            // Add one ERROR agent
            states[AgentId("error-agent")] = AgentState.ERROR

            val indicator =
                createIndicator(
                    agentStates = states,
                    isOnline = true,
                    errorLog = log,
                )

            indicator.computeHealth() == HealthStatus.RED
        }
    }

    @Test
    fun yellowWhenAnyAgentIsPausedButNoneInError() = runTest {
        val healthyStates = listOf(AgentState.IDLE, AgentState.EVALUATING, AgentState.EXECUTING)
        val healthyStateArb = Arb.of(healthyStates)

        forAll(Arb.int(0..4), healthyStateArb) { healthyCount, healthyState ->
            val log = createErrorLog()
            val states = mutableMapOf<AgentId, AgentState>()

            repeat(healthyCount) { i ->
                states[AgentId("healthy-$i")] = healthyState
            }
            // Add one PAUSED agent
            states[AgentId("paused-agent")] = AgentState.PAUSED

            val indicator =
                createIndicator(
                    agentStates = states,
                    isOnline = true,
                    errorLog = log,
                )

            indicator.computeHealth() == HealthStatus.YELLOW
        }
    }

    @Test
    fun yellowWhenOfflineWithNoCriticalErrors() = runTest {
        val healthyStates = listOf(AgentState.IDLE, AgentState.EVALUATING, AgentState.EXECUTING)
        val stateArb = Arb.of(healthyStates)

        forAll(Arb.int(1..5), stateArb) { agentCount, state ->
            val log = createErrorLog()
            val states = (0 until agentCount).associate { AgentId("agent-$it") to state }

            val indicator =
                createIndicator(
                    agentStates = states,
                    isOnline = false, // Offline
                    errorLog = log, // No errors
                )

            indicator.computeHealth() == HealthStatus.YELLOW
        }
    }

    @Test
    fun redWhenOfflineWithRecentCriticalErrors() = runTest {
        val healthyStates = listOf(AgentState.IDLE, AgentState.EVALUATING, AgentState.EXECUTING)
        val stateArb = Arb.of(healthyStates)

        forAll(Arb.int(1..5), stateArb) { agentCount, state ->
            val log = createErrorLog()
            // Add a recent critical error
            addRecentError(log, LogSeverity.CRITICAL, minutesAgo = 10)

            val states = (0 until agentCount).associate { AgentId("agent-$it") to state }

            val indicator =
                createIndicator(
                    agentStates = states,
                    isOnline = false, // Offline + critical errors = RED
                    errorLog = log,
                )

            indicator.computeHealth() == HealthStatus.RED
        }
    }

    @Test
    fun redWhenThreeOrMoreRecentCriticalErrors() = runTest {
        forAll(Arb.int(3..10)) { criticalCount ->
            val log = createErrorLog()

            // Add multiple recent critical errors
            repeat(criticalCount) { i ->
                log.log(
                    ErrorLogEntry(
                        id = "critical-$i",
                        agentId = AgentId("agent-$i"),
                        severity = LogSeverity.CRITICAL,
                        message = "Critical failure $i",
                        timestamp = NOW - ((i + 1) * 60_000L), // Within the last hour
                        stackTrace = null,
                    ),
                )
            }

            val states = mapOf(AgentId("agent-0") to AgentState.IDLE)
            val indicator =
                createIndicator(
                    agentStates = states,
                    isOnline = true,
                    errorLog = log,
                )

            indicator.computeHealth() == HealthStatus.RED
        }
    }

    @Test
    fun yellowWhenRecentNonCriticalErrorsExist() = runTest {
        val errorSeverities = listOf(LogSeverity.ERROR)
        val severityArb = Arb.of(errorSeverities)

        forAll(Arb.int(1..2), severityArb) { errorCount, severity ->
            val log = createErrorLog()

            repeat(errorCount) { i ->
                log.log(
                    ErrorLogEntry(
                        id = "error-$i",
                        agentId = AgentId("agent-0"),
                        severity = severity,
                        message = "Non-critical error $i",
                        timestamp = NOW - ((i + 1) * 60_000L),
                        stackTrace = null,
                    ),
                )
            }

            val states = mapOf(AgentId("agent-0") to AgentState.IDLE)
            val indicator =
                createIndicator(
                    agentStates = states,
                    isOnline = true,
                    errorLog = log,
                )

            indicator.computeHealth() == HealthStatus.YELLOW
        }
    }

    @Test
    fun errorAgentStateAlwaysTakesPrecedence() = runTest {
        val onlineArb = Arb.of(true, false)

        forAll(onlineArb) { isOnline ->
            val log = createErrorLog()
            val states =
                mapOf(
                    AgentId("agent-0") to AgentState.ERROR,
                    AgentId("agent-1") to AgentState.IDLE,
                )

            val indicator =
                createIndicator(
                    agentStates = states,
                    isOnline = isOnline,
                    errorLog = log,
                )

            // ERROR agent state always results in RED regardless of other conditions
            indicator.computeHealth() == HealthStatus.RED
        }
    }

    @Test
    fun emptyAgentStatesWithOnlineAndNoErrorsIsGreen() = runTest {
        forAll(Arb.int(1..10)) { _ ->
            val log = createErrorLog()
            val indicator =
                createIndicator(
                    agentStates = emptyMap(),
                    isOnline = true,
                    errorLog = log,
                )

            indicator.computeHealth() == HealthStatus.GREEN
        }
    }
}
