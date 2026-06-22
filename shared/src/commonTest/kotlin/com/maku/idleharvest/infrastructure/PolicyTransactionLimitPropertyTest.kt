package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentAction
import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.domain.models.PolicyDecision
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.list
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 15: Policy Transaction Limit Enforcement
 *
 * *For any* sequence of transactions by an agent within a time period, the cumulative
 * transaction amount SHALL never exceed the configured per-agent per-period maximum.
 * The transaction that would cause the limit to be exceeded SHALL be blocked.
 *
 * **Validates: Requirements 6.3**
 */
class PolicyTransactionLimitPropertyTest {

    @Test
    fun cumulativeTransactionsNeverExceedDailyLimit() = runTest {
        forAll(
            Arb.double(1.0..1000.0), // daily limit
            Arb.list(Arb.double(0.1..100.0), 1..20) // transaction amounts
        ) { dailyLimit, amounts ->
            val vault = DefaultPrivacyVault(SimpleCryptoProvider())
            val eventBus = DefaultAgentEventBus()
            val fixedTime = 1_719_792_000_000L // fixed clock so all transactions are in the same day
            val policyManager = DefaultPolicyManager(vault, eventBus, clock = { fixedTime })

            val agentId = AgentId("test_agent")
            policyManager.setPolicy(
                Policy(
                    id = "test_policy",
                    agentId = agentId,
                    autonomyLevel = AutonomyLevel.FULLY_AUTOMATIC,
                    maxTransactionPerDay = dailyLimit,
                    maxTransactionSingle = null,
                    resourceShareLimits = null,
                    requireBiometricAbove = null,
                    isActive = true,
                )
            )

            var cumulative = 0.0
            amounts.all { amount ->
                val action = AgentAction(
                    agentId = agentId,
                    actionType = "transfer",
                    description = "test transaction",
                    amountUsdc = amount,
                    resourceImpact = null,
                    timestamp = fixedTime,
                )
                val decision = policyManager.checkAction(agentId, action)
                if (cumulative + amount <= dailyLimit) {
                    cumulative += amount
                    decision is PolicyDecision.Approved
                } else {
                    decision is PolicyDecision.Denied
                }
            }
        }
    }
}
