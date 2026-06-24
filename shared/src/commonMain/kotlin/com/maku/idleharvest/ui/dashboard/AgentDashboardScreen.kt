@file:Suppress(
    "TooManyFunctions",
    "MaxLineLength",
    "MagicNumber",
    "ImplicitDefaultLocale",
    "LongMethod",
    "LongParameterList",
)

package com.maku.idleharvest.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.EarningSource
import com.maku.idleharvest.domain.models.MeshState
import com.maku.idleharvest.infrastructure.HealthStatus
import com.maku.idleharvest.ui.theme.IdleHarvestBrand
import com.maku.idleharvest.ui.theme.IdleHarvestDimens
import com.maku.idleharvest.ui.theme.IdleHarvestTheme
import kotlin.math.round

private val ColorActive = Color(0xFF22C55E)
private val ColorInactive = Color(0xFF94A3B8)
private val ColorWarn = Color(0xFFF59E0B)
private val ColorError = Color(0xFFEF4444)
private val DotSizeLarge = 12.dp
private val DotSizeSmall = 8.dp
private val SpaceXXS = 4.dp

@Composable
fun AgentDashboardScreen(dashboardState: AgentDashboardState = AgentDashboardState.demo()) {
    AirtimeDashboardScreenContent(dashboardState = dashboardState)
}

@Composable
fun AgentDashboardScreen(
    dashboardState: AgentDashboardState,
    phoneAirtimeBalance: AirtimeBalance?,
    airtimeProbeState: AirtimeProbeState,
    onRequestAirtimeBalance: (String) -> Unit,
    onTriggerManualSale: () -> Unit,
) {
    AirtimeDashboardScreenContent(
        dashboardState = dashboardState,
        phoneAirtimeBalance = phoneAirtimeBalance,
        airtimeProbeState = airtimeProbeState,
        onRequestAirtimeBalance = onRequestAirtimeBalance,
        onTriggerManualSale = onTriggerManualSale,
    )
}

@Composable
private fun AirtimeDashboardScreenContent(
    dashboardState: AgentDashboardState,
    phoneAirtimeBalance: AirtimeBalance? = null,
    airtimeProbeState: AirtimeProbeState = AirtimeProbeState.Idle,
    onRequestAirtimeBalance: (String) -> Unit = {},
    onTriggerManualSale: () -> Unit = {},
) {
    IdleHarvestTheme {
        var selectedAgentName by remember { mutableStateOf(dashboardState.agents.firstOrNull()?.name) }
        val selectedAgent =
            dashboardState.agents.firstOrNull { it.name == selectedAgentName }
                ?: dashboardState.agents.firstOrNull()
        val sellNudge = dashboardState.computeSellNudge()
        val liveAirtimeBalance = phoneAirtimeBalance

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DashboardHeader(headline = dashboardState.headline)
            Spacer(Modifier.height(IdleHarvestDimens.SpaceXL))
            EarningsSummaryCard(summary = dashboardState.earningsSummary)
            sellNudge?.let {
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                SellOpportunityCard(nudge = it)
            }
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            liveAirtimeBalance?.let { balance ->
                AirtimeBalanceCard(balance = balance)
            } ?: AirtimeBalanceUnavailableCard()
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            AirtimePhoneControlCard(
                balance = liveAirtimeBalance,
                probeState = airtimeProbeState,
                onRequestAirtimeBalance = onRequestAirtimeBalance,
                onTriggerManualSale = onTriggerManualSale,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
            SectionLabel("Active Agents (${dashboardState.activeAgentCount}/${dashboardState.totalAgentCount})")
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            dashboardState.agents.forEach { agent ->
                AgentCard(
                    agent = agent,
                    onClick = { selectedAgentName = agent.name },
                )
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            }
            selectedAgent?.let {
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                AgentInspectorCard(
                    dashboardState = dashboardState,
                    agent = it,
                    phoneAirtimeBalance = liveAirtimeBalance,
                    airtimeProbeState = airtimeProbeState,
                    onRequestAirtimeBalance = onRequestAirtimeBalance,
                    onTriggerManualSale = onTriggerManualSale,
                )
            }
            Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
            SectionLabel("System Health")
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            SystemHealthCard(dashboardState)
            if (dashboardState.activePeers.isNotEmpty()) {
                Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
                SectionLabel("Connected Peers")
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                PeerRosterCard(dashboardState.activePeers)
            }
            if (dashboardState.recentTransactions.isNotEmpty()) {
                Spacer(Modifier.height(IdleHarvestDimens.SpaceLG))
                SectionLabel("Recent Transactions")
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                RecentTransactionsCard(dashboardState.recentTransactions)
            }
            Spacer(Modifier.height(IdleHarvestDimens.SpaceXXL))
        }
    }
}

