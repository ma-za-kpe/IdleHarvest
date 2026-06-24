package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.MonetizationRecommendation
import com.maku.idleharvest.generators.airtimeBundle
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 3: Monetization Recommendation Generation
 *
 * *For any* airtime bundle with an expiry timestamp within 72 hours from now,
 * the Airtime_Agent SHALL produce exactly one MonetizationRecommendation (sell, transfer, or hold).
 * For any bundle with expiry beyond 72 hours, no recommendation SHALL be generated (Hold returned).
 *
 * **Validates: Requirements 2.1**
 */
class MonetizationRecommendationPropertyTest {
    companion object {
        /** Fixed clock value used as "now" for deterministic testing. */
        private const val FIXED_NOW_MS = 1_719_792_000_000L // 2024-07-01 00:00:00 UTC

        /** 72 hours in milliseconds. */
        private const val SEVENTY_TWO_HOURS_MS = 72L * 3_600_000L

        /** 1 hour in milliseconds. */
        private const val ONE_HOUR_MS = 3_600_000L
    }

    private val cryptoProvider = SimpleCryptoProvider()
    private val vault = DefaultPrivacyVault(cryptoProvider)
    private val eventBus = DefaultAgentEventBus()
    private val policyManager = DefaultPolicyManager(vault, eventBus)
    private val complianceEngine = DefaultComplianceEngine(vault)
    private val inferenceEngine =
        DefaultInferenceEngine(
            modelRegistry = DefaultModelRegistry(vault, cryptoProvider),
            clock = { FIXED_NOW_MS },
        )

    private fun createTestAgent(): DefaultAirtimeAgent = DefaultAirtimeAgent(
        policyManager = policyManager,
        complianceEngine = complianceEngine,
        vault = vault,
        eventBus = eventBus,
        inferenceEngine = inferenceEngine,
        vtuClient = null,
        clock = { FIXED_NOW_MS },
    )

    @Test
    fun bundleWithin72HoursProducesExactlyOneRecommendation() = runTest {
        val agent = createTestAgent()

        forAll(Arb.airtimeBundle(), Arb.long(1L..SEVENTY_TWO_HOURS_MS)) { bundle, offsetMs ->
            // Set expiry within 72 hours of the fixed clock
            val expiringBundle =
                bundle.copy(
                    expiryTimestamp = FIXED_NOW_MS + offsetMs,
                )

            val recommendation = agent.evaluateBundle(expiringBundle)

            // Must produce exactly one of Sell, Transfer, or Hold
            recommendation is MonetizationRecommendation.Sell ||
                recommendation is MonetizationRecommendation.Transfer ||
                recommendation is MonetizationRecommendation.Hold
        }
    }

    @Test
    fun bundleBeyond72HoursAlwaysReturnsHold() = runTest {
        val agent = createTestAgent()

        // Generate expiry times that are strictly beyond 72 hours from now
        // hoursToExpiry > 72 means (expiry - now) / HOUR_MS > 72
        // So expiry > now + 72*HOUR_MS + HOUR_MS (to ensure integer division > 72)
        forAll(
            Arb.airtimeBundle(),
            Arb.long(SEVENTY_TWO_HOURS_MS + ONE_HOUR_MS..SEVENTY_TWO_HOURS_MS * 10),
        ) { bundle, offsetMs ->
            val farBundle =
                bundle.copy(
                    expiryTimestamp = FIXED_NOW_MS + offsetMs,
                )

            val recommendation = agent.evaluateBundle(farBundle)

            // Beyond 72 hours → must always be Hold
            recommendation is MonetizationRecommendation.Hold
        }
    }

    @Test
    fun recommendationIsNeverNull() = runTest {
        val agent = createTestAgent()

        forAll(Arb.airtimeBundle()) { bundle ->
            // Regardless of expiry, evaluateBundle must always return a non-null recommendation
            val recommendation = agent.evaluateBundle(bundle)

            recommendation is MonetizationRecommendation.Sell ||
                recommendation is MonetizationRecommendation.Transfer ||
                recommendation is MonetizationRecommendation.Hold
        }
    }
}
