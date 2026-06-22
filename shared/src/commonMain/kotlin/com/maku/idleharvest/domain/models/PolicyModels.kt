package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * User-defined policy controlling an agent's autonomy boundaries.
 * Stored in the Privacy_Vault and enforced by the Policy_Manager.
 */
@Serializable
data class Policy(
    val id: String,
    val agentId: AgentId,
    val autonomyLevel: AutonomyLevel,
    val maxTransactionPerDay: Double?,
    val maxTransactionSingle: Double?,
    val resourceShareLimits: ResourceThreshold?,
    val requireBiometricAbove: Double?,
    val isActive: Boolean = true,
)

/**
 * Result of a policy check against an agent action.
 */
@Serializable
sealed class PolicyDecision {
    /** Action is permitted under current policies. */
    @Serializable
    data object Approved : PolicyDecision()

    /** Action is blocked by a policy; includes violation reason and the violated policy. */
    @Serializable
    data class Denied(
        val reason: String,
        val violatedPolicy: Policy,
    ) : PolicyDecision()

    /** Action requires explicit user approval before proceeding. */
    @Serializable
    data class RequiresApproval(
        val action: AgentAction,
    ) : PolicyDecision()
}

/**
 * Represents an action an agent wants to perform, subject to policy checks.
 */
@Serializable
data class AgentAction(
    val agentId: AgentId,
    val actionType: String,
    val description: String,
    val amountUsdc: Double?,
    val resourceImpact: ResourceThreshold?,
    val timestamp: Long,
)
