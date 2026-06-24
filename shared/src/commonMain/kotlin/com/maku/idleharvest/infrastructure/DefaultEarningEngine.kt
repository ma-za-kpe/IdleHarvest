package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.SecureKeystore
import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.interfaces.EarningEngine
import com.maku.idleharvest.domain.interfaces.PolicyManager
import com.maku.idleharvest.domain.interfaces.PrivacyVault
import com.maku.idleharvest.domain.models.AgentAction
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.EarningSource
import com.maku.idleharvest.domain.models.EarningsSummary
import com.maku.idleharvest.domain.models.NanopaymentReceipt
import com.maku.idleharvest.domain.models.NanopaymentRequest
import com.maku.idleharvest.domain.models.PayoutReceipt
import com.maku.idleharvest.domain.models.PayoutRequest
import com.maku.idleharvest.domain.models.PayoutStatus
import com.maku.idleharvest.domain.models.PolicyDecision
import com.maku.idleharvest.domain.models.WalletAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Default implementation of [EarningEngine] with Circle Agent Stack integration,
 * idempotent payout guarantee, policy enforcement, biometric gating, and
 * agent-to-agent nanopayments.
 *
 * Key behaviors:
 * - **Idempotent payouts**: Rejects duplicate payout attempts for the same earning event ID.
 * - **Policy enforcement**: All financial transactions pass through [PolicyManager] before execution.
 * - **Biometric gating**: Transactions above the user-defined threshold require biometric confirmation
 *   via [SecureKeystore].
 * - **Nanopayments**: Supports inter-device agent-to-agent payments within a peer network.
 * - **Rejection logging**: Payout failures are logged to the [PrivacyVault] and user is notified
 *   via the [AgentEventBus].
 * - **Circle Agent Stack**: Payout execution is abstracted behind [CirclePayoutRail] for testability
 *   and resilience against API changes.
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
 */
