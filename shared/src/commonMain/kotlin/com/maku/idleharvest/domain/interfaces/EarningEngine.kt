package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.EarningsSummary
import com.maku.idleharvest.domain.models.NanopaymentReceipt
import com.maku.idleharvest.domain.models.NanopaymentRequest
import com.maku.idleharvest.domain.models.PayoutReceipt
import com.maku.idleharvest.domain.models.PayoutRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages autonomous earning decisions, USDC payouts via Circle Agent Stack,
 * and agent-to-agent nanopayments within peer networks.
 *
 * Validates: Requirements 5.1, 5.2, 5.4, 5.5
 */
interface EarningEngine {
    /** Pending payout requests awaiting settlement. */
    val pendingPayouts: StateFlow<List<PayoutRequest>>

    /** Full earning history across all sources. */
    val earningHistory: StateFlow<List<EarningEvent>>

    /** Initiate a USDC payout for a completed earning event. */
    suspend fun initiatePayout(event: EarningEvent): Result<PayoutReceipt>

    /** Process an agent-to-agent nanopayment within the peer network. */
    suspend fun processNanopayment(payment: NanopaymentRequest): Result<NanopaymentReceipt>

    /** Get aggregated earnings summary across all time periods and sources. */
    fun getTotalEarnings(): EarningsSummary
}
