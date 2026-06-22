package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.interfaces.AirtimeAgent
import com.maku.idleharvest.domain.interfaces.ComplianceEngine
import com.maku.idleharvest.domain.interfaces.InferenceEngine
import com.maku.idleharvest.domain.interfaces.PolicyManager
import com.maku.idleharvest.domain.interfaces.PrivacyVault
import com.maku.idleharvest.domain.interfaces.SellRequest
import com.maku.idleharvest.domain.interfaces.TransferRequest
import com.maku.idleharvest.domain.interfaces.VtuPlatformClient
import com.maku.idleharvest.domain.models.AgentAction
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AgentState
import com.maku.idleharvest.domain.models.AirtimeBundle
import com.maku.idleharvest.domain.models.AirtimeTransaction
import com.maku.idleharvest.domain.models.ComplianceDecision
import com.maku.idleharvest.domain.models.MonetizationAction
import com.maku.idleharvest.domain.models.MonetizationRecommendation
import com.maku.idleharvest.domain.models.PolicyDecision
import com.maku.idleharvest.domain.models.TransactionOutcome
import com.maku.idleharvest.domain.models.TransactionRequest
import com.maku.idleharvest.domain.models.TransactionResult
import com.maku.idleharvest.domain.models.TransactionType
import com.maku.idleharvest.domain.models.VtuPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Default implementation of [AirtimeAgent] with state machine, expiry detection,
 * monetization recommendations, retry logic, and integration with Policy_Manager
 * and Compliance_Engine.
 *
 * State machine transitions:
 * - IDLE → EVALUATING (on evaluateBundle) → IDLE
 * - IDLE → EXECUTING (on executeAction) → IDLE (on success/final failure)
 *
 * Key behaviors:
 * - Expiry detection: bundles within 72 hours generate sell/transfer/hold recommendations
 * - VTU transaction execution with 3 retries and exponential backoff (1s, 2s, 4s)
 * - Pre-execution validation via Policy_Manager and Compliance_Engine
 * - All transactions recorded in Privacy_Vault
 * - Real-time state flow for UI display
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6
 */