@Composable
private fun DashboardHeader(headline: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(
                vertical = IdleHarvestDimens.SpaceLG,
                horizontal = IdleHarvestDimens.ScreenPaddingHorizontal,
            ),
    ) {
        Column {
            Text(
                text = IdleHarvestBrand.APP_NAME,
                style = IdleHarvestBrand.LogoTextStyle,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun EarningsSummaryCard(summary: com.maku.idleharvest.domain.models.EarningsSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(IdleHarvestDimens.CardPadding)) {
            Text(
                text = "Total Earnings",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SpaceXXS))
            Text(
                text = "${formatMoney(summary.totalEarnedUsdc)} USDC",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            ResponsiveRow(
                compact = true,
                spacing = IdleHarvestDimens.SpaceSM,
                items = listOf(
                    { modifier ->
                        SummaryMetric("Last 24h", "${formatMoney(summary.last24hUsdc)} USDC", modifier)
                    },
                    { modifier ->
                        SummaryMetric("Last 7d", "${formatMoney(summary.last7dUsdc)} USDC", modifier)
                    },
                ),
            )
            Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
            summary.bySource.entries.sortedByDescending { it.value }.forEach { (source, amount) ->
                Text(
                    text = "${sourceLabel(source)}: ${formatMoney(amount)} USDC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
    )
}

@Composable
private fun AgentCard(agent: DashboardAgentStatus, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IdleHarvestDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(DotSizeLarge)
                    .clip(CircleShape)
                    .background(agentColor(agent.state)),
            )
            Spacer(Modifier.width(IdleHarvestDimens.SpaceSM))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = agent.statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = agentColor(agent.state),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun AgentInspectorCard(
    dashboardState: AgentDashboardState,
    agent: DashboardAgentStatus,
    phoneAirtimeBalance: AirtimeBalance?,
    airtimeProbeState: AirtimeProbeState,
    onRequestAirtimeBalance: (String) -> Unit,
    onTriggerManualSale: () -> Unit,
) {
    val sellNudge = dashboardState.computeSellNudge()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Column(
            modifier = Modifier.padding(IdleHarvestDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceSM),
        ) {
            Text(
                text = "${agent.name} live preview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = agentPreviewCopy(agent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (agent.name == "Airtime Agent") {
                Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
                phoneAirtimeBalance?.let { balance ->
                    AirtimeBalanceCard(balance = balance)
                } ?: AirtimeBalanceUnavailableCard()
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                AirtimePhoneControlCard(
                    balance = phoneAirtimeBalance,
                    probeState = airtimeProbeState,
                    onRequestAirtimeBalance = onRequestAirtimeBalance,
                    onTriggerManualSale = onTriggerManualSale,
                )
                Spacer(Modifier.height(IdleHarvestDimens.SpaceSM))
                sellNudge?.let {
                    SellOpportunityCard(nudge = it)
                } ?: SellOpportunityCard(
                    nudge =
                    SellNudge(
                        title = "No sell opportunity yet",
                        body = "The Airtime Agent is still waiting for a bundle close enough to expiry to recommend a sale.",
                        severityLabel = "Hold",
                    ),
                )
            }
            ResponsiveRow(
                compact = true,
                spacing = IdleHarvestDimens.SpaceSM,
                items = listOf(
                    { modifier -> PreviewMetric("Runtime", agent.statusLabel, modifier) },
                    { modifier -> PreviewMetric("Mesh", meshLabel(dashboardState.meshState), modifier) },
                    { modifier -> PreviewMetric("Battery", batteryLabel(dashboardState.resourceProfile.batteryLevel, dashboardState.resourceProfile.isCharging), modifier) },
                ),
            )
            dashboardState.recentTransactions.firstOrNull()?.let { event ->
                Text(
                    text = "Latest settlement: ${sourceLabel(event.source)} for ${formatMoney(event.amountUsdc)} USDC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SellOpportunityCard(nudge: SellNudge, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
        CardDefaults.cardColors(
            containerColor =
            if (nudge.severityLabel == "Sell now") {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(IdleHarvestDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceXS),
        ) {
            Text(
                text = nudge.severityLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = nudge.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = nudge.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AirtimeBalanceCard(balance: AirtimeBalance, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(IdleHarvestDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceXS),
        ) {
            Text(
                text = "Airtime balance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${formatUnits(balance.amountUnits)} ${balance.currency}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Carrier: ${balance.carrier}${balance.expiryTimestamp?.let { " · expires soon" } ?: " · verified from phone"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AirtimeBalanceUnavailableCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(IdleHarvestDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceXS),
        ) {
            Text(
                text = "Airtime balance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Unavailable from phone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tap the probe below to read a live balance from the phone using your carrier's USSD code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AirtimePhoneControlCard(
    balance: AirtimeBalance?,
    probeState: AirtimeProbeState,
    onRequestAirtimeBalance: (String) -> Unit,
    onTriggerManualSale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ussdCode by remember { mutableStateOf("*124#") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Column(
            modifier = Modifier.padding(IdleHarvestDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceSM),
        ) {
            Text(
                text = "Phone balance probe",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Enter your carrier USSD code to query the phone directly. The app never invents a balance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = ussdCode,
                onValueChange = { ussdCode = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("USSD code") },
            )
            ResponsiveRow(
                compact = true,
                spacing = IdleHarvestDimens.SpaceSM,
                items = listOf(
                    { itemModifier ->
                        OutlinedButton(
                            onClick = { onRequestAirtimeBalance(ussdCode) },
                            modifier = itemModifier,
                        ) {
                            Text("Read from phone")
                        }
                    },
                    { itemModifier ->
                        Button(
                            onClick = onTriggerManualSale,
                            modifier = itemModifier,
                            enabled = balance != null,
                        ) {
                            Text("Trigger sale")
                        }
                    },
                ),
            )
            when (probeState) {
                AirtimeProbeState.Idle -> Text(
                    text = "Waiting for a probe request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AirtimeProbeState.Loading -> Text(
                    text = "Querying the phone now...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is AirtimeProbeState.Success -> {
                    Text(
                        text = "Phone response: ${probeState.rawResponse}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    probeState.balance?.let {
                        Text(
                            text = "Parsed balance: ${formatUnits(it.amountUnits)} ${it.currency}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is AirtimeProbeState.Error -> Text(
                    text = probeState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PreviewMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(SpaceXXS))
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun agentPreviewCopy(agent: DashboardAgentStatus): String = when (agent.state) {
    com.maku.idleharvest.domain.models.AgentState.IDLE ->
        "${agent.name} is waiting for the next trigger while the device stays within policy."
    com.maku.idleharvest.domain.models.AgentState.EVALUATING ->
        "${agent.name} is scoring live device signals and deciding whether the next action is worth taking."
    com.maku.idleharvest.domain.models.AgentState.EXECUTING ->
        "${agent.name} is currently acting on an approved opportunity and writing the result into the audit trail."
    com.maku.idleharvest.domain.models.AgentState.PAUSED ->
        "${agent.name} is paused by guardrails or device conditions and will resume when the policy engine allows it."
    com.maku.idleharvest.domain.models.AgentState.ERROR ->
        "${agent.name} hit a runtime issue and needs attention before it can continue earning."
}

private data class SellNudge(
    val title: String,
    val body: String,
    val severityLabel: String,
)

private fun AgentDashboardState.computeSellNudge(): SellNudge? {
    val now = resourceProfile.timestamp
    val expiryWindowMs = 72L * 60L * 60L * 1000L

    val airtimeExpiry = resourceProfile.airtimeBalance?.expiryTimestamp
    val airtimeHoursLeft =
        airtimeExpiry
            ?.let { ((it - now) / (60L * 60L * 1000L)).coerceAtLeast(0L) }

    val closestBundle =
        resourceProfile.dataBundles
            .minByOrNull { it.expiryTimestamp }
    val bundleHoursLeft =
        closestBundle
            ?.let { ((it.expiryTimestamp - now) / (60L * 60L * 1000L)).coerceAtLeast(0L) }

    val shouldSellAirtime = airtimeExpiry != null && airtimeExpiry - now in 0..expiryWindowMs
    val shouldSellBundle = closestBundle != null && closestBundle.expiryTimestamp - now in 0..expiryWindowMs

    return when {
        shouldSellAirtime -> SellNudge(
            title = "Airtime looks eligible to sell",
            body = "Your airtime expires in about ${airtimeHoursLeft ?: 0}h. The Airtime Agent can nudge you to sell before value decays.",
            severityLabel = "Sell now",
        )
        shouldSellBundle -> SellNudge(
            title = "Data bundle looks eligible to sell",
            body = "Your next bundle expires in about ${bundleHoursLeft ?: 0}h. The Airtime Agent can suggest a sale or transfer while it still has value.",
            severityLabel = "Sell now",
        )
        else -> null
    }
}

@Composable
private fun ResponsiveRow(
    compact: Boolean,
    spacing: androidx.compose.ui.unit.Dp,
    items: List<@Composable (itemModifier: Modifier) -> Unit>,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { item -> item(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items.forEach { item -> item(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SystemHealthCard(dashboardState: AgentDashboardState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IdleHarvestDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceSM),
        ) {
            ResponsiveRow(
                compact = true,
                spacing = IdleHarvestDimens.SpaceSM,
                items = listOf(
                    { modifier -> HealthMetric("Overall", dashboardState.healthStatus.name, dashboardState.healthStatus != HealthStatus.RED, modifier) },
                    { modifier -> HealthMetric("Battery", batteryLabel(dashboardState.resourceProfile.batteryLevel, dashboardState.resourceProfile.isCharging), batteryHealthy(dashboardState.resourceProfile.batteryLevel, dashboardState.resourceProfile.isCharging), modifier) },
                    { modifier -> HealthMetric("Thermal", dashboardState.resourceProfile.thermalState.name, dashboardState.resourceProfile.thermalState != com.maku.idleharvest.domain.models.ThermalState.CRITICAL, modifier) },
                ),
            )
            ResponsiveRow(
                compact = true,
                spacing = IdleHarvestDimens.SpaceSM,
                items = listOf(
                    { modifier -> HealthMetric("Mesh", meshLabel(dashboardState.meshState), dashboardState.meshState != MeshState.ERROR, modifier) },
                    { modifier -> HealthMetric("Peers", "${dashboardState.activePeers.size} connected", dashboardState.activePeers.isNotEmpty() || dashboardState.meshState != MeshState.CONNECTED, modifier) },
                ),
            )
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String, ok: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(DotSizeSmall)
                .clip(CircleShape)
                .background(if (ok) ColorActive else ColorWarn),
        )
        Spacer(Modifier.height(SpaceXXS))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PeerRosterCard(peers: List<com.maku.idleharvest.domain.models.Peer>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(IdleHarvestDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceSM)) {
            peers.take(3).forEach { peer ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(peer.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "${peer.connectionState.name}  ${peer.signalStrength} dBm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = peer.id.value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionsCard(events: List<EarningEvent>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IdleHarvestDimens.ScreenPaddingHorizontal),
        elevation = CardDefaults.cardElevation(defaultElevation = IdleHarvestDimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(IdleHarvestDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(IdleHarvestDimens.SpaceSM)) {
            events.take(3).forEach { event ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sourceLabel(event.source), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = event.agentId.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${formatMoney(event.amountUsdc)} USDC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(IdleHarvestDimens.SpaceXS))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun agentColor(state: com.maku.idleharvest.domain.models.AgentState): Color = when (state) {
    com.maku.idleharvest.domain.models.AgentState.IDLE -> ColorInactive
    com.maku.idleharvest.domain.models.AgentState.EVALUATING -> ColorWarn
    com.maku.idleharvest.domain.models.AgentState.EXECUTING -> ColorActive
    com.maku.idleharvest.domain.models.AgentState.PAUSED -> ColorWarn
    com.maku.idleharvest.domain.models.AgentState.ERROR -> ColorError
}

private fun sourceLabel(source: EarningSource): String = when (source) {
    EarningSource.AIRTIME_SALE -> "Airtime sale"
    EarningSource.DEPIN_REWARD -> "DePIN reward"
    EarningSource.MESH_SERVICE -> "Mesh service"
    EarningSource.NANOPAYMENT -> "Nanopayment"
}

private fun meshLabel(state: MeshState): String = when (state) {
    MeshState.IDLE -> "Idle"
    MeshState.SCANNING -> "Scanning"
    MeshState.CONNECTED -> "Connected"
    MeshState.ERROR -> "Error"
}

private fun batteryLabel(level: Int, charging: Boolean): String = when {
    level < 0 -> "Unknown"
    charging -> "$level% charging"
    else -> "$level%"
}

private fun batteryHealthy(level: Int, charging: Boolean): Boolean = charging || level > 20

private fun formatMoney(value: Double): String {
    val scaled = round(value * 100.0).toLong()
    val whole = scaled / 100
    val fraction = kotlin.math.abs(scaled % 100)
    return buildString {
        append(whole)
        append('.')
        if (fraction < 10) {
            append('0')
        }
        append(fraction)
    }
}

private fun formatUnits(value: Long): String {
    val formatted = value.toString()
    return formatted.chunked(3).joinToString(",")
}
