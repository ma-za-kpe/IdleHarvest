package com.maku.idleharvest.infrastructure

import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Property 30: Circuit Breaker State Machine
 *
 * For any sequence of success and failure calls, the circuit breaker SHALL transition
 * to OPEN after exactly 5 consecutive failures, remain OPEN for the backoff period,
 * transition to HALF_OPEN, and return to CLOSED on success or OPEN on failure.
 *
 * Tests use the real [CircuitBreaker] class.
 *
 * Validates: Requirements 16.1
 */
class CircuitBreakerPropertyTest {
    @Test
    fun transitionsToOpenAfterExactlyFiveConsecutiveFailures() = runTest {
        forAll(Arb.int(1..10)) { _ ->
            val cb = CircuitBreaker(failureThreshold = 5, backoffPeriodMs = 60_000L)

            // First 4 failures should keep it CLOSED
            repeat(4) {
                cb.execute<Unit> { throw RuntimeException("fail") }
            }
            val stateAfter4 = cb.state.value
            val isClosed = stateAfter4 is CircuitBreaker.State.Closed

            // 5th failure should transition to OPEN
            cb.execute<Unit> { throw RuntimeException("fail") }
            val stateAfter5 = cb.state.value
            val isOpen = stateAfter5 is CircuitBreaker.State.Open

            isClosed && isOpen
        }
    }

    @Test
    fun rejectsCallsWhileOpen() = runTest {
        var currentTime = 0L
        val cb =
            CircuitBreaker(
                failureThreshold = 5,
                backoffPeriodMs = 60_000L,
                clock = { currentTime },
            )

        // Trip the circuit
        repeat(5) {
            cb.execute<Unit> { throw RuntimeException("fail") }
        }

        forAll(Arb.long(1L..59_999L)) { elapsed ->
            currentTime = elapsed

            val result = cb.execute { "should not execute" }

            result.isFailure && result.exceptionOrNull() is CircuitBreakerOpenException
        }
    }

    @Test
    fun transitionsToHalfOpenAfterBackoffPeriod() = runTest {
        forAll(Arb.long(60_000L..120_000L)) { backoffElapsed ->
            var currentTime = 0L
            val cb =
                CircuitBreaker(
                    failureThreshold = 5,
                    backoffPeriodMs = 60_000L,
                    clock = { currentTime },
                )

            // Trip the circuit
            repeat(5) {
                cb.execute<Unit> { throw RuntimeException("fail") }
            }
            assertIs<CircuitBreaker.State.Open>(cb.state.value)

            // Advance time past backoff
            currentTime = backoffElapsed

            // Next call should transition to HALF_OPEN and execute the probe
            cb.execute { "probe" }

            // If probe succeeded, it should go to Closed
            cb.state.value is CircuitBreaker.State.Closed
        }
    }

    @Test
    fun halfOpenSuccessReturnsToClosed() = runTest {
        forAll(Arb.int(1..10)) { _ ->
            var currentTime = 0L
            val cb =
                CircuitBreaker(
                    failureThreshold = 5,
                    backoffPeriodMs = 60_000L,
                    clock = { currentTime },
                )

            // Trip the circuit
            repeat(5) {
                cb.execute<Unit> { throw RuntimeException("fail") }
            }

            // Advance past backoff to allow transition to HalfOpen
            currentTime = 60_001L

            // Successful probe should return to CLOSED
            val result = cb.execute { "success" }

            result.isSuccess && cb.state.value is CircuitBreaker.State.Closed
        }
    }

    @Test
    fun halfOpenFailureReturnsToOpen() = runTest {
        forAll(Arb.int(1..10)) { _ ->
            var currentTime = 0L
            val cb =
                CircuitBreaker(
                    failureThreshold = 5,
                    backoffPeriodMs = 60_000L,
                    clock = { currentTime },
                )

            // Trip the circuit
            repeat(5) {
                cb.execute<Unit> { throw RuntimeException("fail") }
            }

            // Advance past backoff
            currentTime = 60_001L

            // Failed probe should return to OPEN
            val result = cb.execute<Unit> { throw RuntimeException("probe failed") }

            result.isFailure && cb.state.value is CircuitBreaker.State.Open
        }
    }

    @Test
    fun successResetsConsecutiveFailureCount() = runTest {
        forAll(Arb.int(1..4)) { failureCount ->
            val cb = CircuitBreaker(failureThreshold = 5, backoffPeriodMs = 60_000L)

            // Accumulate some failures (less than threshold)
            repeat(failureCount) {
                cb.execute<Unit> { throw RuntimeException("fail") }
            }

            // A success should reset the counter
            cb.execute { "success" }

            // Now we should need 5 more consecutive failures to trip
            repeat(4) {
                cb.execute<Unit> { throw RuntimeException("fail") }
            }
            val stillClosed = cb.state.value is CircuitBreaker.State.Closed

            // 5th failure after reset trips it
            cb.execute<Unit> { throw RuntimeException("fail") }
            val nowOpen = cb.state.value is CircuitBreaker.State.Open

            stillClosed && nowOpen
        }
    }

    @Test
    fun resetManuallyClearsState() = runTest {
        forAll(Arb.int(1..5)) { failures ->
            val cb = CircuitBreaker(failureThreshold = 5, backoffPeriodMs = 60_000L)

            repeat(failures) {
                cb.execute<Unit> { throw RuntimeException("fail") }
            }

            cb.reset()

            // After reset, state should be Closed regardless of prior state
            cb.state.value is CircuitBreaker.State.Closed
        }
    }

    @Test
    fun arbitrarySequenceMaintainsCorrectState() = runTest {
        forAll(Arb.list(Arb.boolean(), 1..20)) { callResults ->
            var currentTime = 0L
            val cb =
                CircuitBreaker(
                    failureThreshold = 5,
                    backoffPeriodMs = 60_000L,
                    clock = { currentTime },
                )

            var consecutiveFailures = 0
            var isOpen = false

            for (succeeds in callResults) {
                if (isOpen) {
                    // Advance past backoff to allow HalfOpen probe
                    currentTime += 60_001L
                }

                if (succeeds) {
                    cb.execute { "ok" }
                    consecutiveFailures = 0
                    isOpen = false
                } else {
                    cb.execute<Unit> { throw RuntimeException("fail") }
                    if (isOpen) {
                        // Failed probe in HalfOpen → back to Open
                        isOpen = true
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= 5) {
                            isOpen = true
                            consecutiveFailures = 0
                        }
                    }
                }
            }

            // Validate final state matches expectations
            val state = cb.state.value
            if (isOpen) {
                state is CircuitBreaker.State.Open
            } else {
                state is CircuitBreaker.State.Closed
            }
        }
    }
}
