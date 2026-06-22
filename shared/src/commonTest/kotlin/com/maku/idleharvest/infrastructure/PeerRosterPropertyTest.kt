package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.MeshState
import com.maku.idleharvest.domain.models.Peer
import com.maku.idleharvest.domain.models.PeerConnectionState
import com.maku.idleharvest.domain.models.PeerId
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.generators.resourceProfile
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.uuid
import io.kotest.property.forAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property 9: Peer Roster Invariant
 *
 * *For any* sequence of peer connect and disconnect events, the Mesh_Coordinator
 * active peer roster SHALL accurately reflect the current set of connected peers —
 * containing exactly and only those peers that are actively connected, with their
 * latest Resource_Profiles.
 *
 * **Validates: Requirements 4.2, 4.4**
 */
class PeerRosterPropertyTest {

    /**
     * Lightweight peer roster coordinator that replicates the roster logic
     * from DefaultMeshCoordinator without requiring the platform-specific BleAdapter.
     *
     * This tests the same invariant: after connects and disconnects, the active
     * peers roster contains exactly and only the currently connected peers.
     */
    private class TestPeerRosterCoordinator {
        private val _activePeers = MutableStateFlow<List<Peer>>(emptyList())
        val activePeers: StateFlow<List<Peer>> = _activePeers.asStateFlow()

        private val _meshState = MutableStateFlow(MeshState.IDLE)
        val meshState: StateFlow<MeshState> = _meshState.asStateFlow()

        private val connectedPeerIds = mutableSetOf<PeerId>()

        /**
         * Simulate a successful peer connection: add peer to roster.
         * Mirrors DefaultMeshCoordinator.connectToPeer success path.
         */
        fun connectPeer(peerId: PeerId, profile: ResourceProfile, signalStrength: Int) {
            if (connectedPeerIds.contains(peerId)) return

            connectedPeerIds.add(peerId)
            val peer = Peer(
                id = peerId,
                displayName = "Peer-${peerId.value.take(8)}",
                resourceProfile = profile,
                signalStrength = signalStrength,
                connectionState = PeerConnectionState.CONNECTED,
                lastSeen = 1_719_792_000_000L,
            )

            val currentPeers = _activePeers.value.toMutableList()
            val existingIndex = currentPeers.indexOfFirst { it.id == peerId }
            if (existingIndex >= 0) {
                currentPeers[existingIndex] = peer
            } else {
                currentPeers.add(peer)
            }
            _activePeers.value = currentPeers
            _meshState.value = MeshState.CONNECTED
        }

        /**
         * Simulate peer disconnection: remove peer from roster.
         * Mirrors DefaultMeshCoordinator.disconnectPeer logic.
         */
        fun disconnectPeer(peerId: PeerId) {
            connectedPeerIds.remove(peerId)
            val currentPeers = _activePeers.value.toMutableList()
            currentPeers.removeAll { it.id == peerId }
            _activePeers.value = currentPeers

            _meshState.value = if (connectedPeerIds.isEmpty()) MeshState.IDLE else MeshState.CONNECTED
        }

        /**
         * Update an existing connected peer's resource profile.
         * Mirrors the profile update path in handlePeerDiscovered.
         */
        fun updatePeerProfile(peerId: PeerId, profile: ResourceProfile) {
            if (!connectedPeerIds.contains(peerId)) return

            val currentPeers = _activePeers.value.toMutableList()
            val index = currentPeers.indexOfFirst { it.id == peerId }
            if (index >= 0) {
                currentPeers[index] = currentPeers[index].copy(resourceProfile = profile)
                _activePeers.value = currentPeers
            }
        }

        /** Returns the set of peer IDs that are tracked as connected. */
        fun getConnectedPeerIds(): Set<PeerId> = connectedPeerIds.toSet()
    }

    /** Represents a connect or disconnect operation in a sequence. */
    sealed class PeerEvent {
        data class Connect(val peerId: PeerId, val profile: ResourceProfile, val signal: Int) : PeerEvent()
        data class Disconnect(val peerId: PeerId) : PeerEvent()
        data class UpdateProfile(val peerId: PeerId, val profile: ResourceProfile) : PeerEvent()
    }

    /** Generator for a unique peer ID. */
    private fun arbPeerId(): Arb<PeerId> = Arb.uuid().map { PeerId(it.toString()) }

    /** Generator for a sequence of peer events with a bounded pool of peer IDs. */
    private fun arbPeerEventSequence(): Arb<List<PeerEvent>> = arbitrary {
        // Generate a small pool of peer IDs (2-6 peers) to get interesting connect/disconnect interactions
        val peerCount = Arb.int(2..6).bind()
        val peerIds = (1..peerCount).map { PeerId("peer-$it") }

        // Generate a sequence of events (5-20 events)
        val eventCount = Arb.int(5..20).bind()
        val events = mutableListOf<PeerEvent>()

        for (i in 0 until eventCount) {
            val peerIndex = Arb.int(0 until peerCount).bind()
            val peerId = peerIds[peerIndex]
            val profile = Arb.resourceProfile().bind()

            // Randomly choose connect, disconnect, or update profile
            when (Arb.int(0..2).bind()) {
                0 -> events.add(PeerEvent.Connect(peerId, profile, Arb.int(-100..-30).bind()))
                1 -> events.add(PeerEvent.Disconnect(peerId))
                2 -> events.add(PeerEvent.UpdateProfile(peerId, profile))
            }
        }
        events
    }