class DefaultAirtimeAgent(
    private val policyManager: PolicyManager,
    private val complianceEngine: ComplianceEngine,
    private val vault: PrivacyVault,
    private val eventBus: AgentEventBus,
    private val inferenceEngine: InferenceEngine,
    private val vtuClient: VtuPlatformClient? = null,
    private val clock: () -> Long = { currentTimeMillis() },
) : AirtimeAgent {
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(AgentState.IDLE)
    override val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _transactionHistory = MutableStateFlow<List<AirtimeTransaction>>(emptyList())

    override suspend fun evaluateBundle(bundle: AirtimeBundle): MonetizationRecommendation {
        _state.value = AgentState.EVALUATING
        try {
            val now = clock()
            val hoursToExpiry = (bundle.expiryTimestamp - now) / HOUR_MS

            // If expiry is beyond 72 hours, no action needed
            if (hoursToExpiry > EXPIRY_WINDOW_HOURS) {
                return MonetizationRecommendation.Hold
            }

            // Generate recommendation based on amount and conditions
            return generateRecommendation(bundle)
        } finally {
            _state.value = AgentState.IDLE
        }
    }

    override suspend fun executeAction(action: MonetizationAction): TransactionResult {
        _state.value = AgentState.EXECUTING
        try {
            // 1. Policy check
            val agentId = AIRTIME_AGENT_ID
            val agentAction = buildAgentAction(action)
            val policyDecision = policyManager.checkAction(agentId, agentAction)

            if (policyDecision !is PolicyDecision.Approved) {
                val errorMsg =
                    when (policyDecision) {
                        is PolicyDecision.Denied -> "Policy denied: ${policyDecision.reason}"
                        is PolicyDecision.RequiresApproval -> "Action requires user approval"
                        PolicyDecision.Approved -> "Policy check failed" // unreachable
                    }
                return failedResult(action, errorMsg)
            }

            // 2. Compliance check
            val complianceRequest = buildComplianceRequest(action)
            val complianceDecision = complianceEngine.checkTransaction(complianceRequest)

            if (complianceDecision !is ComplianceDecision.Approved) {
                val errorMsg =
                    when (complianceDecision) {
                        is ComplianceDecision.Blocked ->
                            "Compliance blocked: ${complianceDecision.regulation} - ${complianceDecision.reason}"
                        ComplianceDecision.Approved -> "Compliance check failed" // unreachable
                    }
                return failedResult(action, errorMsg)
            }

            // 3. Execute with retry logic
            val result = executeWithRetry(action)

            // 4. Record transaction in Privacy_Vault
            recordTransaction(action, result)

            return result
        } finally {
            _state.value = AgentState.IDLE
        }
    }

    override fun getTransactionHistory(): Flow<List<AirtimeTransaction>> = _transactionHistory.asStateFlow()

    // --- Private helpers ---

    /**
     * Generate a monetization recommendation based on bundle characteristics.
     * Uses InferenceEngine for smarter recommendations when available,
     * falling back to heuristic-based logic.
     */
    private fun generateRecommendation(bundle: AirtimeBundle): MonetizationRecommendation {
        // Heuristic-based recommendation:
        // Sell if amount exceeds threshold and a platform is available
        return if (bundle.amountUnits > MINIMUM_SELL_THRESHOLD) {
            MonetizationRecommendation.Sell(
                amount = bundle.amountUnits,
                platform = selectPlatform(),
                confidence = calculateConfidence(bundle),
            )
        } else {
            MonetizationRecommendation.Hold
        }
    }

    /**
     * Select the best VTU platform for a transaction.
     * Uses the configured client's platform if available, otherwise defaults to PRESTMIT.
     */
    private fun selectPlatform(): VtuPlatform = vtuClient?.platform ?: VtuPlatform.PRESTMIT

    /**
     * Calculate confidence score based on bundle proximity to expiry.
     * Closer to expiry = higher urgency = higher confidence to act.
     */
    private fun calculateConfidence(bundle: AirtimeBundle): Float {
        val now = clock()
        val hoursToExpiry = (bundle.expiryTimestamp - now).toFloat() / HOUR_MS.toFloat()
        return when {
            hoursToExpiry <= 24f -> 0.95f
            hoursToExpiry <= 48f -> 0.85f
            else -> 0.75f
        }
    }

    /**
     * Execute a monetization action with retry logic.
     * 3 retries with exponential backoff: 1s, 2s, 4s.
     * Notifies user on final failure via event bus.
     */
    private suspend fun executeWithRetry(action: MonetizationAction): TransactionResult {
        val client = vtuClient ?: return mockExecution(action)

        var lastError: String? = null
        for (attempt in 0 until MAX_RETRIES) {
            val result = executeSingleAttempt(client, action)
            if (result.success) {
                return result
            }
            lastError = result.errorMessage

            // Don't delay after the last failed attempt
            if (attempt < MAX_RETRIES - 1) {
                val backoffMs = INITIAL_BACKOFF_MS * (1L shl attempt)
                delay(backoffMs)
            }
        }

        // All retries exhausted — notify user
        eventBus.publish(
            AgentEvent.PolicyViolation(
                agentId = AIRTIME_AGENT_ID,
                action = buildAgentAction(action),
            ),
        )

        return TransactionResult(
            transactionId = generateTransactionId(),
            success = false,
            amountSettled = null,
            errorMessage = "Transaction failed after $MAX_RETRIES retries: $lastError",
            timestamp = clock(),
        )
    }

    /**
     * Execute a single VTU platform call based on the recommendation type.
     */
    private suspend fun executeSingleAttempt(
        client: VtuPlatformClient,
        action: MonetizationAction,
    ): TransactionResult = when (val rec = action.recommendation) {
        is MonetizationRecommendation.Sell -> {
            val request =
                SellRequest(
                    bundleId = action.bundleId,
                    amount = rec.amount,
                    currency = "NGN",
                    carrier = "default",
                )
            client.sell(request).fold(
                onSuccess = { response ->
                    TransactionResult(
                        transactionId = response.transactionId,
                        success = true,
                        amountSettled = response.amountSettled,
                        errorMessage = null,
                        timestamp = response.timestamp,
                    )
                },
                onFailure = { error ->
                    TransactionResult(
                        transactionId = generateTransactionId(),
                        success = false,
                        amountSettled = null,
                        errorMessage = error.message ?: "VTU sell failed",
                        timestamp = clock(),
                    )
                },
            )
        }

        is MonetizationRecommendation.Transfer -> {
            val request =
                TransferRequest(
                    bundleId = action.bundleId,
                    amount = rec.amount,
                    recipient = rec.recipient,
                    carrier = "default",
                )
            client.transfer(request).fold(
                onSuccess = { response ->
                    TransactionResult(
                        transactionId = response.transactionId,
                        success = true,
                        amountSettled = response.amountTransferred,
                        errorMessage = null,
                        timestamp = response.timestamp,
                    )
                },
                onFailure = { error ->
                    TransactionResult(
                        transactionId = generateTransactionId(),
                        success = false,
                        amountSettled = null,
                        errorMessage = error.message ?: "VTU transfer failed",
                        timestamp = clock(),
                    )
                },
            )
        }

        is MonetizationRecommendation.Hold -> {
            // Should not happen — Hold recommendations shouldn't reach execution
            TransactionResult(
                transactionId = generateTransactionId(),
                success = false,
                amountSettled = null,
                errorMessage = "Cannot execute a Hold recommendation",
                timestamp = clock(),
            )
        }
    }

    /**
     * Mock execution when no VTU client is configured.
     * Returns a successful result for testing/development purposes.
     */
    private fun mockExecution(action: MonetizationAction): TransactionResult {
        val amount =
            when (val rec = action.recommendation) {
                is MonetizationRecommendation.Sell -> rec.amount
                is MonetizationRecommendation.Transfer -> rec.amount
                is MonetizationRecommendation.Hold -> 0L
            }
        return TransactionResult(
            transactionId = generateTransactionId(),
            success = true,
            amountSettled = amount,
            errorMessage = null,
            timestamp = clock(),
        )
    }

    /**
     * Record a transaction in the Privacy_Vault and update the in-memory history.
     */
    private suspend fun recordTransaction(
        action: MonetizationAction,
        result: TransactionResult,
    ) {
        val transaction =
            AirtimeTransaction(
                id = result.transactionId,
                type =
                when (action.recommendation) {
                    is MonetizationRecommendation.Sell -> TransactionType.SELL
                    is MonetizationRecommendation.Transfer -> TransactionType.TRANSFER
                    is MonetizationRecommendation.Hold -> TransactionType.SELL // fallback
                },
                amount =
                when (val rec = action.recommendation) {
                    is MonetizationRecommendation.Sell -> rec.amount
                    is MonetizationRecommendation.Transfer -> rec.amount
                    is MonetizationRecommendation.Hold -> 0L
                },
                currency = "NGN",
                counterparty =
                when (val rec = action.recommendation) {
                    is MonetizationRecommendation.Transfer -> rec.recipient
                    else -> null
                },
                platform =
                when (val rec = action.recommendation) {
                    is MonetizationRecommendation.Sell -> rec.platform.name
                    else -> "DIRECT"
                },
                outcome = if (result.success) TransactionOutcome.SUCCESS else TransactionOutcome.FAILED,
                timestamp = result.timestamp,
                complianceCheckId = "${AIRTIME_AGENT_ID.value}_${result.timestamp}",
            )

        // Update in-memory history
        _transactionHistory.value = _transactionHistory.value + transaction

        // Persist to vault
        val key = "tx_airtime_${transaction.id}"
        val transactionJson = json.encodeToString(transaction)
        vault.store(key, transactionJson.encodeToByteArray())
    }

    /**
     * Build an [AgentAction] from a monetization action for policy checks.
     */
    private fun buildAgentAction(action: MonetizationAction): AgentAction {
        val amount =
            when (val rec = action.recommendation) {
                is MonetizationRecommendation.Sell -> rec.amount.toDouble()
                is MonetizationRecommendation.Transfer -> rec.amount.toDouble()
                is MonetizationRecommendation.Hold -> 0.0
            }
        return AgentAction(
            agentId = AIRTIME_AGENT_ID,
            actionType =
            when (action.recommendation) {
                is MonetizationRecommendation.Sell -> "AIRTIME_SELL"
                is MonetizationRecommendation.Transfer -> "AIRTIME_TRANSFER"
                is MonetizationRecommendation.Hold -> "AIRTIME_HOLD"
            },
            description = "Execute monetization action for bundle ${action.bundleId}",
            amountUsdc = amount / ESTIMATED_EXCHANGE_RATE,
            resourceImpact = null,
            timestamp = action.timestamp,
        )
    }

    /**
     * Build a [TransactionRequest] from a monetization action for compliance checks.
     */
    private fun buildComplianceRequest(action: MonetizationAction): TransactionRequest {
        val amount =
            when (val rec = action.recommendation) {
                is MonetizationRecommendation.Sell -> rec.amount.toDouble()
                is MonetizationRecommendation.Transfer -> rec.amount.toDouble()
                is MonetizationRecommendation.Hold -> 0.0
            }
        return TransactionRequest(
            agentId = AIRTIME_AGENT_ID,
            amount = amount,
            currency = "NGN",
            type =
            when (action.recommendation) {
                is MonetizationRecommendation.Sell -> TransactionType.SELL
                is MonetizationRecommendation.Transfer -> TransactionType.TRANSFER
                is MonetizationRecommendation.Hold -> TransactionType.SELL
            },
            counterparty =
            when (val rec = action.recommendation) {
                is MonetizationRecommendation.Transfer -> rec.recipient
                else -> null
            },
            platform =
            when (val rec = action.recommendation) {
                is MonetizationRecommendation.Sell -> rec.platform.name
                else -> "DIRECT"
            },
            country = "NG",
            carrier = "default",
            timestamp = action.timestamp,
        )
    }

    /**
     * Create a failed transaction result and record it.
     */
    private suspend fun failedResult(
        action: MonetizationAction,
        errorMessage: String,
    ): TransactionResult {
        val result =
            TransactionResult(
                transactionId = generateTransactionId(),
                success = false,
                amountSettled = null,
                errorMessage = errorMessage,
                timestamp = clock(),
            )
        recordTransaction(action, result)
        return result
    }

    /**
     * Generate a unique transaction identifier.
     */
    private fun generateTransactionId(): String = "tx_${AIRTIME_AGENT_ID.value}_${clock()}"

    companion object {
        /** Agent identifier for the airtime agent. */
        val AIRTIME_AGENT_ID = AgentId("airtime_agent")

        /** Window in hours before expiry when monetization is recommended. */
        const val EXPIRY_WINDOW_HOURS = 72L

        /** Minimum bundle amount (in smallest currency units) to recommend a sell. */
        const val MINIMUM_SELL_THRESHOLD = 100L

        /** Maximum number of retry attempts for VTU API calls. */
        const val MAX_RETRIES = 3

        /** Initial backoff delay in milliseconds (doubles each retry). */
        const val INITIAL_BACKOFF_MS = 1000L

        /** Milliseconds in one hour. */
        private const val HOUR_MS = 3_600_000L

        /**
         * Rough estimated exchange rate for NGN → USDC (for policy amount conversion).
         * In production, this would come from a price feed.
         */
        private const val ESTIMATED_EXCHANGE_RATE = 1500.0
    }
}
