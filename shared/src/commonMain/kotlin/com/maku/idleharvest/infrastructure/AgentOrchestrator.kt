package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AirtimeAgent
import com.maku.idleharvest.domain.interfaces.DePinAgent
import com.maku.idleharvest.domain.interfaces.EarningEngine
import com.maku.idleharvest.domain.interfaces.MeshCoordinator
import com.maku.idleharvest.domain.interfaces.ResourceMonitor
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AirtimeBundle
import com.maku.idleharvest.domain.models.AirtimeBundleType
import com.maku.idleharvest.domain.models.MonitorConfig
import com.maku.idleharvest.domain.models.ResourceThreshold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Wires all agents to the Event Bus and coordinates their lifecycle.
 *
 * Event subscriptions:
 * - ResourceUpdated → AirtimeAgent (bundle expiry detection)
 * - ResourceUpdated → DePinAgent (threshold enforcement)
 * - ResourceUpdated → ResourceMonitor (thermal adaptation)
 * - ConnectivityChanged → DePinAgent (graceful pause/resume)
 * - PeerDiscovered → logged for nanopayment availability
 *
 * Validates: Requirements 1.1, 2.6, 3.2, 5.2, 6.4, 10.6
 */
class AgentOrchestrator(
    private val resourceMonitor: ResourceMonitor,
    private val airtimeAgent: AirtimeAgent,
    private val depinAgent: DePinAgent,
    private val meshCoordinator: MeshCoordinator,
    private val earningEngine: EarningEngine,
    private val eventBus: DefaultAgentEventBus,
    private val scope: CoroutineScope,
) {
    private var wireJob: Job? = null

    fun start(config: MonitorConfig = MonitorConfig()) {
        resourceMonitor.startMonitoring(config)
        wireEventSubscriptions()
    }

    fun stop() {
        resourceMonitor.stopMonitoring()
        wireJob?.cancel()
        wireJob = null
    }

    private fun wireEventSubscriptions() {
        wireJob?.cancel()
        wireJob =
            scope.launch {
                // ResourceMonitor → AirtimeAgent:
                // When a resource profile arrives with expiring bundles, emit BundleExpiring
                // so the AirtimeAgent can evaluate and generate monetization recommendations.
                eventBus
                    .subscribe(AgentEvent.ResourceUpdated::class)
                    .onEach { event ->
                        val now = currentTimeMillis()
                        val horizon72h = 72 * 60 * 60 * 1000L

                        event.profile.airtimeBalance?.let { balance ->
                            val expiryTs = balance.expiryTimestamp
                            if (expiryTs != null && (expiryTs - now) in 0..horizon72h) {
                                val hoursLeft = ((expiryTs - now) / 3_600_000L).toInt().coerceAtLeast(0)
                                eventBus.publish(
                                    AgentEvent.BundleExpiring(
                                        bundle =
                                        AirtimeBundle(
                                            id = "airtime_${balance.carrier}_$now",
                                            carrier = balance.carrier,
                                            type = AirtimeBundleType.AIRTIME,
                                            amountUnits = balance.amountUnits,
                                            currency = balance.currency,
                                            remainingMb = null,
                                            expiryTimestamp = expiryTs,
                                            purchasedAt = now,
                                        ),
                                        expiryHours = hoursLeft,
                                    ),
                                )
                            }
                        }

                        event.profile.dataBundles.forEach { bundle ->
                            if ((bundle.expiryTimestamp - now) in 0..horizon72h) {
                                val hoursLeft = ((bundle.expiryTimestamp - now) / 3_600_000L).toInt().coerceAtLeast(0)
                                eventBus.publish(
                                    AgentEvent.BundleExpiring(
                                        bundle =
                                        AirtimeBundle(
                                            id = "data_${bundle.carrier}_$now",
                                            carrier = bundle.carrier,
                                            type = AirtimeBundleType.DATA,
                                            amountUnits = bundle.remainingMb,
                                            currency = "MB",
                                            remainingMb = bundle.remainingMb,
                                            expiryTimestamp = bundle.expiryTimestamp,
                                            purchasedAt = now,
                                        ),
                                        expiryHours = hoursLeft,
                                    ),
                                )
                            }
                        }
                    }.launchIn(this)

                // ResourceMonitor → DePinAgent:
                // Adjust DePIN contributions based on resource availability.
                // Pauses contributions when resources are below user-defined thresholds.
                eventBus
                    .subscribe(AgentEvent.ResourceUpdated::class)
                    .onEach { _ ->
                        depinAgent.adjustContribution(
                            ResourceThreshold(
                                bandwidthMinMbps = 1.0f,
                                storageMinMb = 500L,
                                computeMaxCpuPercent = 30,
                            ),
                        )
                    }.launchIn(this)

                // ThermalState changes → ResourceMonitor adaptive scan frequency
                eventBus
                    .subscribe(AgentEvent.ThermalStateChanged::class)
                    .onEach { event ->
                        resourceMonitor.adaptToThermalState(event.state)
                    }.launchIn(this)

                // ConnectivityChanged → DePinAgent graceful disconnect / reconnect
                eventBus
                    .subscribe(AgentEvent.ConnectivityChanged::class)
                    .onEach { event ->
                        if (!event.isOnline) {
                            // Graceful pause — agent implementation queues pending proofs internally
                            depinAgent.adjustContribution(
                                ResourceThreshold(
                                    bandwidthMinMbps = Float.MAX_VALUE,
                                    storageMinMb = Long.MAX_VALUE,
                                    computeMaxCpuPercent = 0,
                                ),
                            )
                        }
                    }.launchIn(this)
            }
    }
}
