package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.ComplianceAuditEntry
import com.maku.idleharvest.domain.models.ComplianceDecision
import com.maku.idleharvest.domain.models.ComplianceRuleSet
import com.maku.idleharvest.domain.models.TransactionRequest
import com.maku.idleharvest.domain.models.TransactionType
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 25: Compliance Audit Completeness
 *
 * *For any* compliance check (whether approved or denied), a corresponding audit entry
 * SHALL exist containing the check result, timestamp, transaction details, and applicable
 * regulation. No compliance check SHALL be unlogged.
 *
 * **Validates: Requirements 12.4**
 */
class ComplianceAuditPropertyTest {

    private val fixedClock = 1_719_792_000_000L // 2024-07-01 00:00:00 UTC

    /**
     * Generator for TransactionRequest instances with varying amounts.
     * Some amounts will exceed typical KYC thresholds (to trigger Blocked decisions)
     * and some will be within limits (to trigger Approved decisions).
     */
    private fun transactionRequestArb(): Arb<TransactionRequest> = arbitrary {
        TransactionRequest(
            agentId = AgentId(Arb.of("agent_1", "agent_2", "agent_3").bind()),
            amount = Arb.double(1.0..20_000.0).bind(),
            currency = Arb.of("KES", "NGN", "UGX", "TZS", "GHS").bind(),
            type = Arb.enum<TransactionType>().bind(),
            counterparty = Arb.string(5..15).orNull(0.3).bind(),
            platform = Arb.of("Prestmit", "VTU.ng", "Reloadly").bind(),
            country = Arb.of("KE", "NG", "UG", "TZ", "GH").bind(),
            carrier = Arb.of("Safaricom", "Airtel", "MTN", "Glo").bind(),
            timestamp = Arb.long(fixedClock - 3_600_000L..fixedClock).bind(),
        )
    }

    /**
     * Creates a compliance engine with rules configured to produce a mix of
     * approved and blocked decisions based on transaction amounts.
     */
    private fun createEngineWithRules(): DefaultComplianceEngine {
        val vault = DefaultPrivacyVault(SimpleCryptoProvider())
        return DefaultComplianceEngine(vault, clock = { fixedClock })
    }

    private fun testRuleSet(country: String, carrier: String): ComplianceRuleSet {
        return ComplianceRuleSet(
            country = country,
            carrier = carrier,
            dailyTransactionLimit = 50_000.0,
            monthlyTransactionLimit = 200_000.0,
            kycThreshold = 5_000.0, // Amounts above 5000 will be blocked
            rateLimitPerHour = 100, // High enough to not interfere
            version = 1,
            lastUpdated = fixedClock - 86_400_000L,
        )
    }

    @Test
    fun everyComplianceCheckIsAudited() = runTest {
        forAll(
            Arb.list(transactionRequestArb(), 1..20)
        ) { transactions ->
            val engine = createEngineWithRules()

            // Set up rules for all country/carrier combinations that might appear
            val countryCarrierPairs = transactions.map { it.country to it.carrier }.distinct()
            for ((country, carrier) in countryCarrierPairs) {
                engine.updateRules(testRuleSet(country, carrier))
            }

            // Execute all checks
            transactions.forEach { tx ->
                engine.checkTransaction(tx)
            }

            // Verify audit log count equals number of checks performed
            val auditEntries = engine.getAuditLog().first()
            auditEntries.size == transactions.size
        }
    }

    @Test
    fun auditEntriesContainCorrectDecisions() = runTest {
        forAll(
            Arb.list(transactionRequestArb(), 1..15)
        ) { transactions ->
            val engine = createEngineWithRules()

            // Set up rules for all combinations
            val countryCarrierPairs = transactions.map { it.country to it.carrier }.distinct()
            for ((country, carrier) in countryCarrierPairs) {
                engine.updateRules(testRuleSet(country, carrier))
            }

            // Execute checks and collect decisions
            val decisions = transactions.map { tx ->
                engine.checkTransaction(tx)
            }

            // Verify each audit entry contains correct fields
            val auditEntries = engine.getAuditLog().first()

            // Every entry has a non-null decision, timestamp, and transaction details
            val allEntriesComplete = auditEntries.all { entry ->
                entry.id.isNotEmpty() &&
                    entry.timestamp > 0 &&
                    entry.transactionRequest.agentId.value.isNotEmpty() &&
                    entry.transactionRequest.amount > 0 &&
                    entry.appliedRules.country.isNotEmpty()
            }

            // Decisions in audit log match the decisions returned by checkTransaction
            val decisionsMatch = auditEntries.zip(decisions).all { (entry, decision) ->
                entry.decision == decision
            }

            allEntriesComplete && decisionsMatch
        }
    }

    @Test
    fun auditLogsApprovedAndBlockedDecisions() = runTest {
        forAll(
            Arb.int(1..10)
        ) { count ->
            val engine = createEngineWithRules()
            engine.updateRules(testRuleSet("KE", "Safaricom"))

            // Create transactions: some under KYC threshold (approved), some over (blocked)
            val approvedTx = TransactionRequest(
                agentId = AgentId("agent_1"),
                amount = 100.0, // Well under 5000 KYC threshold
                currency = "KES",
                type = TransactionType.SELL,
                counterparty = "buyer123",
                platform = "Prestmit",
                country = "KE",
                carrier = "Safaricom",
                timestamp = fixedClock,
            )
            val blockedTx = TransactionRequest(
                agentId = AgentId("agent_1"),
                amount = 10_000.0, // Over 5000 KYC threshold
                currency = "KES",
                type = TransactionType.SELL,
                counterparty = "buyer456",
                platform = "Prestmit",
                country = "KE",
                carrier = "Safaricom",
                timestamp = fixedClock,
            )

            // Execute both types of checks multiple times
            repeat(count) {
                engine.checkTransaction(approvedTx)
                engine.checkTransaction(blockedTx)
            }

            val auditEntries = engine.getAuditLog().first()

            // Total entries = 2 * count (both approved and blocked are logged)
            val correctCount = auditEntries.size == 2 * count

            // Verify we have both approved and blocked entries
            val hasApproved = auditEntries.any { it.decision is ComplianceDecision.Approved }
            val hasBlocked = auditEntries.any { it.decision is ComplianceDecision.Blocked }

            correctCount && hasApproved && hasBlocked
        }
    }

    @Test
    fun auditEntryTimestampsAreNonZero() = runTest {
        forAll(
            Arb.list(transactionRequestArb(), 1..10)
        ) { transactions ->
            val engine = createEngineWithRules()

            // Set up rules
            val countryCarrierPairs = transactions.map { it.country to it.carrier }.distinct()
            for ((country, carrier) in countryCarrierPairs) {
                engine.updateRules(testRuleSet(country, carrier))
            }

            // Execute checks
            transactions.forEach { tx ->
                engine.checkTransaction(tx)
            }

            // Verify all audit entries have valid timestamps
            val auditEntries = engine.getAuditLog().first()
            auditEntries.all { entry ->
                entry.timestamp > 0
            }
        }
    }
}
