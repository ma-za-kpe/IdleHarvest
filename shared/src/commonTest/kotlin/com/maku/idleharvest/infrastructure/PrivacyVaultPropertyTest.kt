package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AirtimeTransaction
import com.maku.idleharvest.domain.models.ComplianceRuleSet
import com.maku.idleharvest.domain.models.DePinContribution
import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.generators.airtimeTransaction
import com.maku.idleharvest.generators.complianceRuleSet
import com.maku.idleharvest.generators.dePinContribution
import com.maku.idleharvest.generators.earningEvent
import com.maku.idleharvest.generators.policy
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 6: Privacy Vault Storage Round-Trip
 *
 * *For any* valid domain object (transaction, policy, contribution record, model metadata,
 * compliance rule), serializing and storing it in the Privacy_Vault and then retrieving
 * and deserializing it SHALL produce an object equal to the original.
 *
 * **Validates: Requirements 2.4, 3.4, 6.5, 9.1, 12.1**
 */
class PrivacyVaultPropertyTest {

    private val cryptoProvider = SimpleCryptoProvider()
    private val vault = DefaultPrivacyVault(cryptoProvider)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun airtimeTransactionRoundTrip() = runTest {
        forAll(Arb.airtimeTransaction()) { transaction ->
            val bytes = json.encodeToString(transaction).encodeToByteArray()
            vault.store("tx_airtime_${transaction.id}", bytes).getOrThrow()
            val retrieved = vault.retrieve("tx_airtime_${transaction.id}").getOrThrow()!!
            val decoded = json.decodeFromString<AirtimeTransaction>(retrieved.decodeToString())
            decoded == transaction
        }
    }

    @Test
    fun policyRoundTrip() = runTest {
        forAll(Arb.policy()) { policy ->
            val bytes = json.encodeToString(policy).encodeToByteArray()
            vault.store("policy_${policy.id}", bytes).getOrThrow()
            val retrieved = vault.retrieve("policy_${policy.id}").getOrThrow()!!
            val decoded = json.decodeFromString<Policy>(retrieved.decodeToString())
            decoded == policy
        }
    }

    @Test
    fun dePinContributionRoundTrip() = runTest {
        forAll(Arb.dePinContribution()) { contribution ->
            val bytes = json.encodeToString(contribution).encodeToByteArray()
            vault.store("tx_depin_${contribution.sessionId}", bytes).getOrThrow()
            val retrieved = vault.retrieve("tx_depin_${contribution.sessionId}").getOrThrow()!!
            val decoded = json.decodeFromString<DePinContribution>(retrieved.decodeToString())
            decoded == contribution
        }
    }

    @Test
    fun complianceRuleSetRoundTrip() = runTest {
        forAll(Arb.complianceRuleSet()) { ruleSet ->
            val bytes = json.encodeToString(ruleSet).encodeToByteArray()
            vault.store("compliance_rules_${ruleSet.country}", bytes).getOrThrow()
            val retrieved = vault.retrieve("compliance_rules_${ruleSet.country}").getOrThrow()!!
            val decoded = json.decodeFromString<ComplianceRuleSet>(retrieved.decodeToString())
            decoded == ruleSet
        }
    }

    @Test
    fun earningEventRoundTrip() = runTest {
        forAll(Arb.earningEvent()) { earningEvent ->
            val bytes = json.encodeToString(earningEvent).encodeToByteArray()
            vault.store("tx_earning_${earningEvent.id}", bytes).getOrThrow()
            val retrieved = vault.retrieve("tx_earning_${earningEvent.id}").getOrThrow()!!
            val decoded = json.decodeFromString<EarningEvent>(retrieved.decodeToString())
            decoded == earningEvent
        }
    }
}
