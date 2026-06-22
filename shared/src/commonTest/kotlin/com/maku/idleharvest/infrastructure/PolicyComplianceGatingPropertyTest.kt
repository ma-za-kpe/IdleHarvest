package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentAction
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.ComplianceDecision
import com.maku.idleharvest.domain.models.ComplianceRuleSet
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.domain.models.PolicyDecision
import com.maku.idleharvest.domain.models.TransactionRequest
import com.maku.idleharvest.domain.models.TransactionType
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.forAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Property 4: Policy and Compliance Gating
 *
 * *For any* agent action that involves a financial transaction or resource commitment,
 * the Policy_Manager check and Compliance_Engine check SHALL both be invoked and approved
 * BEFORE the action is executed. If either check denies the action, execution SHALL NOT
 * occur and the denial SHALL be logged.
 *
 * **Validates: Requirements 2.6, 5.2, 6.4, 12.2, 12.3**
 */
class PolicyComplianceGatingPropertyTest {
    private val cryptoProvider = SimpleCryptoProvider()
    private val vault = DefaultPrivacyVault(cryptoProvider)
    private val eventBus = DefaultAgentEventBus()

    private val testAgentId = AgentId("airtime_agent")
    private val testCountry = "KE"
    private val testCarrier = "Safaricom"

    @Test
    fun actionBlockedWhenPolicyDenies() = runTest {
        // Set up policy that denies (MANUAL autonomy → all financial actions require approval)
        val policyManager = DefaultPolicyManager(vault, eventBus)
        val manualPolicy =
            Policy(
                id = "test_policy_manual",
                agentId = testAgentId,
                autonomyLevel = AutonomyLevel.MANUAL,
                maxTransactionPerDay = 100.0,
                maxTransactionSingle = 50.0,
                resourceShareLimits = null,
                requireBiometricAbove = null,
                isActive = true,
            )
        policyManager.setPolicy(manualPolicy)

        // Set up compliance that approves (permissive rules)
        val complianceEngine = DefaultComplianceEngine(vault)
        val permissiveRules =
            ComplianceRuleSet(
                country = testCountry,
                carrier = testCarrier,
                dailyTransactionLimit = 100_000.0,
                monthlyTransactionLimit = 1_000_000.0,
                kycThreshold = 100_000.0,
                rateLimitPerHour = 1000,
                version = 1,
                lastUpdated = 0L,
            )
        complianceEngine.updateRules(permissiveRules)

        forAll(Arb.double(0.1..50.0)) { amount ->
            val action =
                AgentAction(
                    agentId = testAgentId,
                    actionType = "SELL_AIRTIME",
                    description = "Sell airtime worth $amount",
                    amountUsdc = amount,
                    resourceImpact = null,
                    timestamp = 1_719_792_000_000L,
                )

            // Policy check should deny (RequiresApproval due to MANUAL autonomy)
            val policyDecision = policyManager.checkAction(testAgentId, action)

            // MANUAL autonomy means all actions require approval → execution blocked
            policyDecision is PolicyDecision.RequiresApproval ||
                policyDecision is PolicyDecision.Denied
        }
    }

    @Test
    fun actionBlockedWhenComplianceDenies() = runTest {
        // Set up policy that approves (FULLY_AUTOMATIC, high limits)
        val policyManager = DefaultPolicyManager(vault, eventBus)
        val permissivePolicy =
            Policy(
                id = "test_policy_auto",
                agentId = testAgentId,
                autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                maxTransactionPerDay = 100_000.0,
                maxTransactionSingle = 100_000.0,
                resourceShareLimits = null,
                requireBiometricAbove = null,
                isActive = true,
            )
        policyManager.setPolicy(permissivePolicy)

        // Set up compliance that denies (low KYC threshold)
        val complianceEngine = DefaultComplianceEngine(vault)
        val restrictiveRules =
            ComplianceRuleSet(
                country = testCountry,
                carrier = testCarrier,
                dailyTransactionLimit = 100_000.0,
                monthlyTransactionLimit = 1_000_000.0,
                kycThreshold = 5.0, // Very low threshold, most amounts will exceed
                rateLimitPerHour = 1000,
                version = 1,
                lastUpdated = 0L,
            )
        complianceEngine.updateRules(restrictiveRules)

        forAll(Arb.double(6.0..100.0)) { amount ->
            val transactionRequest =
                TransactionRequest(
                    agentId = testAgentId,
                    amount = amount,
                    currency = "KES",
                    type = TransactionType.SELL,
                    counterparty = "buyer_123",
                    platform = "Prestmit",
                    country = testCountry,
                    carrier = testCarrier,
                    timestamp = 1_719_792_000_000L,
                )

            // Policy approves (FULLY_AUTOMATIC + within limits)
            val action =
                AgentAction(
                    agentId = testAgentId,
                    actionType = "SELL_AIRTIME",
                    description = "Sell airtime worth $amount",
                    amountUsdc = amount,
                    resourceImpact = null,
                    timestamp = 1_719_792_000_000L,
                )
            val policyDecision = policyManager.checkAction(testAgentId, action)

            // Compliance blocks (amount exceeds KYC threshold of 5.0)
            val complianceDecision = complianceEngine.checkTransaction(transactionRequest)

            // Verify: policy approved but compliance blocked
            policyDecision is PolicyDecision.Approved &&
                complianceDecision is ComplianceDecision.Blocked
        }
    }

