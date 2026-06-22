package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.EarningSource
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.domain.models.WalletAddress
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.uuid
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Property 14: Biometric Gating by Transaction Value
 *
 * *For any* transaction amount and user-configured high-value threshold,
 * biometric authentication SHALL be required if and only if the amount exceeds
 * the threshold. Transactions at or below the threshold SHALL proceed without
 * biometric challenge.
 *
 * **Validates: Requirements 5.7, 13.4**
 */
class BiometricGatingPropertyTest {

    private val cryptoProvider = SimpleCryptoProvider()
    private val vault = DefaultPrivacyVault(cryptoProvider)
    private val eventBus = DefaultAgentEventBus()

    private val earningEngineAgentId = AgentId("earning_engine")
    private val biometricThreshold = 25.0

    /**
     * Creates a DefaultEarningEngine with FULLY_AUTOMATIC policy (high limits)
     * so that the policy check passes and we reach the biometric gating logic.
     * The secureKeystore is set to null, so biometric-required transactions will
     * fail with BiometricRequiredException (proving biometric was required).
     */
    private suspend fun createEngine(threshold: Double = biometricThreshold): DefaultEarningEngine {
        // Set up a FULLY_AUTOMATIC policy with very high limits to ensure policy approval
        val policyManager = DefaultPolicyManager(vault, eventBus)
        val permissivePolicy = Policy(
            id = "test_earning_policy",
            agentId = earningEngineAgentId,
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
            secureKeystore = null, // No keystore → biometric will fail when required
            payoutRail = null,
            walletAddress = WalletAddress("test_wallet"),
            biometricThreshold = threshold,
        )
    }

    /**
     * Creates an EarningEvent with the given amount and a unique ID.
     */
    private fun createEvent(amountUsdc: Double, id: String): EarningEvent {
        return EarningEvent(
            id = id,
            source = EarningSource.AIRTIME_SALE,
            amountUsdc = amountUsdc,
            amountLocal = null,
            localCurrency = null,
            agentId = earningEngineAgentId,
            timestamp = 1_719_792_000_000L,
        )
    }

    @Test
    fun amountsAboveThresholdRequireBiometric() = runTest {
        /**
         * For any amount strictly above the biometric threshold (25.0),
         * initiating a payout with secureKeystore=null should fail with
         * BiometricRequiredException — proving that biometric authentication
         * was required for the transaction.
         */
        forAll(Arb.double(25.01..1000.0)) { amount ->
            val engine = createEngine()
            val eventId = Arb.uuid().bind().toString()
            val event = createEvent(amountUsdc = amount, id = eventId)
            val result = engine.initiatePayout(event)

            // Should fail because biometric is required but no keystore is available
            result.isFailure && result.exceptionOrNull() is BiometricRequiredException
        }
    }

    @Test
    fun amountsAtOrBelowThresholdDoNotRequireBiometric() = runTest {
        /**
         * For any amount at or below the biometric threshold (25.0),
         * initiating a payout with secureKeystore=null should succeed —
         * proving that biometric authentication was NOT required.
         */
        forAll(Arb.double(0.01..25.0)) { amount ->
            val engine = createEngine()
            val eventId = Arb.uuid().bind().toString()
            val event = createEvent(amountUsdc = amount, id = eventId)
            val result = engine.initiatePayout(event)

            // Should succeed without biometric challenge (amount <= threshold)
            result.isSuccess
        }
    }

    @Test
    fun thresholdBoundaryExactlyAtThreshold() = runTest {
        /**
         * An amount exactly equal to the threshold should NOT require biometric.
         * The spec says biometric is required if amount EXCEEDS the threshold.
         */
        val engine = createEngine()
        val event = createEvent(amountUsdc = biometricThreshold, id = "boundary_test_event")
        val result = engine.initiatePayout(event)

        // Amount equals threshold → no biometric required → should succeed
        assertTrue(result.isSuccess, "Amount exactly at threshold should not require biometric")
    }

    @Test
    fun amountJustAboveThresholdRequiresBiometric() = runTest {
        /**
         * An amount just barely above the threshold (threshold + epsilon) should require biometric.
         */
        val engine = createEngine()
        val justAbove = biometricThreshold + 0.001
        val event = createEvent(amountUsdc = justAbove, id = "just_above_test_event")
        val result = engine.initiatePayout(event)

        // Amount exceeds threshold → biometric required → fails without keystore
        assertTrue(result.isFailure, "Amount just above threshold should require biometric")
        assertIs<BiometricRequiredException>(result.exceptionOrNull())
    }
}
