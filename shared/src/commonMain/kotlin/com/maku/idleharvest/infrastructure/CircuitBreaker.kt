package com.maku.idleharvest.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Circuit Breaker pattern implementation for wrapping external service calls
 * (VTU platforms, DePIN networks, Circle Agent Stack).
 *
 * State machine: Closed → Open → HalfOpen
 * - **Closed**: All calls pass through normally. Tracks consecutive failures.
 * - **Open**: After [failureThreshold] consecutive failures, rejects all calls for the
 *   configured [backoffPeriodMs]. Returns [CircuitBreakerOpenException] immediately.
 * - **HalfOpen**: After the backoff period expires, allows one test call through.
 *   - Success → returns to Closed (resets failure count)
 *   - Failure → returns to Open (restarts backoff timer)
 *
 * Thread-safety: Uses [MutableStateFlow] for atomic state transitions. The consecutive
 * failure counter is accessed only within the `execute` suspend function, which is safe
 * for single-caller patterns. For multi-caller scenarios, external synchronization or
 * a Mutex should be used.
 *
 * Validates: Requirements 16.1
 */
class CircuitBreaker(
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val backoffPeriodMs: Long = DEFAULT_BACKOFF_PERIOD_MS,
    private val clock: () -> Long = { currentTimeMillis() },
) {
    /**
     * Represents the three states of the circuit breaker.
     */
    sealed class State {
        /** Circuit is healthy — all calls pass through. */
        data object Closed : State()

        /**
         * Circuit is tripped — all calls are rejected until the backoff period expires.
         * @param openedAt timestamp when the circuit was opened
         * @param backoffMs configured backoff duration in milliseconds
         */
        data class Open(
            val openedAt: Long,
            val backoffMs: Long,
        ) : State()

        /** Circuit is testing — one call is allowed through to probe service health. */
        data object HalfOpen : State()
    }

    private val _state = MutableStateFlow<State>(State.Closed)

    /** Observable state of the circuit breaker. */
    val state: StateFlow<State> = _state.asStateFlow()

    /** Consecutive failure counter in the Closed state. */
    private var consecutiveFailures = 0

    /**
     * Execute a block of code through the circuit breaker.
     *
     * - In **Closed** state: executes the block. On success, resets the failure counter.
     *   On failure, increments the counter and transitions to Open if threshold is reached.
     * - In **Open** state: if the backoff period has elapsed, transitions to HalfOpen and
     *   executes a probe call. Otherwise, immediately returns failure with
     *   [CircuitBreakerOpenException].
     * - In **HalfOpen** state: executes the block as a probe. On success, transitions to
     *   Closed. On failure, transitions back to Open.
     *
     * @param block the suspend function wrapping the external service call
     * @return [Result.success] with the value if the call succeeds, or [Result.failure]
     *         with the exception if the call fails or the circuit is open.
     */
    suspend fun <T> execute(block: suspend () -> T): Result<T> = when (val currentState = _state.value) {
        is State.Closed -> executeClosed(block)
        is State.Open -> executeOpen(currentState, block)
        is State.HalfOpen -> executeHalfOpen(block)
    }

    /**
     * Manually reset the circuit breaker to Closed state.
     * Useful for administrative overrides or testing.
     */
    fun reset() {
        consecutiveFailures = 0
        _state.value = State.Closed
    }

    // --- Private execution helpers ---

    /**
     * Execute in Closed state: track consecutive failures, trip to Open on threshold.
     */
    private suspend fun <T> executeClosed(block: suspend () -> T): Result<T> = try {
        val result = block()
        consecutiveFailures = 0
        Result.success(result)
    } catch (e: Exception) {
        consecutiveFailures++
        if (consecutiveFailures >= failureThreshold) {
            _state.value =
                State.Open(
                    openedAt = clock(),
                    backoffMs = backoffPeriodMs,
                )
        }
        Result.failure(e)
    }

    /**
     * Execute in Open state: check if backoff has elapsed. If yes, transition to HalfOpen
     * and probe. If no, reject immediately.
     */
    private suspend fun <T> executeOpen(
        openState: State.Open,
        block: suspend () -> T,
    ): Result<T> {
        val elapsed = clock() - openState.openedAt
        return if (elapsed >= openState.backoffMs) {
            _state.value = State.HalfOpen
            executeHalfOpen(block)
        } else {
            Result.failure(
                CircuitBreakerOpenException(
                    "Circuit breaker is OPEN. Remaining backoff: ${openState.backoffMs - elapsed}ms",
                ),
            )
        }
    }

    /**
     * Execute in HalfOpen state: allow one probe call. On success → Closed. On failure → Open.
     */
    private suspend fun <T> executeHalfOpen(block: suspend () -> T): Result<T> = try {
        val result = block()
        // Probe succeeded — circuit recovers
        consecutiveFailures = 0
        _state.value = State.Closed
        Result.success(result)
    } catch (e: Exception) {
        // Probe failed — circuit remains open with fresh backoff timer
        _state.value =
            State.Open(
                openedAt = clock(),
                backoffMs = backoffPeriodMs,
            )
        Result.failure(e)
    }

    companion object {
        /** Default number of consecutive failures before the circuit opens. */
        const val DEFAULT_FAILURE_THRESHOLD = 5

        /** Default backoff period in milliseconds (60 seconds). */
        const val DEFAULT_BACKOFF_PERIOD_MS = 60_000L
    }
}

/**
 * Exception thrown when a call is attempted while the circuit breaker is in the Open state.
 */
class CircuitBreakerOpenException(
    message: String,
) : Exception(message)
