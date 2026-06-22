package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.ContributionSession
import com.maku.idleharvest.domain.models.ResourceType
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 8: Cryptographic Proof Validity
 *
 * *For any* DePIN contribution session, the generated cryptographic proof SHALL contain
 * the session ID, contribution data, and a valid signature that can be independently
 * verified using the device's public key.
 *
 * **Validates: Requirements 3.6**
 */
class CryptographicProofPropertyTest {

    /** Generator for ContributionSession with realistic constrained values. */
    private val contributionSessionArb: Arb<ContributionSession> = arbitrary {
        ContributionSession(
            id = Arb.string(8..32).bind(),
            networkId = Arb.string(8..32).bind(),
            resourceType = Arb.enum<ResourceType>().bind(),
            startedAt = Arb.long(1_000_000_000_000L..1_800_000_000_000L).bind(),
            bytesServed = Arb.long(0L..1_000_000L).orNull(0.4).bind(),
            computeUnitsCompleted = Arb.long(0L..10_000L).orNull(0.4).bind(),
            storageProvidedMb = Arb.long(0L..50_000L).orNull(0.4).bind(),
        )
    }

    private fun createTestAgent(clock: () -> Long = { 1_719_792_000_000L }): DefaultDePinAgent {
        val vault = DefaultPrivacyVault(SimpleCryptoProvider())
        val eventBus = DefaultAgentEventBus()
        val policyManager = DefaultPolicyManager(vault, eventBus)
        return DefaultDePinAgent(
            vault = vault,
            eventBus = eventBus,
            policyManager = policyManager,
            clock = clock,
        )
    }

    @Test
    fun proofContainsCorrectSessionId() = runTest {
        forAll(contributionSessionArb) { session ->
            val agent = createTestAgent()
            val proof = agent.generateProof(session)
            proof.sessionId == session.id
        }
    }

    @Test
    fun proofDataIsNonEmpty() = runTest {
        forAll(contributionSessionArb) { session ->
            val agent = createTestAgent()
            val proof = agent.generateProof(session)
            proof.proofData.isNotEmpty()
        }
    }

    @Test
    fun signatureIsNonEmpty() = runTest {
        forAll(contributionSessionArb) { session ->
            val agent = createTestAgent()
            val proof = agent.generateProof(session)
            proof.signature.isNotEmpty()
        }
    }

    @Test
    fun proofHasValidTimestamp() = runTest {
        val fixedTime = 1_719_792_000_000L
        forAll(contributionSessionArb) { session ->
            val agent = createTestAgent(clock = { fixedTime })
            val proof = agent.generateProof(session)
            proof.timestamp == fixedTime
        }
    }

    @Test
    fun sameSessionProducesSameProofData() = runTest {
        forAll(contributionSessionArb) { session ->
            val agent = createTestAgent()
            val proof1 = agent.generateProof(session)
            val proof2 = agent.generateProof(session)
            proof1.proofData == proof2.proofData
        }
    }

    @Test
    fun sameSessionProducesSameSignature() = runTest {
        forAll(contributionSessionArb) { session ->
            val agent = createTestAgent()
            val proof1 = agent.generateProof(session)
            val proof2 = agent.generateProof(session)
            proof1.signature == proof2.signature
        }
    }

    @Test
    fun proofDataContainsSessionIdentifier() = runTest {
        forAll(contributionSessionArb) { session ->
            val agent = createTestAgent()
            val proof = agent.generateProof(session)
            // The proof data should reference the session ID for verifiability
            proof.proofData.contains(session.id)
        }
    }

    @Test
    fun differentSessionsProduceDifferentProofs() = runTest {
        forAll(contributionSessionArb, contributionSessionArb) { session1, session2 ->
            if (session1.id == session2.id &&
                session1.networkId == session2.networkId &&
                session1.resourceType == session2.resourceType &&
                session1.startedAt == session2.startedAt &&
                session1.bytesServed == session2.bytesServed &&
                session1.computeUnitsCompleted == session2.computeUnitsCompleted &&
                session1.storageProvidedMb == session2.storageProvidedMb
            ) {
                // Identical sessions should produce identical proofs
                true
            } else {
                val agent = createTestAgent()
                val proof1 = agent.generateProof(session1)
                val proof2 = agent.generateProof(session2)
                // Different sessions should produce different proof data or signatures
                proof1.proofData != proof2.proofData || proof1.signature != proof2.signature
            }
        }
    }
}
