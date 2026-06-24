package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentState
import com.maku.idleharvest.domain.models.DePinNetwork
import com.maku.idleharvest.domain.models.ResourceThreshold
import com.maku.idleharvest.domain.models.ResourceType
import com.maku.idleharvest.generators.resourceThreshold
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 7: DePIN Threshold Enforcement
 *
 * *For any* resource level and user-defined sharing threshold, the DePIN_Agent SHALL
 * contribute resources only when the available amount exceeds the threshold. When
 * resources drop below the threshold, contributions SHALL be reduced or paused.
 *
 * **Validates: Requirements 3.2, 3.3**
 */
class DePinThresholdPropertyTest {
    private val testNetwork =
        DePinNetwork(
            id = "test-network-1",
            name = "TestNet",
            supportedResources = listOf(ResourceType.BANDWIDTH, ResourceType.STORAGE, ResourceType.COMPUTE),
            tokenSymbol = "TEST",
            endpointUrl = "https://testnet.example.com",
        )

    private fun createTestAgent(): DefaultDePinAgent {
        val vault = DefaultPrivacyVault(SimpleCryptoProvider())
        val eventBus = DefaultAgentEventBus()
        val policyManager = DefaultPolicyManager(vault, eventBus)
        return DefaultDePinAgent(
            vault = vault,
            eventBus = eventBus,
            policyManager = policyManager,
        )
    }

    @Test
    fun contributionsPausedWhenThresholdIndicatesNoCapacity() = runTest {
        forAll(Arb.resourceThreshold()) { threshold ->
            val agent = createTestAgent()
            agent.register(testNetwork, ResourceType.BANDWIDTH)

            // Set threshold to indicate no available capacity (computeMaxCpuPercent = 0)
            val pauseThreshold = threshold.copy(computeMaxCpuPercent = 0)
            agent.adjustContribution(pauseThreshold)

            // Agent should be paused and report itself as paused
            agent.isContributionPaused() && agent.state.value == AgentState.PAUSED
        }
    }

    @Test
    fun contributionsResumeWhenThresholdIndicatesAvailableCapacity() = runTest {
        forAll(Arb.int(1..100)) { cpuPercent ->
            val agent = createTestAgent()
            agent.register(testNetwork, ResourceType.BANDWIDTH)

            // First pause the agent
            agent.adjustContribution(ResourceThreshold(computeMaxCpuPercent = 0))
            val wasPaused = agent.isContributionPaused()

            // Then resume with capacity > 0
            agent.adjustContribution(ResourceThreshold(computeMaxCpuPercent = cpuPercent))

            // Agent should no longer be paused and should be executing
            wasPaused && !agent.isContributionPaused() && agent.state.value == AgentState.EXECUTING
        }
    }

    @Test
    fun pausingEndsActiveContributionSessions() = runTest {
        forAll(Arb.resourceThreshold()) { threshold ->
            val agent = createTestAgent()
            agent.register(testNetwork, ResourceType.BANDWIDTH)

            // Verify there's an active (non-ended) contribution
            val hasActiveBeforePause = agent.contributions.value.any { it.endedAt == null }

            // Pause by setting threshold to no capacity
            val pauseThreshold = threshold.copy(computeMaxCpuPercent = 0)
            agent.adjustContribution(pauseThreshold)

            // All contribution sessions should now be ended (endedAt != null)
            val allEnded = agent.contributions.value.all { it.endedAt != null }

            hasActiveBeforePause && allEnded
        }
    }

    @Test
    fun resumingCreatesNewContributionSessions() = runTest {
        forAll(Arb.int(1..100)) { cpuPercent ->
            val agent = createTestAgent()
            agent.register(testNetwork, ResourceType.BANDWIDTH)

            // Pause contributions
            agent.adjustContribution(ResourceThreshold(computeMaxCpuPercent = 0))
            val countAfterPause = agent.contributions.value.size

            // Resume contributions
            agent.adjustContribution(ResourceThreshold(computeMaxCpuPercent = cpuPercent))

            // New contribution session should be created
            val countAfterResume = agent.contributions.value.size
            val hasNewActive = agent.contributions.value.any { it.endedAt == null }

            countAfterResume > countAfterPause && hasNewActive
        }
    }

    @Test
    fun negativeComputeMaxAlsoPausesContributions() = runTest {
        forAll(Arb.int(-100..-1)) { negativeCpu ->
            val agent = createTestAgent()
            agent.register(testNetwork, ResourceType.BANDWIDTH)

            // Set threshold with negative computeMaxCpuPercent (below zero = no capacity)
            agent.adjustContribution(ResourceThreshold(computeMaxCpuPercent = negativeCpu))

            agent.isContributionPaused() && agent.state.value == AgentState.PAUSED
        }
    }
}
