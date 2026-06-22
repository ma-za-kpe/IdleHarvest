package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.MeshState
import com.maku.idleharvest.domain.models.Peer
import com.maku.idleharvest.domain.models.PeerConnection
import com.maku.idleharvest.domain.models.PeerId
import com.maku.idleharvest.domain.models.ResourceProfile
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE peer discovery and coordination for pooled resource sharing.
 * Uses GATT connections and advertising-based discovery (no mesh flooding).
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4
 */
interface MeshCoordinator {
    /** Currently connected active peers with their resource profiles. */
    val activePeers: StateFlow<List<Peer>>

    /** Current state of the mesh coordinator (idle, scanning, connected, error). */
    val meshState: StateFlow<MeshState>

    /** Start BLE scanning for nearby IdleHarvest peers. */
    fun startDiscovery()

    /** Stop BLE scanning. */
    fun stopDiscovery()

    /** Attempt to connect to a discovered peer via GATT. */
    suspend fun connectToPeer(peerId: PeerId): Result<PeerConnection>

    /** Disconnect from a specific peer and remove from active roster. */
    fun disconnectPeer(peerId: PeerId)

    /** Broadcast this device's resource profile to all connected peers. */
    fun broadcastResourceProfile(profile: ResourceProfile)
}

/** Maximum number of simultaneous GATT peer connections allowed. */
const val MAX_GATT_CONNECTIONS = 5
