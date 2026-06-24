package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.AgentAction
import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.domain.models.PolicyDecision
import kotlinx.coroutines.flow.StateFlow

/**
 * User-defined guardrails controlling agent autonomy boundaries.
 * All agent actions must pass policy checks before execution.
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4
 */
interface PolicyManager {
    /** All currently active policies. */
    val activePolicies: StateFlow<List<Policy>>

    /** Create or update a policy. Applied immediately to running agents. */
    fun setPolicy(policy: Policy)

    /** Remove a policy by its identifier. */
    fun removePolicy(policyId: String)

    /** Check whether an agent action is permitted under current policies. */
    suspend fun checkAction(
        agentId: AgentId,
        action: AgentAction,
    ): PolicyDecision

    /** Get the default conservative policies for first-time users. */
    fun getDefaults(): List<Policy>
}
