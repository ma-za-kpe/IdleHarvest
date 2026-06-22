package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.AgentState
import com.maku.idleharvest.domain.models.ContributionSession
import com.maku.idleharvest.domain.models.CryptographicProof
import com.maku.idleharvest.domain.models.DePinContribution
import com.maku.idleharvest.domain.models.DePinNetwork
import com.maku.idleharvest.domain.models.ResourceThreshold
import com.maku.idleharvest.domain.models.ResourceType
import kotlinx.coroutines.flow.StateFlow

/**
 * AI agent managing opt-in resource sharing to decentralized physical infrastructure networks.
 * Handles registration, contribution adjustment, and cryptographic proof generation.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.6
 */
interface DePinAgent {
    /** Current lifecycle state of the DePIN agent. */
    val state: StateFlow<AgentState>

    /** Active contributions across all registered DePIN networks. */
    val contributions: StateFlow<List<DePinContribution>>

    /** Register the device with a DePIN network for a specific resource type. */
    suspend fun register(
        network: DePinNetwork,
        resourceType: ResourceType,
    )

    /** Unregister and disconnect from a DePIN network. */
    suspend fun unregister(network: DePinNetwork)

    /** Adjust contribution levels based on resource availability thresholds. */
    suspend fun adjustContribution(threshold: ResourceThreshold)

    /** Generate a cryptographic proof for a completed contribution session. */
    fun generateProof(session: ContributionSession): CryptographicProof
}
