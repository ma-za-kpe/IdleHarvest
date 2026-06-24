package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * A pending payout request awaiting settlement via Circle Agent Stack.
 */
@Serializable
data class PayoutRequest(
    val id: String,
    val earningEventId: String,
    val amountUsdc: Double,
    val destination: WalletAddress,
    val status: PayoutStatus,
    val requiresBiometric: Boolean,
)

/**
 * Receipt confirming a successful USDC payout.
 */
@Serializable
data class PayoutReceipt(
    val payoutRequestId: String,
    val transactionHash: String,
    val amountUsdc: Double,
    val destination: WalletAddress,
    val confirmedAt: Long,
)

/**
 * Request for an agent-to-agent nanopayment within a peer network.
 */
@Serializable
data class NanopaymentRequest(
    val fromAgent: AgentId,
    val toAgent: AgentId,
    val toPeer: PeerId,
    val amountUsdc: Double,
    val serviceDescription: String,
    val timestamp: Long,
)

/**
 * Receipt confirming a completed nanopayment.
 */
@Serializable
data class NanopaymentReceipt(
    val requestId: String,
    val transactionHash: String,
    val amountUsdc: Double,
    val confirmedAt: Long,
)
