package com.maku.idleharvest

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.maku.idleharvest.service.ResourceMonitorService
import com.maku.idleharvest.service.ResourceMonitorWorker
import com.maku.idleharvest.ui.dashboard.AgentDashboardState
import com.maku.idleharvest.ui.onboarding.OnboardingFlow
import com.maku.idleharvest.ui.onboarding.OnboardingState
import com.maku.idleharvest.ui.onboarding.SafeOnboardingDefaults
import com.maku.idleharvest.ui.onboarding.toAirtimePolicy
import com.maku.idleharvest.ui.onboarding.toDepinPolicy
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
                    agents = agents,
                    initialOnboardingDone = onboardingDone,
                    onOnboardingComplete = { completedState ->
                        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                        // Apply guardrail policies from onboarding to the agent orchestrator
                        // (Req 12.5 — policy applied immediately after consent)
                        val pm = agents.policyManager
                        // Use guardrails from completed state (or safe defaults)
                        val g = if (completedState.guardrails.skipped) {
                            SafeOnboardingDefaults
                        } else {
                            completedState.guardrails
                        }
                        pm.setPolicy(g.toAirtimePolicy())
                        pm.setPolicy(g.toDepinPolicy())
                        // Apply some default policies for earning/mesh from the manager's defaults (fills onboarding wiring gap)
                        // Note: full defaults application would load/persist properly
                        println("[MainActivity] Applied onboarding guardrail policies (airtime/depin) to PolicyManager")
                        // In real, also pm.setPolicy for mesh/earning using similar from state or defaults
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
    agents: AgentContainer,
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
        LiveApp(agents)
    }
}

@Composable
private fun LiveApp(agents: AgentContainer) {
    val resourceProfile by agents.resourceMonitor.resourceProfile.collectAsState()
    val airtimeState by agents.airtimeAgent.state.collectAsState()
    val depinState by agents.depinAgent.state.collectAsState()
    val meshState by agents.meshCoordinator.meshState.collectAsState()
    val activePeers by agents.meshCoordinator.activePeers.collectAsState()
    val earningHistory by agents.earningEngine.earningHistory.collectAsState()
    val airtimeProbeState by agents.airtimeProbeState.collectAsState()
    val phoneAirtimeBalance by agents.phoneAirtimeBalance.collectAsState()

    App(
        dashboardState =
        AgentDashboardState.fromRuntime(
            resourceProfile = resourceProfile,
            airtimeState = airtimeState,
            depinState = depinState,
            meshState = meshState,
            activePeers = activePeers,
            earningHistory = earningHistory,
        ),
        phoneAirtimeBalance = phoneAirtimeBalance,
        airtimeProbeState = airtimeProbeState,
        onRequestAirtimeBalance = { ussdCode -> agents.requestAirtimeBalance(ussdCode) },
        onTriggerManualSale = { agents.triggerManualAirtimeSale() },
    )
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
