package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.interfaces.PolicyManager
import com.maku.idleharvest.domain.interfaces.PrivacyVault
import com.maku.idleharvest.domain.models.AgentAction
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.domain.models.PolicyDecision
import com.maku.idleharvest.domain.models.ResourceThreshold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Default implementation of [PolicyManager] with in-memory policy store
 * backed by Privacy_Vault persistence.
 *
 * Enforces per-agent autonomy levels, transaction limits, and violation logging.
 * Policies are immediately applied to running agents on update.
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
class DefaultPolicyManager(
    private val vault: PrivacyVault,
    private val eventBus: AgentEventBus,
    private val clock: () -> Long = { currentTimeMillis() },
) : PolicyManager {
    private val json = Json { ignoreUnknownKeys = true }

    private val _activePolicies = MutableStateFlow<List<Policy>>(emptyList())
    override val activePolicies: StateFlow<List<Policy>> = _activePolicies.asStateFlow()

    /**
     * Tracks daily transaction totals per agent.
     * Key: AgentId value, Value: DailyTransactionTracker
     */
    private val dailyTotals = mutableMapOf<String, DailyTransactionTracker>()

    /**
     * Loads all persisted policies from the vault into memory.
     * Should be called during initialization.
     */
    suspend fun loadFromVault() {
        val policies = mutableListOf<Policy>()
        // Load the policy index to know which policies exist
        val indexBytes = vault.retrieve(POLICY_INDEX_KEY).getOrNull()
        if (indexBytes != null) {
            val indexJson = indexBytes.decodeToString()
            val policyIds: List<String> = json.decodeFromString(indexJson)
            for (id in policyIds) {
                val policyBytes = vault.retrieve("policy_$id").getOrNull()
                if (policyBytes != null) {
                    val policy: Policy = json.decodeFromString(policyBytes.decodeToString())
                    policies.add(policy)
                }
            }
        }
        _activePolicies.value = policies
    }

    override fun setPolicy(policy: Policy) {
        require(policy.id.isNotBlank()) { "Policy ID must not be blank" }
        require(policy.agentId.value.isNotBlank()) { "Policy agent ID must not be blank" }

        val currentPolicies = _activePolicies.value.toMutableList()
        val existingIndex = currentPolicies.indexOfFirst { it.id == policy.id }

        if (existingIndex >= 0) {
            currentPolicies[existingIndex] = policy
        } else {
            currentPolicies.add(policy)
        }

        _activePolicies.value = currentPolicies
    }

    override fun removePolicy(policyId: String) {
        val currentPolicies = _activePolicies.value.toMutableList()
        currentPolicies.removeAll { it.id == policyId }
        _activePolicies.value = currentPolicies
    }

    override suspend fun checkAction(
        agentId: AgentId,
        action: AgentAction,
    ): PolicyDecision {
        val policy =
            findPolicyForAgent(agentId)
                ?: return PolicyDecision.Approved

        if (!policy.isActive) {
            return PolicyDecision.Approved
        }

        // Check autonomy level
        val autonomyDecision = checkAutonomyLevel(policy, action)
        if (autonomyDecision != null) {
            if (autonomyDecision is PolicyDecision.Denied) {
                publishViolation(agentId, action)
            }
            return autonomyDecision
        }

        // Check single transaction limit
        val singleLimitDecision = checkSingleTransactionLimit(policy, action)
        if (singleLimitDecision != null) {
            publishViolation(agentId, action)
            return singleLimitDecision
        }

        // Check daily transaction limit
        val dailyLimitDecision = checkDailyTransactionLimit(policy, agentId, action)
        if (dailyLimitDecision != null) {
            publishViolation(agentId, action)
            return dailyLimitDecision
        }

        return PolicyDecision.Approved
    }

    override fun getDefaults(): List<Policy> = listOf(
        Policy(
            id = "default_airtime",
            agentId = AgentId("airtime_agent"),
            autonomyLevel = AutonomyLevel.MANUAL,
            maxTransactionPerDay = 10.0,
            maxTransactionSingle = 5.0,
            resourceShareLimits = null,
            requireBiometricAbove = 5.0,
            isActive = true,
        ),
        Policy(
            id = "default_depin",
            agentId = AgentId("depin_agent"),
            autonomyLevel = AutonomyLevel.SEMI_AUTOMATIC,
            maxTransactionPerDay = 50.0,
            maxTransactionSingle = 20.0,
            resourceShareLimits =
            ResourceThreshold(
                bandwidthMinMbps = 2.0f,
                storageMinMb = 1000L,
                computeMaxCpuPercent = 20,
            ),
            requireBiometricAbove = 20.0,
            isActive = true,
        ),
        Policy(
            id = "default_mesh",
            agentId = AgentId("mesh_coordinator"),
            autonomyLevel = AutonomyLevel.SEMI_AUTOMATIC,
            maxTransactionPerDay = 5.0,
            maxTransactionSingle = 2.0,
            resourceShareLimits =
            ResourceThreshold(
                bandwidthMinMbps = 1.0f,
                storageMinMb = 500L,
                computeMaxCpuPercent = 15,
            ),
            requireBiometricAbove = 5.0,
            isActive = true,
        ),
        Policy(
            id = "default_earning",
            agentId = AgentId("earning_engine"),
            autonomyLevel = AutonomyLevel.MANUAL,
            maxTransactionPerDay = 100.0,
            maxTransactionSingle = 50.0,
            resourceShareLimits = null,
            requireBiometricAbove = 25.0,
            isActive = true,
        ),
    )

    /**
     * Persists the current policy state to the vault.
     * Call after setPolicy/removePolicy to ensure durability across restarts.
     */
    suspend fun persistToVault() {
        val policies = _activePolicies.value

        // Store the policy index
        val policyIds = policies.map { it.id }
        val indexJson = json.encodeToString(policyIds)
        vault.store(POLICY_INDEX_KEY, indexJson.encodeToByteArray())

        // Store each policy individually
        for (policy in policies) {
            val policyJson = json.encodeToString(policy)
            vault.store("policy_${policy.id}", policyJson.encodeToByteArray())
        }
    }

    // --- Private helpers ---

    private fun findPolicyForAgent(agentId: AgentId): Policy? = _activePolicies.value.firstOrNull {
        it.agentId ==
            agentId
    }

    /**
     * Checks autonomy level rules.
     * Returns a PolicyDecision if the action should be blocked/requires approval, or null if it passes.
     */
    private fun checkAutonomyLevel(
        policy: Policy,
        action: AgentAction,
    ): PolicyDecision? = when (policy.autonomyLevel) {
        AutonomyLevel.MANUAL -> {
            // All actions require user approval
            PolicyDecision.RequiresApproval(action)
        }

        AutonomyLevel.SEMI_AUTOMATIC -> {
            // Financial actions need approval, resource actions auto-approve
            if (isFinancialAction(action)) {
                PolicyDecision.RequiresApproval(action)
            } else {
                null // approved, continue checks
            }
        }

        AutonomyLevel.FULLY_AUTOMATIC -> {
            // All actions auto-approve (within limits checked separately)
            null
        }
    }

    /**
     * Checks if the action exceeds the single transaction limit.
     */
    private fun checkSingleTransactionLimit(
        policy: Policy,
        action: AgentAction,
    ): PolicyDecision? {
        val limit = policy.maxTransactionSingle ?: return null
        val amount = action.amountUsdc ?: return null

        if (amount > limit) {
            return PolicyDecision.Denied(
                reason = "Transaction amount $amount exceeds single transaction limit of $limit",
                violatedPolicy = policy,
            )
        }
        return null
    }

    /**
     * Checks if the action would exceed the daily transaction limit for this agent.
     */
    private fun checkDailyTransactionLimit(
        policy: Policy,
        agentId: AgentId,
        action: AgentAction,
    ): PolicyDecision? {
        val limit = policy.maxTransactionPerDay ?: return null
        val amount = action.amountUsdc ?: return null

        val now = clock()
        val tracker = dailyTotals.getOrPut(agentId.value) { DailyTransactionTracker(0.0, now) }

        // Reset tracker if we crossed into a new day (24h window)
        val dayMs = 24 * 60 * 60 * 1000L
        if (now - tracker.dayStartTimestamp >= dayMs) {
            dailyTotals[agentId.value] = DailyTransactionTracker(0.0, now)
        }

        val currentTracker = dailyTotals[agentId.value]!!
        val projectedTotal = currentTracker.totalAmount + amount

        if (projectedTotal > limit) {
            return PolicyDecision.Denied(
                reason = "Daily transaction total would be $projectedTotal, exceeding daily limit of $limit",
                violatedPolicy = policy,
            )
        }

        // Update the tracker with the new amount (action is approved at this point)
        dailyTotals[agentId.value] = currentTracker.copy(totalAmount = projectedTotal)
        return null
    }

    private fun isFinancialAction(action: AgentAction): Boolean = action.amountUsdc != null && action.amountUsdc > 0.0

    private fun publishViolation(
        agentId: AgentId,
        action: AgentAction,
    ) {
        eventBus.publish(AgentEvent.PolicyViolation(agentId, action))
    }

    companion object {
        private const val POLICY_INDEX_KEY = "policy_index"
    }
}

/**
 * Tracks cumulative transaction amounts for an agent within a daily window.
 */
internal data class DailyTransactionTracker(
    val totalAmount: Double,
    val dayStartTimestamp: Long,
)
