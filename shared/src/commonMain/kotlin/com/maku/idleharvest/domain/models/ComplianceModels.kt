package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * Configurable compliance ruleset for a specific country and carrier.
 * Enforces transaction rate limits, caps, and KYC thresholds.
 */
@Serializable
data class ComplianceRuleSet(
    val country: String,
    val carrier: String?,
    val dailyTransactionLimit: Double,
    val monthlyTransactionLimit: Double,
    val kycThreshold: Double,
    val rateLimitPerHour: Int,
    val version: Int,
    val lastUpdated: Long,
)

/**
 * Result of a compliance check on a transaction request.
 */
@Serializable
sealed class ComplianceDecision {
    /** Transaction is compliant with all applicable rules. */
    @Serializable
    data object Approved : ComplianceDecision()

    /** Transaction is blocked due to a regulatory violation. */
    @Serializable
    data class Blocked(val regulation: String, val reason: String) : ComplianceDecision()
}

/**
 * A request to perform a financial transaction, subject to compliance validation.
 */
@Serializable
data class TransactionRequest(
    val agentId: AgentId,
    val amount: Double,
    val currency: String,
    val type: TransactionType,
    val counterparty: String?,
    val platform: String,
    val country: String,
    val carrier: String,
    val timestamp: Long,
)

/**
 * An audit log entry for a compliance check performed by the Compliance_Engine.
 */
@Serializable
data class ComplianceAuditEntry(
    val id: String,
    val transactionRequest: TransactionRequest,
    val decision: ComplianceDecision,
    val appliedRules: ComplianceRuleSet,
    val timestamp: Long,
)