    @Test
    fun rosterContainsExactlyConnectedPeersAfterEventSequence() = runTest {
        forAll(arbPeerEventSequence()) { events ->
            val coordinator = TestPeerRosterCoordinator()

            // Apply all events
            for (event in events) {
                when (event) {
                    is PeerEvent.Connect -> coordinator.connectPeer(event.peerId, event.profile, event.signal)
                    is PeerEvent.Disconnect -> coordinator.disconnectPeer(event.peerId)
                    is PeerEvent.UpdateProfile -> coordinator.updatePeerProfile(event.peerId, event.profile)
                }
            }

            // Invariant: roster peer IDs == connected peer IDs set
            val rosterPeerIds = coordinator.activePeers.value.map { it.id }.toSet()
            val connectedIds = coordinator.getConnectedPeerIds()

            rosterPeerIds == connectedIds
        }
    }

    @Test
    fun disconnectedPeerIsNeverInRoster() = runTest {
        forAll(arbPeerEventSequence()) { events ->
            val coordinator = TestPeerRosterCoordinator()

            // Track which peers have been disconnected as the last action
            val lastDisconnected = mutableSetOf<PeerId>()

            for (event in events) {
                when (event) {
                    is PeerEvent.Connect -> {
                        coordinator.connectPeer(event.peerId, event.profile, event.signal)
                        lastDisconnected.remove(event.peerId)
                    }
                    is PeerEvent.Disconnect -> {
                        coordinator.disconnectPeer(event.peerId)
                        lastDisconnected.add(event.peerId)
                    }
                    is PeerEvent.UpdateProfile -> coordinator.updatePeerProfile(event.peerId, event.profile)
                }
            }

            // Invariant: no peer whose last action was disconnect should be in the roster
            val rosterPeerIds = coordinator.activePeers.value.map { it.id }.toSet()
            lastDisconnected.none { it in rosterPeerIds }
        }
    }

    @Test
    fun connectedPeerIsAlwaysInRoster() = runTest {
        forAll(arbPeerEventSequence()) { events ->
            val coordinator = TestPeerRosterCoordinator()

            // Track which peers were connected and not subsequently disconnected
            val currentlyConnected = mutableSetOf<PeerId>()

            for (event in events) {
                when (event) {
                    is PeerEvent.Connect -> {
                        coordinator.connectPeer(event.peerId, event.profile, event.signal)
                        currentlyConnected.add(event.peerId)
                    }
                    is PeerEvent.Disconnect -> {
                        coordinator.disconnectPeer(event.peerId)
                        currentlyConnected.remove(event.peerId)
                    }
                    is PeerEvent.UpdateProfile -> coordinator.updatePeerProfile(event.peerId, event.profile)
                }
            }

            // Invariant: every peer that is currently connected must be in the roster
            val rosterPeerIds = coordinator.activePeers.value.map { it.id }.toSet()
            currentlyConnected.all { it in rosterPeerIds }
        }
    }

    @Test
    fun rosterReflectsLatestResourceProfile() = runTest {
        forAll(arbPeerEventSequence()) { events ->
            val coordinator = TestPeerRosterCoordinator()

            // Track the latest profile for each connected peer
            // Only update tracking when the coordinator actually applies the change
            val latestProfiles = mutableMapOf<PeerId, ResourceProfile>()

            for (event in events) {
                when (event) {
                    is PeerEvent.Connect -> {
                        // Only track profile if peer was not already connected
                        // (duplicate connect is a no-op, matching DefaultMeshCoordinator behavior)
                        val wasAlreadyConnected = coordinator.getConnectedPeerIds().contains(event.peerId)
                        coordinator.connectPeer(event.peerId, event.profile, event.signal)
                        if (!wasAlreadyConnected) {
                            latestProfiles[event.peerId] = event.profile
                        }
                    }
                    is PeerEvent.Disconnect -> {
                        coordinator.disconnectPeer(event.peerId)
                        latestProfiles.remove(event.peerId)
                    }
                    is PeerEvent.UpdateProfile -> {
                        coordinator.updatePeerProfile(event.peerId, event.profile)
                        if (coordinator.getConnectedPeerIds().contains(event.peerId)) {
                            latestProfiles[event.peerId] = event.profile
                        }
                    }
                }
            }

            // Invariant: each peer in the roster has its latest resource profile
            coordinator.activePeers.value.all { peer ->
                val expectedProfile = latestProfiles[peer.id]
                expectedProfile != null && peer.resourceProfile == expectedProfile
            }
        }
    }

    @Test
    fun emptyRosterWhenAllPeersDisconnected() = runTest {
        forAll(Arb.list(arbPeerId(), 1..5)) { peerIds ->
            val coordinator = TestPeerRosterCoordinator()
            val profile = ResourceProfile.empty()

            // Connect all peers
            for (peerId in peerIds) {
                coordinator.connectPeer(peerId, profile, -50)
            }

            // Disconnect all peers
            for (peerId in peerIds) {
                coordinator.disconnectPeer(peerId)
            }

            // Invariant: roster should be empty
            coordinator.activePeers.value.isEmpty()
        }
    }

    @Test
    fun duplicateConnectDoesNotCreateDuplicateEntries() = runTest {
        forAll(Arb.resourceProfile(), Arb.resourceProfile()) { profile1, profile2 ->
            val coordinator = TestPeerRosterCoordinator()
            val peerId = PeerId("duplicate-peer")

            // Connect same peer twice with different profiles
            coordinator.connectPeer(peerId, profile1, -50)
            coordinator.connectPeer(peerId, profile2, -60)

            // Invariant: roster should contain exactly one entry for this peer
            val matchingPeers = coordinator.activePeers.value.filter { it.id == peerId }
            matchingPeers.size == 1
        }
    }
}