    @Test
    fun actionBlockedWhenBothDeny() = runTest {
        // Set up policy that denies (MANUAL autonomy)
        val policyManager = DefaultPolicyManager(vault, eventBus)
        val manualPolicy =
            Policy(
                id = "test_policy_both_deny",
                agentId = testAgentId,
                autonomyLevel = AutonomyLevel.MANUAL,
                maxTransactionPerDay = 100.0,
                maxTransactionSingle = 50.0,
                resourceShareLimits = null,
                requireBiometricAbove = null,
                isActive = true,
            )
        policyManager.setPolicy(manualPolicy)

        // Set up compliance that also denies (low KYC threshold)
        val complianceEngine = DefaultComplianceEngine(vault)
        val restrictiveRules =
            ComplianceRuleSet(
                country = testCountry,
                carrier = testCarrier,
                dailyTransactionLimit = 100_000.0,
                monthlyTransactionLimit = 1_000_000.0,
                kycThreshold = 5.0,
                rateLimitPerHour = 1000,
                version = 1,
                lastUpdated = 0L,
            )
        complianceEngine.updateRules(restrictiveRules)

        forAll(Arb.double(6.0..100.0)) { amount ->
            val action =
                AgentAction(
                    agentId = testAgentId,
                    actionType = "SELL_AIRTIME",
                    description = "Sell airtime worth $amount",
                    amountUsdc = amount,
                    resourceImpact = null,
                    timestamp = 1_719_792_000_000L,
                )

            val transactionRequest =
                TransactionRequest(
                    agentId = testAgentId,
                    amount = amount,
                    currency = "KES",
                    type = TransactionType.SELL,
                    counterparty = "buyer_123",
                    platform = "Prestmit",
                    country = testCountry,
                    carrier = testCarrier,
                    timestamp = 1_719_792_000_000L,
                )

            // Both should deny
            val policyDecision = policyManager.checkAction(testAgentId, action)
            val complianceDecision = complianceEngine.checkTransaction(transactionRequest)

            // Policy blocks (MANUAL → RequiresApproval) and compliance blocks (KYC)
            (
                policyDecision is PolicyDecision.RequiresApproval ||
                    policyDecision is PolicyDecision.Denied
                ) &&
                complianceDecision is ComplianceDecision.Blocked
        }
    }

