package com.maku.idleharvest.ui.onboarding

import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.Policy

enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    WALLET_SETUP,
    GUARDRAILS,
    FIRST_SCAN,
}

data class PermissionsState(
    val bluetoothGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationExempt: Boolean = false,
)

data class WalletSetupState(
    val walletCreated: Boolean = false,
    val mnemonicConfirmed: Boolean = false,
    val skipped: Boolean = false,
)

data class GuardrailsState(
    val airtimeAutonomy: AutonomyLevel = AutonomyLevel.MANUAL,
    val depinAutonomy: AutonomyLevel = AutonomyLevel.MANUAL,
    val maxDailyUsdc: Double = 5.0,
    val maxSingleUsdc: Double = 1.0,
    val biometricThresholdUsdc: Double = 2.0,
    val skipped: Boolean = false,
)

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val completedSteps: Set<OnboardingStep> = emptySet(),
    val permissions: PermissionsState = PermissionsState(),
    val wallet: WalletSetupState = WalletSetupState(),
    val guardrails: GuardrailsState = GuardrailsState(),
    val scanComplete: Boolean = false,
    val isComplete: Boolean = false,
)

fun GuardrailsState.toAirtimePolicy(agentId: AgentId = AgentId("airtime")): Policy = Policy(
    id = "policy_airtime_onboarding",
    agentId = agentId,
    autonomyLevel = airtimeAutonomy,
    maxTransactionPerDay = maxDailyUsdc,
    maxTransactionSingle = maxSingleUsdc,
    resourceShareLimits = null,
    requireBiometricAbove = biometricThresholdUsdc,
    isActive = true,
)

fun GuardrailsState.toDepinPolicy(agentId: AgentId = AgentId("depin")): Policy = Policy(
    id = "policy_depin_onboarding",
    agentId = agentId,
    autonomyLevel = depinAutonomy,
    maxTransactionPerDay = maxDailyUsdc,
    maxTransactionSingle = maxSingleUsdc,
    resourceShareLimits = null,
    requireBiometricAbove = biometricThresholdUsdc,
    isActive = true,
)

/** Safe defaults applied when a user skips onboarding steps. */
val SafeOnboardingDefaults =
    GuardrailsState(
        airtimeAutonomy = AutonomyLevel.MANUAL,
        depinAutonomy = AutonomyLevel.MANUAL,
        maxDailyUsdc = 5.0,
        maxSingleUsdc = 1.0,
        biometricThresholdUsdc = 2.0,
        skipped = true,
    )
