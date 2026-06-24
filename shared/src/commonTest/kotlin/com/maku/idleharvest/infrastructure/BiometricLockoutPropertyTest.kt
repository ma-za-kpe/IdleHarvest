package com.maku.idleharvest.infrastructure

import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 27: Biometric Lockout After Consecutive Failures
 *
 * For any sequence of biometric authentication attempts, if 3 consecutive attempts fail,
 * signing operations SHALL be locked for the configured cooldown period. A successful
 * attempt at any point SHALL reset the consecutive failure counter.
 *
 * Tested using a fake keystore model that tracks failure count and lockout state.
 *
 * Validates: Requirements 13.4, 13.5
 */
class BiometricLockoutPropertyTest {
    companion object {
        const val MAX_CONSECUTIVE_FAILURES = 3
        const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds cooldown
    }

    /**
     * Fake keystore that implements biometric lockout behavior:
     * - Tracks consecutive biometric failures
     * - Locks signing after 3 consecutive failures
     * - Resets counter on success
     * - Unlocks after cooldown period
     */
    private class FakeBiometricKeystore(
        private var currentTime: Long = 0L,
    ) {
        var consecutiveFailures: Int = 0
            private set
        var isLocked: Boolean = false
            private set
        var lockoutStartTime: Long = 0L
            private set
        var totalRestarts: Int = 0
            private set

        fun advanceTime(ms: Long) {
            currentTime += ms
        }

        /**
         * Attempt biometric authentication.
         * @param success Whether this attempt succeeds.
         * @return true if authentication passed, false if failed or locked out.
         */
        fun attemptBiometric(success: Boolean): BiometricResult {
            // Check if lockout has expired
            if (isLocked) {
                val elapsed = currentTime - lockoutStartTime
                if (elapsed >= LOCKOUT_DURATION_MS) {
                    isLocked = false
                    consecutiveFailures = 0
                } else {
                    return BiometricResult.LOCKED_OUT
                }
            }

            return if (success) {
                consecutiveFailures = 0
                BiometricResult.SUCCESS
            } else {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    isLocked = true
                    lockoutStartTime = currentTime
                    totalRestarts++
                    BiometricResult.LOCKED_OUT
                } else {
                    BiometricResult.FAILED
                }
            }
        }

        /**
         * Attempt to sign data. Fails if locked out.
         */
        fun sign(data: ByteArray): Result<ByteArray> {
            // Check if lockout has expired
            if (isLocked) {
                val elapsed = currentTime - lockoutStartTime
                if (elapsed >= LOCKOUT_DURATION_MS) {
                    isLocked = false
                    consecutiveFailures = 0
                } else {
                    return Result.failure(
                        IllegalStateException("Signing locked due to biometric failures"),
                    )
                }
            }
            return Result.success(data) // Simplified signing
        }

        enum class BiometricResult { SUCCESS, FAILED, LOCKED_OUT }
    }

    @Test
    fun threeConsecutiveFailuresLocksSigningOperations() = runTest {
        forAll(Arb.long(0L..1_000_000L)) { startTime ->
            val keystore = FakeBiometricKeystore(currentTime = startTime)

            // 3 consecutive failures must trigger lockout
            keystore.attemptBiometric(success = false)
            keystore.attemptBiometric(success = false)
            val result = keystore.attemptBiometric(success = false)

            result == FakeBiometricKeystore.BiometricResult.LOCKED_OUT &&
                keystore.isLocked &&
                keystore.sign(byteArrayOf(1, 2, 3)).isFailure
        }
    }

    @Test
    fun successfulAttemptResetsFailureCounter() = runTest {
        forAll(Arb.int(1..2)) { failuresBefore ->
            val keystore = FakeBiometricKeystore()

            // Accumulate some failures (less than threshold)
            repeat(failuresBefore) {
                keystore.attemptBiometric(success = false)
            }

            // Successful attempt should reset counter
            val result = keystore.attemptBiometric(success = true)

            result == FakeBiometricKeystore.BiometricResult.SUCCESS &&
                keystore.consecutiveFailures == 0 &&
                !keystore.isLocked
        }
    }

    @Test
    fun fewerThanThreeFailuresDoesNotLock() = runTest {
        forAll(Arb.int(1..2)) { failures ->
            val keystore = FakeBiometricKeystore()

            repeat(failures) {
                keystore.attemptBiometric(success = false)
            }

            // Should NOT be locked with fewer than 3 consecutive failures
            !keystore.isLocked && keystore.sign(byteArrayOf(1)).isSuccess
        }
    }

    @Test
    fun lockoutExpiresAfterCooldownPeriod() = runTest {
        forAll(Arb.long(LOCKOUT_DURATION_MS..LOCKOUT_DURATION_MS * 5)) { waitTime ->
            val keystore = FakeBiometricKeystore()

            // Trigger lockout
            repeat(MAX_CONSECUTIVE_FAILURES) {
                keystore.attemptBiometric(success = false)
            }
            assertTrue(keystore.isLocked)

            // Advance past cooldown
            keystore.advanceTime(waitTime)

            // Signing should now be available again
            keystore.sign(byteArrayOf(1, 2, 3)).isSuccess
        }
    }

    @Test
    fun lockoutDoesNotExpireBeforeCooldownPeriod() = runTest {
        forAll(Arb.long(1L..LOCKOUT_DURATION_MS - 1)) { waitTime ->
            val keystore = FakeBiometricKeystore()

            // Trigger lockout
            repeat(MAX_CONSECUTIVE_FAILURES) {
                keystore.attemptBiometric(success = false)
            }

            // Advance time but NOT past the cooldown
            keystore.advanceTime(waitTime)

            // Signing should still be locked
            keystore.sign(byteArrayOf(1, 2, 3)).isFailure
        }
    }

    @Test
    fun successInMiddleOfFailuresPreventsLockout() = runTest {
        forAll(Arb.int(1..10)) { rounds ->
            val keystore = FakeBiometricKeystore()

            // Pattern: 2 failures, then 1 success, repeated
            // This should NEVER trigger lockout because success resets the counter
            repeat(rounds) {
                keystore.attemptBiometric(success = false)
                keystore.attemptBiometric(success = false)
                keystore.attemptBiometric(success = true)
            }

            // Should never be locked because we always reset before reaching 3
            !keystore.isLocked && keystore.consecutiveFailures == 0
        }
    }

    @Test
    fun arbitrarySequenceRespectsLockoutRules() = runTest {
        forAll(Arb.list(Arb.boolean(), 1..20)) { attempts ->
            val keystore = FakeBiometricKeystore()

            var consecutiveFailuresSoFar = 0
            var expectLocked = false

            for (success in attempts) {
                if (expectLocked) break // Stop processing once locked

                if (success) {
                    consecutiveFailuresSoFar = 0
                    keystore.attemptBiometric(success = true)
                } else {
                    consecutiveFailuresSoFar++
                    keystore.attemptBiometric(success = false)
                    if (consecutiveFailuresSoFar >= MAX_CONSECUTIVE_FAILURES) {
                        expectLocked = true
                    }
                }
            }

            // Verify: locked state matches our expectation
            keystore.isLocked == expectLocked
        }
    }
}
