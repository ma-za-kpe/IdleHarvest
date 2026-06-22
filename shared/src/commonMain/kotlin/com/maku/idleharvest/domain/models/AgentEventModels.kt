package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * Events published through the AgentEventBus for inter-agent coordination.
 * Agents subscribe to specific event types to react to system changes.
 */
@Serializable
sealed class AgentEvent {
    /** Device resource profile has been updated by the Resource_Monitor. */
    @Serializable
    data class ResourceUpdated(val profile: ResourceProfile) : AgentEvent()

    /** An airtime/data bundle is approaching expiry. */
    @Serializable
    data class BundleExpiring(val bundle: AirtimeBundle, val expiryHours: Int) : AgentEvent()

    /** A new BLE peer has been discovered by the Mesh_Coordinator. */
    @Serializable
    data class PeerDiscovered(val peer: Peer) : AgentEvent()

    /** An earning event has been completed and settled. */
    @Serializable
    data class EarningCompleted(val event: EarningEvent) : AgentEvent()

    /** An agent attempted an action that violated a policy. */
    @Serializable
    data class PolicyViolation(val agentId: AgentId, val action: AgentAction) : AgentEvent()

    /** Device thermal state has changed (affects inference and agent activity). */
    @Serializable
    data class ThermalStateChanged(val state: ThermalState) : AgentEvent()

    /** Network connectivity state has changed. */
    @Serializable
    data class ConnectivityChanged(val isOnline: Boolean) : AgentEvent()
}
