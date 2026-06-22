package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentAction
import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.domain.models.PolicyDecision
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 16: Policy Immediate Application
 *
 * *For any* valid policy created or updated at time T, the next agent action check
 * occurring after T SHALL evaluate against the new/updated policy. No action check
 * after T SHALL use the stale policy version.
 *
 * **Validates: Requirements 6.2**
 */
class PolicyImmediateApplicationPropertyTest {

    @Test
    fun updatedPolicyIsAppliedImmediately() = runTest {
        forAll(
            Arb.double(1.0..100.0),   // restrictive limit (lower)
            Arb.double(101.0..500.0)  // transaction amount (above restrictive, below permissive)
        ) { restrictiveLimit, amount ->
            val vault = DefaultPrivacyVault(SimpleCryptoProvider())
            val eventBus = DefaultAgentEventBus()
            val policyManager = DefaultPolicyManager(vault, eventBus)
            val agentId = AgentId("test_agent")

            // Set a permissive policy: limit is well above the transaction amount
            policyManager.setPolicy(
                Policy(
                    id = "policy1",
                    agentId = agentId,
                    autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                    maxTransactionSingle = amount + 100.0, // allows the transaction
                    maxTransactionPerDay = null,
                    resourceShareLimits = null,
                    requireBiometricAbove = null,
                    isActive = true,
                )
            )

            val action = AgentAction(
                agentId = agentId,
                actionType = "transfer",
                description = "Test transaction",
                amountUsdc = amount,
                resourceImpact = null,
                timestamp = 1_000_000L,
            )

            // First check with permissive policy — should be approved
            val result1 = policyManager.checkAction(agentId, action)

            // Update to restrictive policy: limit is below the transaction amount
            policyManager.setPolicy(
                Policy(
                    id = "policy1",
                    agentId = agentId,
                    autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                    maxTransactionSingle = restrictiveLimit, // less than amount (restrictiveLimit ≤ 100 < 101 ≤ amount)
                    maxTransactionPerDay = null,
                    resourceShareLimits = null,
                    requireBiometricAbove = null,
                    isActive = true,
                )
            )

            // Second check with same action — should now be denied
            val result2 = policyManager.checkAction(agentId, action)

            result1 is PolicyDecision.Approved && result2 is PolicyDecision.Denied
        }
    }

    @Test
    fun newPolicyIsAppliedImmediatelyToNewAgent() = runTest {
        forAll(
            Arb.double(1.0..100.0),   // restrictive limit
            Arb.double(101.0..500.0)  // transaction amount
        ) { restrictiveLimit, amount ->
            val vault = DefaultPrivacyVault(SimpleCryptoProvider())
            val eventBus = DefaultAgentEventBus()
            val policyManager = DefaultPolicyManager(vault, eventBus)
            val agentId = AgentId("new_agent")

            val action = AgentAction(
                agentId = agentId,
                actionType = "transfer",
                description = "Test transaction",
                amountUsdc = amount,
                resourceImpact = null,
                timestamp = 1_000_000L,
            )

            // No policy set yet — action should be approved (no policy = no restriction)
            val result1 = policyManager.checkAction(agentId, action)

            // Create a restrictive policy (limit below amount)
            policyManager.setPolicy(
                Policy(
                    id = "policy_new",
                    agentId = agentId,
                    autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                    maxTransactionSingle = restrictiveLimit, // less than amount
                    maxTransactionPerDay = null,
                    resourceShareLimits = null,
                    requireBiometricAbove = null,
                    isActive = true,
                )
            )

            // After policy creation, same action should now be denied
            val result2 = policyManager.checkAction(agentId, action)

            result1 is PolicyDecision.Approved && result2 is PolicyDecision.Denied
        }
    }
}
