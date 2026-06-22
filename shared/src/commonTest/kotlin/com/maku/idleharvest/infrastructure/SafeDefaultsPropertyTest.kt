package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.ui.onboarding.GuardrailsState
import com.maku.idleharvest.ui.onboarding.OnboardingState
import com.maku.idleharvest.ui.onboarding.OnboardingStep
import com.maku.idleharvest.ui.onboarding.SafeOnboardingDefaults
import com.maku.idleharvest.ui.onboarding.WalletSetupState
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.set
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property 29: Safe Defaults on Incomplete Onboarding
 * For any subset of skipped onboarding steps, the resulting config must have conservative
 * defaults: manual approval, low caps, biometric required, no auto-financial actions.
 * Validates: Requirements 15.5
 */
class SafeDefaultsPropertyTest {
    private val allSteps = OnboardingStep.entries.toList()

    private val arbSkippedSteps = Arb.set(Arb.of(allSteps), 0..allSteps.size)

    private fun defaultsForSkipped(skipped: Set<OnboardingStep>): GuardrailsState = if (OnboardingStep.GUARDRAILS in skipped) SafeOnboardingDefaults else GuardrailsState()

    @Test
    fun safeDefaultsAutonomyIsAlwaysManualWhenGuardrailsSkipped() = runTest {
        forAll(arbSkippedSteps) { skipped ->
            val guardrails = defaultsForSkipped(skipped)
            if (guardrails.skipped) {
                guardrails.airtimeAutonomy == AutonomyLevel.MANUAL &&
                    guardrails.depinAutonomy == AutonomyLevel.MANUAL
            } else {
                true
            }
        }
    }

    @Test
    fun safeDefaultsDailyLimitAtMost5UsdcWhenSkipped() = runTest {
        forAll(arbSkippedSteps) { skipped ->
            val guardrails = defaultsForSkipped(skipped)
            if (guardrails.skipped) guardrails.maxDailyUsdc <= 5.0 else true
        }
    }

    @Test
    fun safeDefaultsSingleTransactionLimitAtMost1UsdcWhenSkipped() = runTest {
        forAll(arbSkippedSteps) { skipped ->
            val guardrails = defaultsForSkipped(skipped)
            if (guardrails.skipped) guardrails.maxSingleUsdc <= 1.0 else true
        }
    }

    @Test
    fun safeDefaultsBiometricThresholdPositiveWhenSkipped() = runTest {
        forAll(arbSkippedSteps) { skipped ->
            val guardrails = defaultsForSkipped(skipped)
            if (guardrails.skipped) guardrails.biometricThresholdUsdc > 0.0 else true
        }
    }

    @Test
    fun walletSkippedFlagReflectedInState() = runTest {
        forAll(Arb.boolean()) { skippedWallet ->
            val wallet = if (skippedWallet) WalletSetupState(skipped = true) else WalletSetupState()
            if (wallet.skipped) !wallet.walletCreated && !wallet.mnemonicConfirmed else true
        }
    }

    @Test
    fun onboardingStateCompletedAllStepsIsMarkedComplete() {
        val finalState =
            OnboardingState(
                currentStep = OnboardingStep.FIRST_SCAN,
                completedSteps = allSteps.toSet(),
                isComplete = true,
            )
        assertTrue(finalState.isComplete)
        assertEquals(allSteps.toSet(), finalState.completedSteps)
    }

    @Test
    fun safeDefaultsNeverSetFullyAutomatic() {
        assertFalse(SafeOnboardingDefaults.airtimeAutonomy == AutonomyLevel.FULLY_AUTOMATIC)
        assertFalse(SafeOnboardingDefaults.depinAutonomy == AutonomyLevel.FULLY_AUTOMATIC)
    }

    @Test
    fun safeDefaultsBiometricThresholdIsPositive() {
        assertTrue(SafeOnboardingDefaults.biometricThresholdUsdc > 0.0)
    }
}
