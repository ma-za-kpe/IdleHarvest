package com.maku.idleharvest.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun OnboardingFlow(onComplete: (OnboardingState) -> Unit) {
    var state by remember { mutableStateOf(OnboardingState()) }

    val stepOrder =
        listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.PERMISSIONS,
            OnboardingStep.WALLET_SETUP,
            OnboardingStep.GUARDRAILS,
            OnboardingStep.FIRST_SCAN,
        )

    fun advance() {
        val currentIndex = stepOrder.indexOf(state.currentStep)
        val completed = state.completedSteps + state.currentStep
        if (currentIndex < stepOrder.lastIndex) {
            state =
                state.copy(
                    currentStep = stepOrder[currentIndex + 1],
                    completedSteps = completed,
                )
        } else {
            val finalState = state.copy(completedSteps = completed, isComplete = true)
            state = finalState
            onComplete(finalState)
        }
    }

    fun back() {
        val currentIndex = stepOrder.indexOf(state.currentStep)
        if (currentIndex > 0) {
            state = state.copy(currentStep = stepOrder[currentIndex - 1])
        }
    }

    AnimatedContent(
        targetState = state.currentStep,
        transitionSpec = {
            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
        },
        label = "onboarding_step",
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> WelcomeScreen(onNext = { advance() })

            OnboardingStep.PERMISSIONS ->
                PermissionsScreen(
                    permissions = state.permissions,
                    onPermissionsUpdated = { updated ->
                        state = state.copy(permissions = updated)
                    },
                    onNext = { advance() },
                    onSkip = {
                        state =
                            state.copy(
                                completedSteps = state.completedSteps + OnboardingStep.PERMISSIONS,
                            )
                        advance()
                    },
                )

            OnboardingStep.WALLET_SETUP ->
                WalletSetupScreen(
                    wallet = state.wallet,
                    onWalletUpdated = { updated -> state = state.copy(wallet = updated) },
                    onNext = { advance() },
                    onSkip = {
                        state = state.copy(wallet = state.wallet.copy(skipped = true))
                        advance()
                    },
                    onBack = { back() },
                )

            OnboardingStep.GUARDRAILS ->
                GuardrailsScreen(
                    guardrails = state.guardrails,
                    onGuardrailsUpdated = { updated -> state = state.copy(guardrails = updated) },
                    onNext = { advance() },
                    onSkip = {
                        state = state.copy(guardrails = SafeOnboardingDefaults)
                        advance()
                    },
                    onBack = { back() },
                )

            OnboardingStep.FIRST_SCAN ->
                FirstScanScreen(
                    onScanComplete = {
                        state = state.copy(scanComplete = true)
                    },
                    onFinish = { advance() },
                    onBack = { back() },
                )
        }
    }
}
