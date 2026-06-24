package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.ComplianceEngine
import com.maku.idleharvest.domain.interfaces.PrivacyVault
import com.maku.idleharvest.domain.models.ComplianceAuditEntry
import com.maku.idleharvest.domain.models.ComplianceDecision
import com.maku.idleharvest.domain.models.ComplianceRuleSet
import com.maku.idleharvest.domain.models.TransactionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Default implementation of [ComplianceEngine] with per-country/carrier configurable
 * rulesets, transaction rate limits, daily/monthly caps, KYC threshold checks, and
 * comprehensive audit logging.
 *
 * Supports over-the-air rule updates without requiring an app update.
 * Persists rulesets to the [PrivacyVault] for durability across restarts.
 *
 * Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5, 12.6
 */
class DefaultComplianceEngine(
    private val vault: PrivacyVault,
    private val clock: () -> Long = { currentTimeMillis() },
) : ComplianceEngine {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /**
     * In-memory ruleset store keyed by "$country:$carrier" or "$country:*" for carrier-agnostic rules.
     */
    private val rulesets = mutableMapOf<String, ComplianceRuleSet>()

    /**
     * Audit log of all compliance checks (approved and denied).
     */
    private val _auditLog = MutableStateFlow<List<ComplianceAuditEntry>>(emptyList())

    /**
     * Transaction history for rate limit and cap tracking.
     * Each record stores agent ID, amount, country, carrier, and timestamp.
     */
    private val transactionHistory = mutableListOf<TransactionRecord>()

    override suspend fun checkTransaction(transaction: TransactionRequest): ComplianceDecision {
        mutex.withLock {
            val rules = findRules(transaction.country, transaction.carrier)
                ?: run {
                    // No rules configured = permissive (approved)
                    val decision = ComplianceDecision.Approved
                    logAudit(transaction, decision, defaultRulesFor(transaction.country, transaction.carrier))
                    return decision
                }

            // 1. Check KYC threshold - single transaction exceeding limit
            if (transaction.amount > rules.kycThreshold) {
                val decision = ComplianceDecision.Blocked(
                    regulation = "KYC_LIMIT",
                    reason =
                    "Transaction amount ${transaction.amount} exceeds KYC threshold of ${rules.kycThreshold}",
                )
                logAudit(transaction, decision, rules)
                return decision
            }

            // 2. Check rate limit (transactions per hour for this agent)
            val now = clock()
            val oneHourAgo = now - ONE_HOUR_MS
            val recentTransactions = transactionHistory.count { record ->
                record.agentId == transaction.agentId.value &&
                    record.country == transaction.country &&
                    record.carrier == transaction.carrier &&
                    record.timestamp > oneHourAgo
            }
            if (recentTransactions >= rules.rateLimitPerHour) {
                val decision = ComplianceDecision.Blocked(
                    regulation = "RATE_LIMIT",
                    reason =
                    "Rate limit of ${rules.rateLimitPerHour} tx/hr exceeded ($recentTransactions already processed)",
                )
                logAudit(transaction, decision, rules)
                return decision
            }

            // 3. Check daily transaction cap
            val startOfDay = now - (now % DAY_MS)
            val dailyTotal = transactionHistory
                .filter { record ->
                    record.agentId == transaction.agentId.value &&
                        record.country == transaction.country &&
                        record.carrier == transaction.carrier &&
                        record.timestamp >= startOfDay
                }
                .sumOf { it.amount }

            if (dailyTotal + transaction.amount > rules.dailyTransactionLimit) {
                val decision = ComplianceDecision.Blocked(
                    regulation = "DAILY_CAP",
                    reason = "Daily total ${dailyTotal + transaction.amount} exceeds limit of ${rules.dailyTransactionLimit}",
                )
                logAudit(transaction, decision, rules)
                return decision
            }

            // 4. Check monthly transaction cap
            val startOfMonth = now - (now % MONTH_MS)
            val monthlyTotal = transactionHistory
                .filter { record ->
                    record.agentId == transaction.agentId.value &&
                        record.country == transaction.country &&
                        record.carrier == transaction.carrier &&
                        record.timestamp >= startOfMonth
                }
                .sumOf { it.amount }

            if (monthlyTotal + transaction.amount > rules.monthlyTransactionLimit) {
                val decision = ComplianceDecision.Blocked(
                    regulation = "MONTHLY_CAP",
                    reason = "Monthly total ${monthlyTotal + transaction.amount} exceeds limit of ${rules.monthlyTransactionLimit}",
                )
                logAudit(transaction, decision, rules)
                return decision
            }

            // All checks passed — approve and record the transaction
            val decision = ComplianceDecision.Approved
            transactionHistory.add(
                TransactionRecord(
                    agentId = transaction.agentId.value,
                    amount = transaction.amount,
                    country = transaction.country,
                    carrier = transaction.carrier,
                    timestamp = now,
                ),
            )
            logAudit(transaction, decision, rules)
            return decision
        }
    }

    override suspend fun updateRules(rules: ComplianceRuleSet): Result<Unit> = runCatching {
        mutex.withLock {
            val key = rulesetKey(rules.country, rules.carrier)
            rulesets[key] = rules
        }
        // Persist to vault
        val vaultKey = "compliance_rules_${rules.country}"
        val rulesJson = json.encodeToString(rules)
        vault.store(vaultKey, rulesJson.encodeToByteArray()).getOrThrow()
    }

    override fun getCurrentRules(country: String, carrier: String): ComplianceRuleSet = findRules(country, carrier)
        ?: defaultRulesFor(country, carrier)

    override fun getAuditLog(): Flow<List<ComplianceAuditEntry>> = _auditLog.asStateFlow()

    /**
     * Loads persisted rules from the vault into memory.
     * Should be called during initialization.
     */
    suspend fun loadRulesFromVault(countries: List<String>) {
        for (country in countries) {
            val vaultKey = "compliance_rules_$country"
            val bytes = vault.retrieve(vaultKey).getOrNull() ?: continue
            try {
                val ruleSet: ComplianceRuleSet = json.decodeFromString(bytes.decodeToString())
                val key = rulesetKey(ruleSet.country, ruleSet.carrier)
                rulesets[key] = ruleSet
            } catch (_: Exception) {
                // Skip corrupted entries
            }
        }
    }

    // --- Private helpers ---

    /**
     * Find the most specific ruleset for a country/carrier pair.
     * Looks for exact match first ("country:carrier"), then falls back to wildcard ("country:*").
     */
    private fun findRules(country: String, carrier: String): ComplianceRuleSet? = rulesets["$country:$carrier"]
        ?: rulesets["$country:*"]

    /**
     * Build the map key for a ruleset.
     */
    private fun rulesetKey(country: String, carrier: String?): String = if (carrier != null) "$country:$carrier" else "$country:*"

    /**
     * Log an audit entry for a compliance check.
     * Appends to the in-memory audit log exposed via Flow.
     */
    private fun logAudit(
        transaction: TransactionRequest,
        decision: ComplianceDecision,
        rules: ComplianceRuleSet,
    ) {
        val entry = ComplianceAuditEntry(
            id = "${transaction.agentId.value}_${clock()}",
            transactionRequest = transaction,
            decision = decision,
            appliedRules = rules,
            timestamp = clock(),
        )
        _auditLog.value = _auditLog.value + entry
    }

    /**
     * Provides a permissive default ruleset when no rules are configured for a country/carrier.
     * This ensures audit entries always have a reference ruleset.
     */
    private fun defaultRulesFor(country: String, carrier: String): ComplianceRuleSet = ComplianceRuleSet(
        country = country,
        carrier = carrier,
        dailyTransactionLimit = Double.MAX_VALUE,
        monthlyTransactionLimit = Double.MAX_VALUE,
        kycThreshold = Double.MAX_VALUE,
        rateLimitPerHour = Int.MAX_VALUE,
        version = 0,
        lastUpdated = 0L,
    )

    companion object {
        private const val ONE_HOUR_MS = 3_600_000L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/**
 * Internal record for tracking transactions for rate limit and cap enforcement.
 */
internal data class TransactionRecord(
    val agentId: String,
    val amount: Double,
    val country: String,
    val carrier: String,
    val timestamp: Long,
)