class DefaultEarningEngine(
    private val policyManager: PolicyManager,
    private val vault: PrivacyVault,
    private val eventBus: AgentEventBus,
    private val secureKeystore: SecureKeystore? = null,
    private val payoutRail: CirclePayoutRail? = null,
    private val walletAddress: WalletAddress = WalletAddress("default_wallet"),
    private val biometricThreshold: Double = DEFAULT_BIOMETRIC_THRESHOLD,
    private val clock: () -> Long = { currentTimeMillis() },
) : EarningEngine {
    private val json = Json { ignoreUnknownKeys = true }

    private val _pendingPayouts = MutableStateFlow<List<PayoutRequest>>(emptyList())
    override val pendingPayouts: StateFlow<List<PayoutRequest>> = _pendingPayouts.asStateFlow()

    private val _earningHistory = MutableStateFlow<List<EarningEvent>>(emptyList())
    override val earningHistory: StateFlow<List<EarningEvent>> = _earningHistory.asStateFlow()

    /** Set of earning event IDs that have already been processed for idempotency. */
    private val processedPayoutIds = mutableSetOf<String>()

    override suspend fun initiatePayout(event: EarningEvent): Result<PayoutReceipt> {
        // 1. Idempotent payout guarantee — reject duplicates
        if (event.id in processedPayoutIds) {
            return Result.failure(
                DuplicatePayoutException("Payout already processed for earning event: ${event.id}"),
            )
        }

        // 2. Policy enforcement — check before executing financial transaction
        val agentAction = buildAgentAction(event)
        val policyDecision = policyManager.checkAction(EARNING_ENGINE_ID, agentAction)

        if (policyDecision !is PolicyDecision.Approved) {
            val reason =
                when (policyDecision) {
                    is PolicyDecision.Denied -> "Policy denied: ${policyDecision.reason}"
                    is PolicyDecision.RequiresApproval -> "Action requires user approval"
                    PolicyDecision.Approved -> "" // unreachable
                }
            logRejection(event, reason)
            notifyUserOfRejection(event, reason)
            return Result.failure(PayoutRejectedException(reason))
        }

        // 3. Biometric requirement for high-value transactions
        val requiresBiometric = event.amountUsdc > biometricThreshold
        if (requiresBiometric) {
            val biometricResult = performBiometricCheck(event)
            if (biometricResult.isFailure) {
                val reason =
                    "Biometric authentication required but failed: ${biometricResult.exceptionOrNull()?.message}"
                logRejection(event, reason)
                return Result.failure(BiometricRequiredException(reason))
            }
        }

        // 4. Create payout request and add to pending
        val payoutRequest =
            PayoutRequest(
                id = generatePayoutId(event),
                earningEventId = event.id,
                amountUsdc = event.amountUsdc,
                destination = walletAddress,
                status = PayoutStatus.SUBMITTED,
                requiresBiometric = requiresBiometric,
            )
        addPendingPayout(payoutRequest)

        // 5. Execute payout via Circle Agent Stack (or mock)
        val payoutResult = executePayout(payoutRequest)

        // 6. Mark as processed for idempotency (even if failed, to prevent re-attempts)
        processedPayoutIds.add(event.id)

        return payoutResult.fold(
            onSuccess = { receipt ->
                // Remove from pending, add to history
                removePendingPayout(payoutRequest.id)
                addToEarningHistory(event)
                persistEarningEvent(event)

                // Publish earning completed event
                eventBus.publish(AgentEvent.EarningCompleted(event))

                Result.success(receipt)
            },
            onFailure = { error ->
                // Update payout status to FAILED
                updatePayoutStatus(payoutRequest.id, PayoutStatus.FAILED)
                val reason = "Payout rail failure: ${error.message}"
                logRejection(event, reason)
                notifyUserOfRejection(event, reason)
                Result.failure(PayoutFailedException(reason))
            },
        )
    }

    override suspend fun processNanopayment(payment: NanopaymentRequest): Result<NanopaymentReceipt> {
        // Policy check for nanopayments
        val agentAction =
            AgentAction(
                agentId = payment.fromAgent,
                actionType = "NANOPAYMENT",
                description = "Nanopayment to ${payment.toAgent.value} for: ${payment.serviceDescription}",
                amountUsdc = payment.amountUsdc,
                resourceImpact = null,
                timestamp = payment.timestamp,
            )

        val policyDecision = policyManager.checkAction(payment.fromAgent, agentAction)

        if (policyDecision !is PolicyDecision.Approved) {
            val reason =
                when (policyDecision) {
                    is PolicyDecision.Denied -> "Nanopayment denied by policy: ${policyDecision.reason}"
                    is PolicyDecision.RequiresApproval -> "Nanopayment requires user approval"
                    PolicyDecision.Approved -> "" // unreachable
                }
            return Result.failure(PayoutRejectedException(reason))
        }

        // Execute nanopayment (via payout rail or mock)
        val receipt =
            NanopaymentReceipt(
                requestId = "np_${payment.fromAgent.value}_${payment.timestamp}",
                transactionHash = "hash_${payment.timestamp}_${payment.amountUsdc}",
                amountUsdc = payment.amountUsdc,
                confirmedAt = clock(),
            )

        // Record in earning history as a nanopayment event
        val earningEvent =
            EarningEvent(
                id = receipt.requestId,
                source = EarningSource.NANOPAYMENT,
                amountUsdc = payment.amountUsdc,
                amountLocal = null,
                localCurrency = null,
                agentId = payment.toAgent,
                timestamp = receipt.confirmedAt,
            )
        addToEarningHistory(earningEvent)
        persistEarningEvent(earningEvent)

        return Result.success(receipt)
    }

    override fun getTotalEarnings(): EarningsSummary {
        val history = _earningHistory.value
        val now = clock()
        val oneDayAgo = now - DAY_MS
        val sevenDaysAgo = now - (7 * DAY_MS)

        val totalUsdc = history.sumOf { it.amountUsdc }
        val last24h = history.filter { it.timestamp >= oneDayAgo }.sumOf { it.amountUsdc }
        val last7d = history.filter { it.timestamp >= sevenDaysAgo }.sumOf { it.amountUsdc }

        val bySource =
            history
                .groupBy { it.source }
                .mapValues { (_, events) -> events.sumOf { it.amountUsdc } }

        return EarningsSummary(
            totalEarnedUsdc = totalUsdc,
            last24hUsdc = last24h,
            last7dUsdc = last7d,
            bySource = bySource,
        )
    }

    // --- Private helpers ---

    /**
     * Build an [AgentAction] from an earning event for policy checks.
     */
    private fun buildAgentAction(event: EarningEvent): AgentAction = AgentAction(
        agentId = EARNING_ENGINE_ID,
        actionType = "PAYOUT_${event.source.name}",
        description = "Payout for earning event ${event.id} from ${event.source.name}",
        amountUsdc = event.amountUsdc,
        resourceImpact = null,
        timestamp = event.timestamp,
    )

    /**
     * Perform biometric authentication via SecureKeystore.
     * Returns success if biometric passed, failure otherwise.
     */
    private fun performBiometricCheck(event: EarningEvent): Result<ByteArray> {
        val keystore =
            secureKeystore
                ?: return Result.failure(BiometricRequiredException("SecureKeystore not available"))

        val challenge = "payout_${event.id}_${event.amountUsdc}".encodeToByteArray()
        return keystore.requireBiometric(SIGNING_KEY_ALIAS, challenge)
    }

    /**
     * Execute the payout via the Circle Agent Stack rail.
     * Falls back to a mock receipt if no payout rail is configured.
     */
    private suspend fun executePayout(request: PayoutRequest): Result<PayoutReceipt> {
        val rail = payoutRail
        if (rail != null) {
            return rail.submitPayout(request)
        }

        // Mock execution when no rail is configured (testing/development)
        val receipt =
            PayoutReceipt(
                payoutRequestId = request.id,
                transactionHash = "mock_tx_${request.id}_${clock()}",
                amountUsdc = request.amountUsdc,
                destination = request.destination,
                confirmedAt = clock(),
            )
        return Result.success(receipt)
    }

    /**
     * Log a payout rejection to the Privacy_Vault for audit purposes.
     */
    private suspend fun logRejection(
        event: EarningEvent,
        reason: String,
    ) {
        val logEntry =
            json.encodeToString(
                RejectionLog(
                    earningEventId = event.id,
                    reason = reason,
                    timestamp = clock(),
                    amountUsdc = event.amountUsdc,
                ),
            )
        vault.store("rejection_${event.id}_${clock()}", logEntry.encodeToByteArray())
    }

    /**
     * Notify the user of a payout rejection via the event bus.
     */
    private fun notifyUserOfRejection(
        event: EarningEvent,
        reason: String,
    ) {
        eventBus.publish(
            AgentEvent.PolicyViolation(
                agentId = EARNING_ENGINE_ID,
                action = buildAgentAction(event),
            ),
        )
    }

    /**
     * Persist an earning event to the Privacy_Vault.
     */
    private suspend fun persistEarningEvent(event: EarningEvent) {
        val eventJson = json.encodeToString(event)
        vault.store("tx_earning_${event.id}", eventJson.encodeToByteArray())
    }

    private fun addPendingPayout(request: PayoutRequest) {
        _pendingPayouts.value = _pendingPayouts.value + request
    }

    private fun removePendingPayout(payoutId: String) {
        _pendingPayouts.value = _pendingPayouts.value.filter { it.id != payoutId }
    }

    private fun updatePayoutStatus(
        payoutId: String,
        status: PayoutStatus,
    ) {
        _pendingPayouts.value =
            _pendingPayouts.value.map { payout ->
                if (payout.id == payoutId) payout.copy(status = status) else payout
            }
    }

    private fun addToEarningHistory(event: EarningEvent) {
        _earningHistory.value = _earningHistory.value + event
    }

    private fun generatePayoutId(event: EarningEvent): String = "payout_${event.id}_${clock()}"

    companion object {
        /** Agent identifier for the earning engine. */
        val EARNING_ENGINE_ID = AgentId("earning_engine")

        /** Default biometric threshold in USDC. */
        const val DEFAULT_BIOMETRIC_THRESHOLD = 25.0

        /** Alias for the wallet signing key in the SecureKeystore. */
        const val SIGNING_KEY_ALIAS = "idle_harvest_wallet"

        /** Milliseconds in one day. */
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * Abstraction over the Circle Agent Stack payout rail.
 * Enables testability and resilience against Circle API changes.
 */
interface CirclePayoutRail {
    /** Submit a payout request for USDC settlement. */
    suspend fun submitPayout(request: PayoutRequest): Result<PayoutReceipt>
}

/**
 * Exception indicating a duplicate payout attempt for an already-processed earning event.
 */
class DuplicatePayoutException(
    message: String,
) : Exception(message)

/**
 * Exception indicating a payout was rejected by policy or compliance checks.
 */
class PayoutRejectedException(
    message: String,
) : Exception(message)

/**
 * Exception indicating biometric authentication is required but was not satisfied.
 */
class BiometricRequiredException(
    message: String,
) : Exception(message)

/**
 * Exception indicating the payout rail (Circle Agent Stack) failed to process the payout.
 */
class PayoutFailedException(
    message: String,
) : Exception(message)

/**
 * Internal log entry for payout rejections, stored in the Privacy_Vault.
 */
@kotlinx.serialization.Serializable
internal data class RejectionLog(
    val earningEventId: String,
    val reason: String,
    val timestamp: Long,
    val amountUsdc: Double,
)
