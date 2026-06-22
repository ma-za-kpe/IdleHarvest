package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * Connection state of a BLE mesh peer.
 */
@Serializable
enum class PeerConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }

/**
 * A discovered or connected BLE mesh peer.
 */
@Serializable
data class Peer(
    val id: PeerId,
    val displayName: String,
    val resourceProfile: ResourceProfile,
    val signalStrength: Int,
    val connectionState: PeerConnectionState,
    val lastSeen: Long,
)

/**
 * Represents an active GATT connection to a peer device.
 */
@Serializable
data class PeerConnection(
    val peerId: PeerId,
    val connectionId: String,
    val establishedAt: Long,
    val mtu: Int,
)
