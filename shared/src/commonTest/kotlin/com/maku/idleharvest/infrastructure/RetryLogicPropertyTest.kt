package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.BalanceResponse
import com.maku.idleharvest.domain.interfaces.SellRequest
import com.maku.idleharvest.domain.interfaces.SellResponse
import com.maku.idleharvest.domain.interfaces.TransferRequest
import com.maku.idleharvest.domain.interfaces.TransferResponse
import com.maku.idleharvest.domain.interfaces.VtuPlatformClient
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.ApprovalSource
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.ComplianceRuleSet
import com.maku.idleharvest.domain.models.MonetizationAction
import com.maku.idleharvest.domain.models.MonetizationRecommendation
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.domain.models.VtuPlatform
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.of
import io.kotest.property.forAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property 5: Retry Logic Invariant
 *
 * *For any* external service call failure (VTU API, DePIN network, Circle API),
 * the retry mechanism SHALL attempt at most 3 retries with exponentially increasing
 * backoff intervals, and SHALL notify the user on final failure. The retry count
 * SHALL never exceed the configured maximum.
 *
 * **Validates: Requirements 2.3, 17.5**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetryLogicPropertyTest {
    private val cryptoProvider = SimpleCryptoProvider()

    /**
     * Creates a VtuPlatformClient that always fails with a RuntimeException,
     * tracking how many times each method is called.
     */
    private fun alwaysFailingClient(callCounter: () -> Unit): VtuPlatformClient = object : VtuPlatformClient {
        override val platform: VtuPlatform = VtuPlatform.PRESTMIT

        override suspend fun sell(request: SellRequest): Result<SellResponse> {
            callCounter()
            return Result.failure(RuntimeException("Service unavailable"))
        }

        override suspend fun transfer(request: TransferRequest): Result<TransferResponse> {
            callCounter()
            return Result.failure(RuntimeException("Service unavailable"))
        }

        override suspend fun checkBalance(): Result<BalanceResponse> {
            callCounter()
            return Result.failure(RuntimeException("Service unavailable"))
        }
    }

    /**
     * Creates a DefaultAirtimeAgent configured with permissive policy/compliance
     * so the VTU client retry logic is exercised.
     */
    private suspend fun createAgentWithFailingClient(
        failingClient: VtuPlatformClient,
        eventBus: DefaultAgentEventBus,
        vault: DefaultPrivacyVault,
    ): DefaultAirtimeAgent {
        val policyManager = DefaultPolicyManager(vault, eventBus)
        val permissivePolicy =
            Policy(
                id = "test_policy_auto",
                agentId = AgentId("airtime_agent"),
                autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                maxTransactionPerDay = 1_000_000.0,
                maxTransactionSingle = 1_000_000.0,
                resourceShareLimits = null,
                requireBiometricAbove = null,
                isActive = true,
            )
        policyManager.setPolicy(permissivePolicy)

        val complianceEngine = DefaultComplianceEngine(vault)
        val permissiveRules =
            ComplianceRuleSet(
                country = "NG",
                carrier = "MTN",
                dailyTransactionLimit = 1_000_000.0,
                monthlyTransactionLimit = 10_000_000.0,
                kycThreshold = 1_000_000.0,
                rateLimitPerHour = 10_000,
                version = 1,
                lastUpdated = 0L,
            )
        complianceEngine.updateRules(permissiveRules)

        val inferenceEngine =
            DefaultInferenceEngine(
                modelRegistry = DefaultModelRegistry(vault, cryptoProvider),
            )

        return DefaultAirtimeAgent(
            policyManager = policyManager,
            complianceEngine = complianceEngine,
            vault = vault,
            eventBus = eventBus,
            inferenceEngine = inferenceEngine,
            vtuClient = failingClient,
        )
    }

    @Test
    fun retriesExactlyMaxTimesBeforeFailure() = runTest {
        var callCount = 0
        val failingClient = alwaysFailingClient { callCount++ }
        val vault = DefaultPrivacyVault(cryptoProvider)
        val eventBus = DefaultAgentEventBus()

        val agent = createAgentWithFailingClient(failingClient, eventBus, vault)

        val action =
            MonetizationAction(
                bundleId = "bundle_001",
                recommendation =
                MonetizationRecommendation.Sell(
                    amount = 500L,
                    platform = VtuPlatform.PRESTMIT,
                    confidence = 0.9f,
                ),
                approvedBy = ApprovalSource.POLICY_AUTO,
                timestamp = 1_719_792_000_000L,
            )

        val result = agent.executeAction(action)

        // Verify the action failed
        assertFalse(result.success)
        // Verify exactly MAX_RETRIES (3) calls were made
        assertEquals(DefaultAirtimeAgent.MAX_RETRIES, callCount)
        // Verify error message mentions retries
        assertTrue(
            result.errorMessage?.contains("${DefaultAirtimeAgent.MAX_RETRIES} retries") == true,
            "Error message should mention retry count, got: ${result.errorMessage}",
        )
    }

    @Test
    fun retryCountNeverExceedsMaximum() = runTest {
        forAll(Arb.int(1..10)) { _ ->
            var callCount = 0
            val failingClient = alwaysFailingClient { callCount++ }
            val vault = DefaultPrivacyVault(cryptoProvider)
            val eventBus = DefaultAgentEventBus()

            val agent = createAgentWithFailingClient(failingClient, eventBus, vault)

            val action =
                MonetizationAction(
                    bundleId = "bundle_property_test",
                    recommendation =
                    MonetizationRecommendation.Sell(
                        amount = 1000L,
                        platform = VtuPlatform.PRESTMIT,
                        confidence = 0.85f,
                    ),
                    approvedBy = ApprovalSource.POLICY_AUTO,
                    timestamp = 1_719_792_000_000L,
                )

            val result = agent.executeAction(action)

            // Invariant: call count MUST never exceed MAX_RETRIES
            !result.success && callCount <= DefaultAirtimeAgent.MAX_RETRIES
        }
    }

    @Test
    fun retryCountIsExactlyMaxForAnyAmountAndPlatform() = runTest {
        val amountArb = Arb.long(100L..50_000L)
        val platformArb = Arb.of(VtuPlatform.PRESTMIT, VtuPlatform.VTU_NG, VtuPlatform.RELOADLY)

        forAll(amountArb, platformArb) { amount, platform ->
            var callCount = 0
            val failingClient = alwaysFailingClient { callCount++ }
            val vault = DefaultPrivacyVault(cryptoProvider)
            val eventBus = DefaultAgentEventBus()

            val agent = createAgentWithFailingClient(failingClient, eventBus, vault)

            val action =
                MonetizationAction(
                    bundleId = "bundle_amounts_$amount",
                    recommendation =
                    MonetizationRecommendation.Sell(
                        amount = amount,
                        platform = platform,
                        confidence = 0.8f,
                    ),
                    approvedBy = ApprovalSource.POLICY_AUTO,
                    timestamp = 1_719_792_000_000L,
                )

            val result = agent.executeAction(action)

            // For any amount and platform, on full failure:
            // - Result must indicate failure
            // - Call count must be exactly MAX_RETRIES
            !result.success && callCount == DefaultAirtimeAgent.MAX_RETRIES
        }
    }

    @Test
    fun notifiesUserOnFinalFailureViaEventBus() = runTest {
        var callCount = 0
        val failingClient = alwaysFailingClient { callCount++ }
        val vault = DefaultPrivacyVault(cryptoProvider)
        val eventBus = DefaultAgentEventBus()

        val agent = createAgentWithFailingClient(failingClient, eventBus, vault)

        // Subscribe to PolicyViolation events (used as the notification mechanism)
        val violations = mutableListOf<AgentEvent.PolicyViolation>()
        val job =
            launch {
                eventBus.subscribe(AgentEvent.PolicyViolation::class).collect {
                    violations.add(it)
                }
            }

        // Ensure subscriber is active
        yield()

        val action =
            MonetizationAction(
                bundleId = "bundle_notify_test",
                recommendation =
                MonetizationRecommendation.Sell(
                    amount = 1000L,
                    platform = VtuPlatform.PRESTMIT,
                    confidence = 0.9f,
                ),
                approvedBy = ApprovalSource.POLICY_AUTO,
                timestamp = 1_719_792_000_000L,
            )

        val result = agent.executeAction(action)

        // Allow event propagation
        advanceUntilIdle()

        // Verify failure
        assertFalse(result.success)
        assertEquals(DefaultAirtimeAgent.MAX_RETRIES, callCount)

        // Verify user notification was sent via event bus
        assertTrue(
            violations.isNotEmpty(),
            "A PolicyViolation event should be published to notify the user on final failure",
        )
        assertEquals(
            DefaultAirtimeAgent.AIRTIME_AGENT_ID,
            violations.first().agentId,
            "Notification should identify the airtime agent",
        )

        job.cancel()
    }

    @Test
    fun transferRetryAlsoRespectsMaxRetries() = runTest {
        var callCount = 0
        val failingClient = alwaysFailingClient { callCount++ }
        val vault = DefaultPrivacyVault(cryptoProvider)
        val eventBus = DefaultAgentEventBus()

        val agent = createAgentWithFailingClient(failingClient, eventBus, vault)

        val action =
            MonetizationAction(
                bundleId = "bundle_transfer_retry",
                recommendation =
                MonetizationRecommendation.Transfer(
                    recipient = "+2341234567890",
                    amount = 2000L,
                ),
                approvedBy = ApprovalSource.POLICY_AUTO,
                timestamp = 1_719_792_000_000L,
            )

        val result = agent.executeAction(action)

        // Verify the transfer also retries exactly MAX_RETRIES times
        assertFalse(result.success)
        assertEquals(DefaultAirtimeAgent.MAX_RETRIES, callCount)
    }

    @Test
    fun earlySuccessStopsRetrying() = runTest {
        var callCount = 0
        // Client that succeeds on the 2nd attempt
        val clientSucceedsOn2nd =
            object : VtuPlatformClient {
                override val platform: VtuPlatform = VtuPlatform.PRESTMIT

                override suspend fun sell(request: SellRequest): Result<SellResponse> {
                    callCount++
                    return if (callCount >= 2) {
                        Result.success(
                            SellResponse(
                                transactionId = "tx_success",
                                amountSettled = request.amount,
                                fee = 10L,
                                timestamp = 1_719_792_000_000L,
                            ),
                        )
                    } else {
                        Result.failure(RuntimeException("Temporary failure"))
                    }
                }

                override suspend fun transfer(request: TransferRequest): Result<TransferResponse> {
                    callCount++
                    return Result.failure(RuntimeException("Not implemented"))
                }

                override suspend fun checkBalance(): Result<BalanceResponse> = Result.failure(RuntimeException("Not implemented"))
            }

        val vault = DefaultPrivacyVault(cryptoProvider)
        val eventBus = DefaultAgentEventBus()

        val policyManager = DefaultPolicyManager(vault, eventBus)
        policyManager.setPolicy(
            Policy(
                id = "test_auto",
                agentId = AgentId("airtime_agent"),
                autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                maxTransactionPerDay = 1_000_000.0,
                maxTransactionSingle = 1_000_000.0,
                resourceShareLimits = null,
                requireBiometricAbove = null,
                isActive = true,
            ),
        )

        val complianceEngine = DefaultComplianceEngine(vault)
        complianceEngine.updateRules(
            ComplianceRuleSet(
                country = "NG",
                carrier = "MTN",
                dailyTransactionLimit = 1_000_000.0,
                monthlyTransactionLimit = 10_000_000.0,
                kycThreshold = 1_000_000.0,
                rateLimitPerHour = 10_000,
                version = 1,
                lastUpdated = 0L,
            ),
        )

        val inferenceEngine =
            DefaultInferenceEngine(
                modelRegistry = DefaultModelRegistry(vault, cryptoProvider),
            )

        val agent =
            DefaultAirtimeAgent(
                policyManager = policyManager,
                complianceEngine = complianceEngine,
                vault = vault,
                eventBus = eventBus,
                inferenceEngine = inferenceEngine,
                vtuClient = clientSucceedsOn2nd,
            )

        val action =
            MonetizationAction(
                bundleId = "bundle_early_success",
                recommendation =
                MonetizationRecommendation.Sell(
                    amount = 500L,
                    platform = VtuPlatform.PRESTMIT,
                    confidence = 0.9f,
                ),
                approvedBy = ApprovalSource.POLICY_AUTO,
                timestamp = 1_719_792_000_000L,
            )

        val result = agent.executeAction(action)

        // Verify success on 2nd attempt (no more retries needed)
        assertTrue(result.success)
        // Only 2 calls should have been made (1 failure + 1 success)
        assertEquals(2, callCount)
        // Must be less than MAX_RETRIES since it succeeded early
        assertTrue(callCount < DefaultAirtimeAgent.MAX_RETRIES)
    }
}
