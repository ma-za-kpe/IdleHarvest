package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.ComplianceAuditEntry
import com.maku.idleharvest.domain.models.ComplianceDecision
import com.maku.idleharvest.domain.models.ComplianceRuleSet
import com.maku.idleharvest.domain.models.TransactionRequest
import kotlinx.coroutines.flow.Flow

/**
 * Validates agent actions against local regulations, carrier terms of service,
 * and anti-fraud rules. Maintains audit logs of all compliance checks.
 *
 * Validates: Requirements 12.1, 12.2, 12.3, 12.4
 */
interface ComplianceEngine {
    /** Check a transaction request against the applicable compliance rules. */
    suspend fun checkTransaction(transaction: TransactionRequest): ComplianceDecision

    /** Update the compliance ruleset (supports over-the-air rule updates). */
    suspend fun updateRules(rules: ComplianceRuleSet): Result<Unit>

    /** Get the current compliance rules for a specific country and carrier. */
    fun getCurrentRules(country: String, carrier: String): ComplianceRuleSet

    /** Observe the audit log of all compliance checks as a reactive stream. */
    fun getAuditLog(): Flow<List<ComplianceAuditEntry>>
}
