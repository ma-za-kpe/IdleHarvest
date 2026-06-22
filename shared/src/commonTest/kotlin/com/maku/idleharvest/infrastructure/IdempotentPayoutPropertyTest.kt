package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.generators.earningEvent
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 13: Idempotent Payout Guarantee
 *
 * *For any* earning event, regardless of how many times payout is attempted for that event,
 * exactly one payout SHALL be created and submitted. Duplicate payout attempts for the same
 * earning event SHALL be rejected.
 *
 * **Validates: Requirements 5.5**
 */
class IdempotentPayoutPropertyTest {

    private val cryptoProvider = SimpleCryptoProvider()

    /**
     * Creates a DefaultEarningEngine configured with a permissive policy
     * so that payout initiation succeeds (policy does not block).
     */
    private suspend fun createTestEngine(): DefaultEarningEngine {
        val vault = DefaultPrivacyVault(cryptoProvider)
        val eventBus = DefaultAgentEventBus()
        val policyManager = DefaultPolicyManager(vault, eventBus)

        // Set up a permissive policy for the earning_engine agent
        val permissivePolicy = Policy(
            id = "test_permissive_earning",
            agentId = DefaultEarningEngine.EARNING_ENGINE_ID,
            autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
            maxTransactionPerDay = 1_000_000.0,
            maxTransactionSingle = 1_000_000.0,
            resourceShareLimits = null,
            requireBiometricAbove = null,
            isActive = true,
        )
        policyManager.setPolicy(permissivePolicy)

        return DefaultEarningEngine(
            policyManager = policyManager,
            vault = vault,
            eventBus = eventBus,
            secureKeystore = null,
            payoutRail = null,
            biometricThreshold = 1_000_000.0, // Very high so biometric is never required
        )
    }

    @Test
    fun firstPayoutSucceedsAndDuplicateIsRejected() = runTest {
        forAll(Arb.earningEvent()) { event ->
            val engine = createTestEngine()
            val firstResult = engine.initiatePayout(event)
            val secondResult = engine.initiatePayout(event)

            firstResult.isSuccess && secondResult.isFailure &&
                secondResult.exceptionOrNull() is DuplicatePayoutException
        }
    }

    @Test
    fun multipleduplicateAttemptsAllRejectedAfterFirst() = runTest {
        forAll(Arb.earningEvent(), Arb.int(2..10)) { event, duplicateCount ->
            val engine = createTestEngine()

            // First attempt should succeed
            val firstResult = engine.initiatePayout(event)

            // All subsequent attempts should fail with DuplicatePayoutException
            val duplicateResults = (1..duplicateCount).map { engine.initiatePayout(event) }

            firstResult.isSuccess &&
                duplicateResults.all { it.isFailure } &&
                duplicateResults.all { it.exceptionOrNull() is DuplicatePayoutException }
        }
    }

    @Test
    fun distinctEventsEachGetExactlyOnePayout() = runTest {
        forAll(Arb.earningEvent(), Arb.earningEvent()) { event1, event2 ->
            // Ensure distinct IDs (skip if collision — extremely unlikely with UUIDs)
            if (event1.id == event2.id) {
                true // trivially pass on collision
            } else {
                val engine = createTestEngine()

                val result1 = engine.initiatePayout(event1)
                val result2 = engine.initiatePayout(event2)

                // Both distinct events should succeed independently
                val duplicate1 = engine.initiatePayout(event1)
                val duplicate2 = engine.initiatePayout(event2)

                result1.isSuccess && result2.isSuccess &&
                    duplicate1.isFailure && duplicate2.isFailure &&
                    duplicate1.exceptionOrNull() is DuplicatePayoutException &&
                    duplicate2.exceptionOrNull() is DuplicatePayoutException
            }
        }
    }

    @Test
    fun exactlyOnePayoutCreatedRegardlessOfAttemptCount() = runTest {
        forAll(Arb.earningEvent(), Arb.int(1..20)) { event, attemptCount ->
            val engine = createTestEngine()

            val results = (1..attemptCount).map { engine.initiatePayout(event) }

            // Exactly one success
            val successCount = results.count { it.isSuccess }
            // All others are DuplicatePayoutException failures
            val duplicateFailCount = results.count {
                it.isFailure && it.exceptionOrNull() is DuplicatePayoutException
            }

            successCount == 1 && duplicateFailCount == attemptCount - 1
        }
    }
}
