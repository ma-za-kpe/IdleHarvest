@file:Suppress("LongParameterList", "LongMethod", "MagicNumber", "TopLevelPropertyNaming")

package com.maku.idleharvest.ui.dashboard

import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AgentState
import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.EarningSource
import com.maku.idleharvest.domain.models.EarningsSummary
import com.maku.idleharvest.domain.models.MeshState
import com.maku.idleharvest.domain.models.Peer
import com.maku.idleharvest.domain.models.PeerConnectionState
import com.maku.idleharvest.domain.models.PeerId
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.domain.models.ThermalState
import com.maku.idleharvest.infrastructure.HealthStatus
import com.maku.idleharvest.infrastructure.currentTimeMillis

data class DashboardAgentStatus(
    val name: String,
    val description: String,
    val state: AgentState,
) {
    val active: Boolean
        get() = state != AgentState.PAUSED && state != AgentState.ERROR

    val statusLabel: String
        get() =
            when (state) {
                AgentState.IDLE -> "Idle"
                AgentState.EVALUATING -> "Evaluating"
                AgentState.EXECUTING -> "Executing"
                AgentState.PAUSED -> "Paused"
                AgentState.ERROR -> "Error"
            }
}

data class AgentDashboardState(
    val resourceProfile: ResourceProfile,
    val agents: List<DashboardAgentStatus>,
    val earningsSummary: EarningsSummary,
    val activePeers: List<Peer>,
    val meshState: MeshState,
    val recentTransactions: List<EarningEvent>,
) {
    val activeAgentCount: Int
        get() = agents.count { it.active }

    val totalAgentCount: Int
        get() = agents.size

    val headline: String
        get() =
            when {
                agents.any { it.state == AgentState.ERROR } -> "Agent issue detected"
                activeAgentCount == totalAgentCount -> "Agents are active and earning"
                activeAgentCount > 0 -> "Some agents are active"
                else -> "Agents are idle"
            }

    val healthStatus: HealthStatus
        get() = deriveHealthStatus()

    companion object {
        fun fromRuntime(
            resourceProfile: ResourceProfile,
            airtimeState: AgentState,
            depinState: AgentState,
            meshState: MeshState,
            activePeers: List<Peer>,
            earningHistory: List<EarningEvent>,
            now: Long = currentTimeMillis(),
        ): AgentDashboardState {
            val recentTransactions = earningHistory.sortedByDescending { it.timestamp }
            val agentStatuses =
                listOf(
                    DashboardAgentStatus(
                        "Airtime Agent",
                        "Sells expiring airtime for USDC",
                        airtimeState,
                    ),
                    DashboardAgentStatus(
                        "DePIN Agent",
                        "Shares idle bandwidth to earn",
                        depinState,
                    ),
                    DashboardAgentStatus(
                        "Mesh Coordinator",
                        "Pools nearby devices for collective earnings",
                        meshState.toAgentState(),
                    ),
                )

            return AgentDashboardState(
                resourceProfile = resourceProfile,
                agents = agentStatuses,
                earningsSummary = earningHistory.toEarningsSummary(now),
                activePeers = activePeers,
                meshState = meshState,
                recentTransactions = recentTransactions,
            )
        }

        fun demo(now: Long = currentTimeMillis()): AgentDashboardState {
            val earnings =
                listOf(
                    EarningEvent(
                        id = "demo_airtime_1",
                        source = EarningSource.AIRTIME_SALE,
                        amountUsdc = 2.45,
                        amountLocal = 5000.0,
                        localCurrency = "NGN",
                        agentId = AgentId("airtime-agent"),
                        timestamp = now - 2 * HOUR_MS,
                    ),
                    EarningEvent(
                        id = "demo_depin_1",
                        source = EarningSource.DEPIN_REWARD,
                        amountUsdc = 1.10,
                        amountLocal = null,
                        localCurrency = null,
                        agentId = AgentId("depin-agent"),
                        timestamp = now - HOUR_MS,
                    ),
                )

            return AgentDashboardState(
                resourceProfile =
                ResourceProfile(
                    airtimeBalance =
                    AirtimeBalance(
                        carrier = "DemoCarrier",
                        amountUnits = 2500L,
                        currency = "NGN",
                        expiryTimestamp = now + 48 * HOUR_MS,
                    ),
                    dataBundles =
                    listOf(
                        DataBundle(
                            carrier = "DemoCarrier",
                            remainingMb = 1200L,
                            totalMb = 2000L,
                            expiryTimestamp = now + 36 * HOUR_MS,
                            bundleType = "DATA",
                        ),
                    ),
                    availableBandwidthMbps = 24.5f,
                    freeStorageMb = 4096L,
                    idleComputePercent = 68,
                    batteryLevel = 72,
                    isCharging = true,
                    thermalState = ThermalState.COOL,
                    timestamp = now,
                ),
                agents =
                listOf(
                    DashboardAgentStatus("Airtime Agent", "Sells expiring airtime for USDC", AgentState.EVALUATING),
                    DashboardAgentStatus("DePIN Agent", "Shares idle bandwidth to earn", AgentState.EXECUTING),
                    DashboardAgentStatus("Mesh Coordinator", "Pooling nearby devices", AgentState.IDLE),
                ),
                earningsSummary = earnings.toEarningsSummary(now),
                activePeers =
                listOf(
                    Peer(
                        id = PeerId("peer-1"),
                        displayName = "Demo Peer",
                        resourceProfile = ResourceProfile.empty(),
                        signalStrength = -58,
                        connectionState = PeerConnectionState.CONNECTED,
                        lastSeen = now - 5 * MINUTE_MS,
                    ),
                ),
                meshState = MeshState.CONNECTED,
                recentTransactions = earnings.sortedByDescending { it.timestamp },
            )
        }
    }
}

