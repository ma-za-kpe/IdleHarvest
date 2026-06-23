package com.maku.idleharvest

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.maku.idleharvest.ui.dashboard.AgentDashboardScreen
import com.maku.idleharvest.ui.dashboard.AgentDashboardState

@Composable
@Preview
fun App(dashboardState: AgentDashboardState = AgentDashboardState.demo()) {
    AgentDashboardScreen(dashboardState = dashboardState)
}
