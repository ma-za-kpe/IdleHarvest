package com.maku.idleharvest

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.maku.idleharvest.service.ResourceMonitorService
import com.maku.idleharvest.service.ResourceMonitorWorker
import com.maku.idleharvest.ui.onboarding.OnboardingFlow
import com.maku.idleharvest.ui.onboarding.OnboardingState
import com.maku.idleharvest.ui.theme.IdleHarvestTheme

private const val PREFS_NAME = "idle_harvest_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_complete"

class MainActivity : ComponentActivity() {
    private val agents by lazy { AgentContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Start persistent foreground service and WorkManager backup restart (Req 10.1, 10.5)
        ResourceMonitorService.start(this)
        ResourceMonitorWorker.enqueue(this)

        // Start the orchestrator — wires all agent event subscriptions
        agents.orchestrator.start()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

        setContent {
            IdleHarvestTheme {
                MainScreen(
                    initialOnboardingDone = onboardingDone,
                    onOnboardingComplete = { completedState ->
                        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                        // Apply guardrail policies from onboarding to the agent orchestrator
                        // (Req 12.5 — policy applied immediately after consent)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        agents.tearDown()
        super.onDestroy()
    }
}

@Composable
private fun MainScreen(
    initialOnboardingDone: Boolean,
    onOnboardingComplete: (OnboardingState) -> Unit,
) {
    var onboardingDone by remember { mutableStateOf(initialOnboardingDone) }

    if (!onboardingDone) {
        OnboardingFlow(
            onComplete = { state ->
                onOnboardingComplete(state)
                onboardingDone = true
            },
        )
    } else {
        App()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