fun List<EarningEvent>.toEarningsSummary(now: Long = currentTimeMillis()): EarningsSummary {
    val oneDayAgo = now - DAY_MS
    val sevenDaysAgo = now - (7 * DAY_MS)

    val bySource =
        groupBy { it.source }
            .mapValues { (_, events) -> events.sumOf { event -> event.amountUsdc } }

    return EarningsSummary(
        totalEarnedUsdc = sumOf { it.amountUsdc },
        last24hUsdc = filter { it.timestamp >= oneDayAgo }.sumOf { it.amountUsdc },
        last7dUsdc = filter { it.timestamp >= sevenDaysAgo }.sumOf { it.amountUsdc },
        bySource = bySource,
    )
}

fun AgentDashboardState.deriveHealthStatus(): HealthStatus = when {
    resourceProfile.thermalState == ThermalState.CRITICAL -> HealthStatus.RED
    resourceProfile.batteryLevel in 0..10 && !resourceProfile.isCharging -> HealthStatus.RED
    agents.any { it.state == AgentState.ERROR } -> HealthStatus.RED
    meshState == MeshState.ERROR -> HealthStatus.RED
    resourceProfile.thermalState == ThermalState.HOT -> HealthStatus.YELLOW
    resourceProfile.thermalState == ThermalState.WARM -> HealthStatus.YELLOW
    resourceProfile.batteryLevel in 11..20 && !resourceProfile.isCharging -> HealthStatus.YELLOW
    agents.any { it.state == AgentState.PAUSED } -> HealthStatus.YELLOW
    meshState == MeshState.SCANNING -> HealthStatus.YELLOW
    else -> HealthStatus.GREEN
}

private const val HOUR_MS = 60L * 60L * 1000L
private const val MINUTE_MS = 60L * 1000L
private const val DAY_MS = 24L * HOUR_MS

private fun MeshState.toAgentState(): AgentState = when (this) {
    MeshState.IDLE -> AgentState.IDLE
    MeshState.SCANNING -> AgentState.EVALUATING
    MeshState.CONNECTED -> AgentState.EXECUTING
    MeshState.ERROR -> AgentState.ERROR
}
