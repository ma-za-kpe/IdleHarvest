package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.MAX_GATT_CONNECTIONS
import com.maku.idleharvest.domain.models.PeerId
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 10: GATT Connection Capacity Invariant
 *
 * *For any* sequence of peer connection requests, the number of active simultaneous
 * GATT connections SHALL never exceed 5. Additional peers beyond this limit SHALL be
 * discoverable via advertising rotation but not hold active connections.
 *
 * **Validates: Requirements 4.3**
 */
class GattCapacityPropertyTest {
    /**
     * Simulates the connection capacity logic as implemented in DefaultMeshCoordinator.
     * The coordinator maintains a `connections` map and rejects new connections when
     * `connections.size >= MAX_GATT_CONNECTIONS`.
     *
     * This models the same contract without requiring a platform-specific BleAdapter.
     */
    private class GattConnectionModel {
        private val connections = mutableMapOf<PeerId, String>()

        /**
         * Attempt to connect a peer. Returns success if under capacity,
         * or failure with MaxConnectionsReachedException if at capacity.
         * Mirrors DefaultMeshCoordinator.connectToPeer logic.
         */
        fun connectPeer(peerId: PeerId): Result<String> {
            if (connections.size >= MAX_GATT_CONNECTIONS) {
                return Result.failure(
                    MaxConnectionsReachedException(
                        "Cannot connect: maximum $MAX_GATT_CONNECTIONS simultaneous GATT connections reached.",
                    ),
                )
            }
            if (connections.containsKey(peerId)) {
                return Result.failure(
                    IllegalStateException("Already connected to peer ${peerId.value}"),
                )
            }
            val connectionId = "${peerId.value}_${connections.size}"
            connections[peerId] = connectionId
            return Result.success(connectionId)
        }

        fun disconnectPeer(peerId: PeerId) {
            connections.remove(peerId)
        }

        fun getActiveConnectionCount(): Int = connections.size
    }

    /**
     * Generator for a list of guaranteed-unique peer IDs.
     * Uses index-based ID generation to ensure uniqueness without relying on distinctBy.
     */
    private fun uniquePeerListArb(count: IntRange = 1..15): Arb<List<PeerId>> = Arb.int(count).map { size ->
        (0 until size).map { i -> PeerId("peer_${i}_${kotlin.random.Random.nextInt()}") }
    }

    @Test
    fun activeConnectionCountNeverExceedsFive() = runTest {
        forAll(uniquePeerListArb(6..20)) { peers ->
            val model = GattConnectionModel()

            // Attempt to connect all peers in sequence
            for (peer in peers) {
                model.connectPeer(peer)
            }

            // Invariant: active connection count MUST never exceed MAX_GATT_CONNECTIONS
            model.getActiveConnectionCount() <= MAX_GATT_CONNECTIONS
        }
    }

    @Test
    fun firstFiveConnectionsAlwaysSucceed() = runTest {
        forAll(uniquePeerListArb(5..20)) { peers ->
            val model = GattConnectionModel()
            val uniquePeers = peers.take(5)

            // First 5 unique peers should always connect successfully
            val results = uniquePeers.map { model.connectPeer(it) }

            results.all { it.isSuccess }
        }
    }

    @Test
    fun sixthAndBeyondConnectionsAlwaysFail() = runTest {
        forAll(uniquePeerListArb(6..20)) { peers ->
            val model = GattConnectionModel()

            val results =
                peers.mapIndexed { index, peer ->
                    index to model.connectPeer(peer)
                }

            // First 5 should succeed, 6th+ should fail
            val successCount = results.count { it.second.isSuccess }
            val failuresAfterFifth = results.filter { it.first >= 5 }

            successCount == MAX_GATT_CONNECTIONS &&
                failuresAfterFifth.all { it.second.isFailure }
        }
    }

    @Test
    fun rejectedPeersReceiveMaxConnectionsReachedException() = runTest {
        forAll(uniquePeerListArb(6..15)) { peers ->
            val model = GattConnectionModel()

            // Fill to capacity
            val firstFive = peers.take(5)
            firstFive.forEach { model.connectPeer(it) }

            // Attempt connections beyond capacity
            val overflow = peers.drop(5)
            val rejections = overflow.map { model.connectPeer(it) }

            rejections.all { result ->
                result.isFailure &&
                    result.exceptionOrNull() is MaxConnectionsReachedException
            }
        }
    }

    @Test
    fun disconnectingAPeerAllowsNewConnection() = runTest {
        forAll(uniquePeerListArb(7..15)) { peers ->
            val model = GattConnectionModel()

            // Fill to capacity
            val firstFive = peers.take(5)
            firstFive.forEach { model.connectPeer(it) }

            // Verify 6th fails
            val sixthPeer = peers[5]
            val rejectedResult = model.connectPeer(sixthPeer)
            val wasRejected = rejectedResult.isFailure

            // Disconnect one peer
            model.disconnectPeer(firstFive.first())

            // Now the 6th should succeed
            val retryResult = model.connectPeer(sixthPeer)
            val nowSucceeds = retryResult.isSuccess

            // Invariant still holds
            wasRejected &&
                nowSucceeds &&
                model.getActiveConnectionCount() <= MAX_GATT_CONNECTIONS
        }
    }

    @Test
    fun connectionCountBoundedForAnyConnectDisconnectSequence() = runTest {
        forAll(uniquePeerListArb(5..20), Arb.int(1..10)) { peers, disconnectCount ->
            val model = GattConnectionModel()
            var maxObserved = 0

            // Connect all peers, tracking max observed connections
            for (peer in peers) {
                model.connectPeer(peer)
                val current = model.getActiveConnectionCount()
                if (current > maxObserved) maxObserved = current
            }

            // Disconnect some peers
            val toDisconnect = peers.take(minOf(disconnectCount, peers.size))
            for (peer in toDisconnect) {
                model.disconnectPeer(peer)
            }

            // Reconnect new peers
            for (i in 0 until disconnectCount) {
                val newPeer = PeerId("reconnect_$i")
                model.connectPeer(newPeer)
                val current = model.getActiveConnectionCount()
                if (current > maxObserved) maxObserved = current
            }

            // The maximum EVER observed must be <= MAX_GATT_CONNECTIONS
            maxObserved <= MAX_GATT_CONNECTIONS
        }
    }

    @Test
    fun maxGattConnectionsConstantIsFive() {
        // Verify the constant itself as a baseline
        assertEquals(5, MAX_GATT_CONNECTIONS)
    }

    @Test
    fun duplicateConnectionAttemptFailsWithoutAffectingCapacity() = runTest {
        forAll(uniquePeerListArb(3..5)) { peers ->
            val model = GattConnectionModel()

            // Connect peers
            peers.forEach { model.connectPeer(it) }
            val countAfterInitial = model.getActiveConnectionCount()

            // Try connecting same peer again
            val duplicateResult = model.connectPeer(peers.first())

            // Duplicate should fail but not change count
            duplicateResult.isFailure &&
                model.getActiveConnectionCount() == countAfterInitial
        }
    }
}
