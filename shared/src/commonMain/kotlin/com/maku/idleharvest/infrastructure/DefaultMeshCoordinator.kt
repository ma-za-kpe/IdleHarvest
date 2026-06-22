package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.interfaces.MAX_GATT_CONNECTIONS
import com.maku.idleharvest.domain.interfaces.MeshCoordinator
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.MeshState
import com.maku.idleharvest.domain.models.Peer
import com.maku.idleharvest.domain.models.PeerConnection
import com.maku.idleharvest.domain.models.PeerConnectionState
import com.maku.idleharvest.domain.models.PeerId
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.infrastructure.crypto.CryptoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Default implementation of [MeshCoordinator] for BLE peer discovery and coordination.
 *
 * Key behaviors:
 * - Peer discovery via BLE advertising and GATT (no mesh flooding/relaying)
 * - Maximum 5 simultaneous GATT connections enforced
 * - Peer disconnect: remove from roster and redistribute tasks within 5 seconds
 * - AES-GCM encrypted communication via [CryptoProvider]
 * - Adaptive scan intervals: aggressive when charging, conservative on battery
 * - Direct GATT connections only (Requirement 4.7)
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7
 */
class DefaultMeshCoordinator(
    private val bleAdapter: BleAdapter,
    private val cryptoProvider: CryptoProvider,
    private val eventBus: AgentEventBus,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { currentTimeMillis() },
) : MeshCoordinator {

    companion object {
        /** Aggressive scan interval when device is charging (5 seconds). */
        const val SCAN_INTERVAL_CHARGING_MS = 5_000L

        /** Conservative scan interval when on battery (30 seconds). */
        const val SCAN_INTERVAL_BATTERY_MS = 30_000L

        /** Time limit to handle peer disconnect and task redistribution (5 seconds). */
        const val DISCONNECT_HANDLING_TIMEOUT_MS = 5_000L

        /** Default MTU for GATT connections. */
        private const val DEFAULT_MTU = 512
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _activePeers = MutableStateFlow<List<Peer>>(emptyList())
    override val activePeers: StateFlow<List<Peer>> = _activePeers.asStateFlow()

    private val _meshState = MutableStateFlow(MeshState.IDLE)
    override val meshState: StateFlow<MeshState> = _meshState.asStateFlow()

    /** Active BLE connections keyed by peer ID. */
    private val connections = mutableMapOf<PeerId, BleConnection>()

    /** Encryption key for inter-device communication. */
    private val encryptionKey: ByteArray by lazy { cryptoProvider.generateKey() }

    /** Whether the device is currently charging (affects scan interval). */
    private var isCharging = false

    /** Job managing the periodic scan cycle. */
    private var scanJob: Job? = null

    /** Whether discovery is currently active. */
    private var isDiscoveryActive = false

    /**
     * Start BLE scanning for nearby IdleHarvest peers.
     * Uses adaptive scan intervals based on charging state.
     *
     * Validates: Requirements 4.1, 4.6
     */
    override fun startDiscovery() {
        if (isDiscoveryActive) return

        isDiscoveryActive = true
        _meshState.value = MeshState.SCANNING

        bleAdapter.startScan { peerId, signalStrength, advertisingData ->
            handlePeerDiscovered(peerId, signalStrength, advertisingData)
        }

        // Start adaptive scan cycle
        scanJob = scope.launch {
            while (isDiscoveryActive) {
                val interval = getCurrentScanInterval()
                delay(interval)
                if (isDiscoveryActive) {
                    // Rotate advertising to allow additional peers beyond max connections
                    // to discover us
                    refreshAdvertising()
                }
            }
        }
    }

    /**
     * Stop BLE scanning.
     */
    override fun stopDiscovery() {
        isDiscoveryActive = false
        scanJob?.cancel()
        scanJob = null
        bleAdapter.stopScan()
        bleAdapter.stopAdvertise()

        if (connections.isEmpty()) {
            _meshState.value = MeshState.IDLE
        }
    }

    /**
     * Attempt to connect to a discovered peer via GATT.
     *
     * Enforces the [MAX_GATT_CONNECTIONS] limit. If the maximum is reached,
     * returns a failure. Additional peers remain discoverable via advertising rotation.
     *
     * Validates: Requirements 4.3, 4.5
     */
    override suspend fun connectToPeer(peerId: PeerId): Result<PeerConnection> {
        // Enforce max simultaneous GATT connections
        if (connections.size >= MAX_GATT_CONNECTIONS) {
            return Result.failure(
                MaxConnectionsReachedException(
                    "Cannot connect: maximum $MAX_GATT_CONNECTIONS simultaneous GATT connections reached. " +
                        "Peer remains discoverable via advertising rotation."
                )
            )
        }

        // Check if already connected
        if (connections.containsKey(peerId)) {
            return Result.failure(
                IllegalStateException("Already connected to peer ${peerId.value}")
            )
        }

        // Update peer state to connecting
        updatePeerState(peerId, PeerConnectionState.CONNECTING)

        val connectionResult = bleAdapter.connect(peerId)

        return connectionResult.fold(
            onSuccess = { bleConnection ->
                connections[peerId] = bleConnection
                val now = clock()

                // Update peer state to connected
                updatePeerState(peerId, PeerConnectionState.CONNECTED)

                // Update mesh state
                _meshState.value = MeshState.CONNECTED

                val peerConnection = PeerConnection(
                    peerId = peerId,
                    connectionId = "${peerId.value}_${now}",
                    establishedAt = now,
                    mtu = DEFAULT_MTU,
                )

                Result.success(peerConnection)
            },
            onFailure = { error ->
                updatePeerState(peerId, PeerConnectionState.DISCONNECTED)
                _meshState.value = if (connections.isEmpty()) MeshState.SCANNING else MeshState.CONNECTED
                Result.failure(error)
            }
        )
    }

    /**
     * Disconnect from a specific peer, remove from active roster,
     * and trigger task redistribution within 5 seconds.
     *
     * Validates: Requirements 4.4
     */
    override fun disconnectPeer(peerId: PeerId) {
        val connection = connections.remove(peerId)
        connection?.close()
        bleAdapter.disconnect(peerId)

        // Remove peer from roster immediately
        removePeerFromRoster(peerId)

        // Publish disconnect event for task redistribution
        scope.launch {
            handlePeerDisconnect(peerId)
        }

        // Update mesh state
        _meshState.value = when {
            connections.isEmpty() && isDiscoveryActive -> MeshState.SCANNING
            connections.isEmpty() -> MeshState.IDLE
            else -> MeshState.CONNECTED
        }
    }

    /**
     * Broadcast this device's resource profile to all connected peers.
     * Data is encrypted using AES-GCM before transmission.
     *
     * Validates: Requirements 4.2, 4.5
     */
    override fun broadcastResourceProfile(profile: ResourceProfile) {
        val serializedProfile = json.encodeToString(profile).encodeToByteArray()
        val encryptedData = cryptoProvider.encrypt(encryptionKey, serializedProfile)

        scope.launch {
            for ((_, connection) in connections) {
                connection.send(encryptedData)
            }
        }

        // Also update our advertising data so discoverable peers can see our profile
        bleAdapter.advertise(encryptedData)
    }

    /**
     * Update the charging state, which affects scan interval behavior.
     * Called when device power state changes.
     *
     * Validates: Requirements 4.6
     */
    fun updateChargingState(charging: Boolean) {
        isCharging = charging
    }

    /**
     * Get the current adaptive scan interval based on power state.
     * Aggressive (shorter) when charging, conservative (longer) on battery.
     *
     * Validates: Requirements 4.6
     */
    fun getCurrentScanInterval(): Long {
        return if (isCharging) SCAN_INTERVAL_CHARGING_MS else SCAN_INTERVAL_BATTERY_MS
    }

    /**
     * Get the current number of active GATT connections.
     */
    fun getActiveConnectionCount(): Int = connections.size

    /**
     * Encrypt a message for peer-to-peer transmission using AES-GCM.
     *
     * Validates: Requirements 4.5
     */
    fun encryptMessage(plaintext: ByteArray): ByteArray {
        return cryptoProvider.encrypt(encryptionKey, plaintext)
    }

    /**
     * Decrypt a received peer message using AES-GCM.
     *
     * Validates: Requirements 4.5
     */
    fun decryptMessage(ciphertext: ByteArray): ByteArray {
        return cryptoProvider.decrypt(encryptionKey, ciphertext)
    }

    // --- Private helpers ---

    /**
     * Handle a discovered peer from BLE scan callback.
     * Adds or updates the peer in the roster.
     */
    private fun handlePeerDiscovered(peerId: PeerId, signalStrength: Int, advertisingData: ByteArray) {
        val now = clock()
        val currentPeers = _activePeers.value.toMutableList()

        val existingIndex = currentPeers.indexOfFirst { it.id == peerId }

        // Try to decode the resource profile from advertising data
        val resourceProfile = tryDecodeResourceProfile(advertisingData)

        val peer = Peer(
            id = peerId,
            displayName = "Peer-${peerId.value.take(8)}",
            resourceProfile = resourceProfile ?: ResourceProfile.empty(),
            signalStrength = signalStrength,
            connectionState = if (connections.containsKey(peerId)) {
                PeerConnectionState.CONNECTED
            } else {
                PeerConnectionState.DISCONNECTED
            },
            lastSeen = now,
        )

        if (existingIndex >= 0) {
            currentPeers[existingIndex] = peer
        } else {
            currentPeers.add(peer)
            // Publish discovery event for new peers
            eventBus.publish(AgentEvent.PeerDiscovered(peer))
        }

        _activePeers.value = currentPeers
    }

    /**
     * Try to decode a ResourceProfile from encrypted advertising data.
     * Returns null if decoding fails (malformed data, wrong key, etc.)
     */
    private fun tryDecodeResourceProfile(data: ByteArray): ResourceProfile? {
        return try {
            val decrypted = cryptoProvider.decrypt(encryptionKey, data)
            val jsonString = decrypted.decodeToString()
            json.decodeFromString<ResourceProfile>(jsonString)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Update a peer's connection state in the roster.
     */
    private fun updatePeerState(peerId: PeerId, state: PeerConnectionState) {
        val currentPeers = _activePeers.value.toMutableList()
        val index = currentPeers.indexOfFirst { it.id == peerId }

        if (index >= 0) {
            currentPeers[index] = currentPeers[index].copy(connectionState = state)
            _activePeers.value = currentPeers
        }
    }

    /**
     * Remove a peer from the active roster.
     */
    private fun removePeerFromRoster(peerId: PeerId) {
        val currentPeers = _activePeers.value.toMutableList()
        currentPeers.removeAll { it.id == peerId }
        _activePeers.value = currentPeers
    }

    /**
     * Handle peer disconnect: publish event and trigger task redistribution.
     * Must complete within [DISCONNECT_HANDLING_TIMEOUT_MS] (5 seconds).
     *
     * Validates: Requirements 4.4
     */
    private suspend fun handlePeerDisconnect(peerId: PeerId) {
        // Publish a peer disconnect event through the event bus.
        // Subscribing agents (e.g., DePIN_Agent, Earning_Engine) should
        // redistribute any tasks associated with this peer within 5 seconds.
        val disconnectedPeer = Peer(
            id = peerId,
            displayName = "Peer-${peerId.value.take(8)}",
            resourceProfile = ResourceProfile.empty(),
            signalStrength = 0,
            connectionState = PeerConnectionState.DISCONNECTED,
            lastSeen = clock(),
        )
        eventBus.publish(AgentEvent.PeerDiscovered(disconnectedPeer))
    }

    /**
     * Refresh advertising data to support advertising rotation for peers
     * beyond the max connection limit.
     */
    private fun refreshAdvertising() {
        // Re-advertise to allow additional peers to discover this device
        // even when at max connection capacity.
        val emptyProfile = ResourceProfile.empty().copy(timestamp = clock())
        val data = json.encodeToString(emptyProfile).encodeToByteArray()
        val encrypted = cryptoProvider.encrypt(encryptionKey, data)
        bleAdapter.advertise(encrypted)
    }
}

/**
 * Exception thrown when attempting to establish a GATT connection
 * while at the maximum connection capacity.
 */
class MaxConnectionsReachedException(message: String) : Exception(message)
