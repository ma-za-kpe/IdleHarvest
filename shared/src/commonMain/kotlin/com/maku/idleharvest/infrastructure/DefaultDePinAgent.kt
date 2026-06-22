package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.interfaces.DePinAgent
import com.maku.idleharvest.domain.interfaces.PolicyManager
import com.maku.idleharvest.domain.interfaces.PrivacyVault
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AgentState
import com.maku.idleharvest.domain.models.ContributionSession
import com.maku.idleharvest.domain.models.CryptographicProof
import com.maku.idleharvest.domain.models.DePinContribution
import com.maku.idleharvest.domain.models.DePinNetwork
import com.maku.idleharvest.domain.models.ResourceThreshold
import com.maku.idleharvest.domain.models.ResourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Default implementation of [DePinAgent] managing opt-in resource sharing
 * to decentralized physical infrastructure networks.
 *
 * Handles:
 * - Network registration and unregistration
 * - Contribution management per network per session
 * - Dynamic threshold enforcement (reduce/pause within 10 seconds)
 * - Token earning tracking in Privacy_Vault
 * - Graceful disconnect on connectivity loss with pending proof queuing
 * - Cryptographic proof generation for contribution verification
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
class DefaultDePinAgent(
    private val vault: PrivacyVault,
    private val eventBus: AgentEventBus,
    private val policyManager: PolicyManager,
    private val clock: () -> Long = { currentTimeMillis() },
) : DePinAgent {
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(AgentState.IDLE)
    override val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _contributions = MutableStateFlow<List<DePinContribution>>(emptyList())
    override val contributions: StateFlow<List<DePinContribution>> = _contributions.asStateFlow()

    /** Registered networks with their resource type bindings. */
    private val registeredNetworks = mutableMapOf<String, NetworkRegistration>()

    /** Pending proofs queued during connectivity loss for later submission. */
    private val pendingProofs = mutableListOf<CryptographicProof>()

    /** Current user-defined resource threshold controlling contribution levels. */
    private var currentThreshold = ResourceThreshold()

    /** Whether the agent is currently paused due to threshold violation. */
    private var isPaused = false

    override suspend fun register(
        network: DePinNetwork,
        resourceType: ResourceType,
    ) {
        require(resourceType in network.supportedResources) {
            "Network ${network.name} does not support resource type $resourceType"
        }

        registeredNetworks[network.id] =
            NetworkRegistration(
                network = network,
                resourceType = resourceType,
                registeredAt = clock(),
            )

        _state.value = AgentState.EVALUATING

        // Create an initial contribution record for this session
        val sessionId = generateSessionId(network.id)
        val contribution =
            DePinContribution(
                networkId = network.id,
                networkName = network.name,
                resourceType = resourceType,
                sessionId = sessionId,
                startedAt = clock(),
                endedAt = null,
                earnedTokens = 0.0,
                tokenSymbol = network.tokenSymbol,
                proof = null,
            )

        val current = _contributions.value.toMutableList()
        current.add(contribution)
        _contributions.value = current

        // Persist contribution to vault
        persistContribution(contribution)

        _state.value = AgentState.EXECUTING
    }

    override suspend fun unregister(network: DePinNetwork) {
        registeredNetworks.remove(network.id)

        // End all active contributions for this network
        val now = clock()
        val updated =
            _contributions.value.map { contribution ->
                if (contribution.networkId == network.id && contribution.endedAt == null) {
                    contribution.copy(endedAt = now)
                } else {
                    contribution
                }
            }
        _contributions.value = updated

        // Persist updated contributions
        updated.filter { it.networkId == network.id }.forEach { persistContribution(it) }

        // Transition state based on remaining registrations
        if (registeredNetworks.isEmpty()) {
            _state.value = AgentState.IDLE
        }
    }

    override suspend fun adjustContribution(threshold: ResourceThreshold) {
        currentThreshold = threshold

        // Immediately adjust contributions based on new threshold.
        // If resources are below threshold, pause contributions.
        // This operation completes immediately to meet the "within 10 seconds" requirement.
        val shouldPause = shouldPauseContributions(threshold)

        if (shouldPause && !isPaused) {
            isPaused = true
            _state.value = AgentState.PAUSED

            // End all active contributions
            val now = clock()
            val paused =
                _contributions.value.map { contribution ->
                    if (contribution.endedAt == null) {
                        contribution.copy(endedAt = now)
                    } else {
                        contribution
                    }
                }
            _contributions.value = paused
            paused.filter { it.endedAt == now }.forEach { persistContribution(it) }
        } else if (!shouldPause && isPaused) {
            // Resume contributions
            isPaused = false
            _state.value = if (registeredNetworks.isNotEmpty()) AgentState.EXECUTING else AgentState.IDLE

            // Restart contributions for registered networks
            val now = clock()
            val resumed = _contributions.value.toMutableList()
            for ((_, registration) in registeredNetworks) {
                val sessionId = generateSessionId(registration.network.id)
                val contribution =
                    DePinContribution(
                        networkId = registration.network.id,
                        networkName = registration.network.name,
                        resourceType = registration.resourceType,
                        sessionId = sessionId,
                        startedAt = now,
                        endedAt = null,
                        earnedTokens = 0.0,
                        tokenSymbol = registration.network.tokenSymbol,
                        proof = null,
                    )
                resumed.add(contribution)
                persistContribution(contribution)
            }
            _contributions.value = resumed
        }
    }

    override fun generateProof(session: ContributionSession): CryptographicProof {
        val proofData = buildProofData(session)
        val signature = signContribution(proofData)

        return CryptographicProof(
            sessionId = session.id,
            proofData = proofData,
            signature = signature,
            timestamp = clock(),
        )
    }

    /**
     * Handle connectivity loss: gracefully disconnect from all networks
     * and queue pending proofs for later submission.
     *
     * Call this when a [AgentEvent.ConnectivityChanged] event with isOnline=false is received.
     */
    suspend fun handleConnectivityLoss() {
        // Generate proofs for all active contributions before disconnecting
        val now = clock()
        val active = _contributions.value.filter { it.endedAt == null }

        for (contribution in active) {
            val session =
                ContributionSession(
                    id = contribution.sessionId,
                    networkId = contribution.networkId,
                    resourceType = contribution.resourceType,
                    startedAt = contribution.startedAt,
                    bytesServed = null,
                    computeUnitsCompleted = null,
                    storageProvidedMb = null,
                )
            val proof = generateProof(session)
            pendingProofs.add(proof)
        }

        // End all active contributions
        val disconnected =
            _contributions.value.map { contribution ->
                if (contribution.endedAt == null) {
                    contribution.copy(endedAt = now)
                } else {
                    contribution
                }
            }
        _contributions.value = disconnected
        disconnected.filter { it.endedAt == now }.forEach { persistContribution(it) }

        _state.value = AgentState.PAUSED
    }

    /**
     * Handle connectivity restoration: submit pending proofs and resume contributions.
     */
    suspend fun handleConnectivityRestored() {
        // Submit pending proofs (in a real implementation, these would be sent to the networks)
        pendingProofs.clear()

        // Resume contributions for registered networks if not paused by threshold
        if (!isPaused && registeredNetworks.isNotEmpty()) {
            _state.value = AgentState.EXECUTING

            val now = clock()
            val resumed = _contributions.value.toMutableList()
            for ((_, registration) in registeredNetworks) {
                val sessionId = generateSessionId(registration.network.id)
                val contribution =
                    DePinContribution(
                        networkId = registration.network.id,
                        networkName = registration.network.name,
                        resourceType = registration.resourceType,
                        sessionId = sessionId,
                        startedAt = now,
                        endedAt = null,
                        earnedTokens = 0.0,
                        tokenSymbol = registration.network.tokenSymbol,
                        proof = null,
                    )
                resumed.add(contribution)
                persistContribution(contribution)
            }
            _contributions.value = resumed
        }
    }

    /**
     * Returns the list of pending proofs queued during connectivity loss.
     */
    fun getPendingProofs(): List<CryptographicProof> = pendingProofs.toList()

    /**
     * Returns whether the agent is currently paused due to threshold enforcement.
     */
    fun isContributionPaused(): Boolean = isPaused

    // --- Private helpers ---

    /**
     * Determine whether contributions should be paused based on the threshold.
     * In a full implementation, this would check actual resource levels against the threshold.
     * For commonMain, it checks whether the threshold values indicate "zero capacity"
     * (no resources available to share).
     */
    private fun shouldPauseContributions(threshold: ResourceThreshold): Boolean {
        // Pause if thresholds indicate no available capacity to contribute:
        // - Bandwidth minimum is unreachable (set to max float means "don't share bandwidth")
        // - Compute max is 0 (no compute sharing allowed)
        // In practice, the caller sets threshold to reflect current resource availability
        // relative to user minimums. A threshold with computeMaxCpuPercent=0 means
        // "current CPU is fully consumed."
        return threshold.computeMaxCpuPercent <= 0
    }

    /**
     * Persist a contribution record to the Privacy_Vault with the standard key pattern.
     */
    private suspend fun persistContribution(contribution: DePinContribution) {
        val key = "tx_depin_${contribution.sessionId}"
        val data = json.encodeToString(contribution).encodeToByteArray()
        vault.store(key, data)
    }

    /**
     * Build the proof data string from a contribution session.
     * Contains a deterministic representation of the session for verification.
     */
    private fun buildProofData(session: ContributionSession): String {
        val parts =
            buildList {
                add("session:${session.id}")
                add("network:${session.networkId}")
                add("resource:${session.resourceType.name}")
                add("started:${session.startedAt}")
                session.bytesServed?.let { add("bytes:$it") }
                session.computeUnitsCompleted?.let { add("compute:$it") }
                session.storageProvidedMb?.let { add("storage:$it") }
            }
        return parts.joinToString("|")
    }

    /**
     * Sign the proof data using a deterministic signature scheme.
     * In commonMain, this uses a simple HMAC-like approach.
     * Platform-specific implementations should use the device's Secure_Keystore.
     */
    private fun signContribution(proofData: String): String {
        // For commonMain, produce a deterministic "signature" from the proof data.
        // Real implementations would use SecureKeystore.sign() with hardware-backed keys.
        val dataBytes = proofData.encodeToByteArray()
        val hash = simpleHash(dataBytes)
        return hash.joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
    }

    /**
     * Simple hash function for commonMain (not cryptographically secure).
     * Platform implementations replace this with proper HMAC-SHA256.
     */
    private fun simpleHash(data: ByteArray): ByteArray {
        // Simple hash: XOR-fold with rotation to produce a 32-byte digest
        val digest = ByteArray(32)
        for (i in data.indices) {
            val idx = i % 32
            digest[idx] = (digest[idx].toInt() xor data[i].toInt()).toByte()
            // Rotate the byte at the next position
            val nextIdx = (idx + 1) % 32
            digest[nextIdx] = (digest[nextIdx].toInt() xor (data[i].toInt() shr 3)).toByte()
        }
        return digest
    }

    /**
     * Generate a unique session ID for a new contribution session.
     */
    private fun generateSessionId(networkId: String): String = "${networkId}_${clock()}"
}

/**
 * Internal registration record for a DePIN network.
 */
internal data class NetworkRegistration(
    val network: DePinNetwork,
    val resourceType: ResourceType,
    val registeredAt: Long,
)
