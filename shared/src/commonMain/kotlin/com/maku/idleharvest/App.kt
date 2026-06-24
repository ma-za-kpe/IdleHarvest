package com.maku.idleharvest

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.ui.dashboard.AgentDashboardScreen
import com.maku.idleharvest.ui.dashboard.AgentDashboardState
import com.maku.idleharvest.ui.dashboard.AirtimeProbeState

@Composable
@Preview
fun App(
    dashboardState: AgentDashboardState = AgentDashboardState.demo(),
    phoneAirtimeBalance: AirtimeBalance? = null,
    airtimeProbeState: AirtimeProbeState = AirtimeProbeState.Idle,
    onRequestAirtimeBalance: (String) -> Unit = {},
    onTriggerManualSale: () -> Unit = {},
) {
    AgentDashboardScreen(
        dashboardState = dashboardState,
        phoneAirtimeBalance = phoneAirtimeBalance,
        airtimeProbeState = airtimeProbeState,
        onRequestAirtimeBalance = onRequestAirtimeBalance,
        onTriggerManualSale = onTriggerManualSale,
    )
}