    @Test
    fun actionProceedsOnlyWhenBothApprove() = runTest {
        // Set up permissive policy (FULLY_AUTOMATIC, high limits)
        val policyManager = DefaultPolicyManager(vault, eventBus)
        val permissivePolicy =
            Policy(
                id = "test_policy_permissive",
                agentId = testAgentId,
                autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                maxTransactionPerDay = 100_000.0,
                maxTransactionSingle = 100_000.0,
                resourceShareLimits = null,
                requireBiometricAbove = null,
                isActive = true,
            )
        policyManager.setPolicy(permissivePolicy)

        // Set up permissive compliance (high thresholds)
        val complianceEngine = DefaultComplianceEngine(vault)
        val permissiveRules =
            ComplianceRuleSet(
                country = testCountry,
                carrier = testCarrier,
                dailyTransactionLimit = 100_000.0,
                monthlyTransactionLimit = 1_000_000.0,
                kycThreshold = 100_000.0,
                rateLimitPerHour = 1000,
                version = 1,
                lastUpdated = 0L,
            )
        complianceEngine.updateRules(permissiveRules)

        forAll(Arb.double(0.1..50.0)) { amount ->
            val action =
                AgentAction(
                    agentId = testAgentId,
                    actionType = "SELL_AIRTIME",
                    description = "Sell airtime worth $amount",
                    amountUsdc = amount,
                    resourceImpact = null,
                    timestamp = 1_719_792_000_000L,
                )

            val transactionRequest =
                TransactionRequest(
                    agentId = testAgentId,
                    amount = amount,
                    currency = "KES",
                    type = TransactionType.SELL,
                    counterparty = "buyer_123",
                    platform = "Prestmit",
                    country = testCountry,
                    carrier = testCarrier,
                    timestamp = 1_719_792_000_000L,
                )

            // Both should approve
            val policyDecision = policyManager.checkAction(testAgentId, action)
            val complianceDecision = complianceEngine.checkTransaction(transactionRequest)

            policyDecision is PolicyDecision.Approved &&
                complianceDecision is ComplianceDecision.Approved
        }
    }

    @Test
    fun policyDenialIsLoggedViaEventBus() = runTest {
        // Set up policy that denies (single transaction limit exceeded)
        val localEventBus = DefaultAgentEventBus()
        val localVault = DefaultPrivacyVault(cryptoProvider)
        val policyManager = DefaultPolicyManager(localVault, localEventBus)
        val limitedPolicy =
            Policy(
                id = "test_policy_limited",
                agentId = testAgentId,
                autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                maxTransactionPerDay = 100.0,
                maxTransactionSingle = 10.0, // Low single limit
                resourceShareLimits = null,
                requireBiometricAbove = null,
                isActive = true,
            )
        policyManager.setPolicy(limitedPolicy)

        val action =
            AgentAction(
                agentId = testAgentId,
                actionType = "SELL_AIRTIME",
                description = "Sell airtime worth 25.0",
                amountUsdc = 25.0, // Exceeds single limit of 10.0
                resourceImpact = null,
                timestamp = 1_719_792_000_000L,
            )

        // Subscribe to violations BEFORE the action check
        val violations = mutableListOf<AgentEvent.PolicyViolation>()
        val job =
            launch {
                localEventBus.subscribe(AgentEvent.PolicyViolation::class).collect {
                    violations.add(it)
                }
            }

        // Ensure subscriber is active before publishing
        yield()

        // Execute the policy check
        val decision = policyManager.checkAction(testAgentId, action)

        // Give time for event to propagate through SharedFlow
        yield()

        // Verify denial
        assertIs<PolicyDecision.Denied>(decision)

        // Verify violation was published to event bus
        assertTrue(violations.isNotEmpty(), "Policy violation should be published to event bus")
        assertEquals(testAgentId, violations.first().agentId)

        job.cancel()
    }

    @Test
    fun complianceDenialIsLoggedInAuditLog() = runTest {
        val complianceEngine = DefaultComplianceEngine(vault)
        val restrictiveRules =
            ComplianceRuleSet(
                country = testCountry,
                carrier = testCarrier,
                dailyTransactionLimit = 100_000.0,
                monthlyTransactionLimit = 1_000_000.0,
                kycThreshold = 5.0, // Low threshold
                rateLimitPerHour = 1000,
                version = 1,
                lastUpdated = 0L,
            )
        complianceEngine.updateRules(restrictiveRules)

        val transactionRequest =
            TransactionRequest(
                agentId = testAgentId,
                amount = 20.0, // Exceeds KYC threshold of 5.0
                currency = "KES",
                type = TransactionType.SELL,
                counterparty = "buyer_123",
                platform = "Prestmit",
                country = testCountry,
                carrier = testCarrier,
                timestamp = 1_719_792_000_000L,
            )

        // Execute compliance check
        val decision = complianceEngine.checkTransaction(transactionRequest)

        // Verify blocked
        assertIs<ComplianceDecision.Blocked>(decision)

        // Verify audit log contains the denial
        val auditLog = complianceEngine.getAuditLog().first()
        assertTrue(auditLog.isNotEmpty(), "Audit log should contain the compliance denial")

        val lastEntry = auditLog.last()
        assertIs<ComplianceDecision.Blocked>(lastEntry.decision)
        assertEquals(transactionRequest, lastEntry.transactionRequest)
    }
}
