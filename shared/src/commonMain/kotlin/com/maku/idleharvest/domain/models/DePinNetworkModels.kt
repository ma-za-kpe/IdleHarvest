package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * A supported DePIN (Decentralized Physical Infrastructure) network.
 */
@Serializable
data class DePinNetwork(
    val id: String,
    val name: String,
    val supportedResources: List<ResourceType>,
    val tokenSymbol: String,
    val endpointUrl: String,
)

/**
 * Represents an active resource contribution session with a DePIN network.
 */
@Serializable
data class ContributionSession(
    val id: String,
    val networkId: String,
    val resourceType: ResourceType,
    val startedAt: Long,
    val bytesServed: Long?,
    val computeUnitsCompleted: Long?,
    val storageProvidedMb: Long?,
)
