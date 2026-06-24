package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.AgentState
import com.maku.idleharvest.domain.models.AirtimeBundle
import com.maku.idleharvest.domain.models.AirtimeTransaction
import com.maku.idleharvest.domain.models.MonetizationAction
import com.maku.idleharvest.domain.models.MonetizationRecommendation
import com.maku.idleharvest.domain.models.TransactionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * AI agent that predicts airtime/data usage patterns and automates monetization.
 * Evaluates expiring bundles and executes sell/transfer actions via VTU platforms.
 *
 * Validates: Requirements 2.1, 2.2, 2.3
 */
interface AirtimeAgent {
    /** Current lifecycle state of the airtime agent. */
    val state: StateFlow<AgentState>

    /** Evaluate a bundle for monetization and produce a recommendation. */
    suspend fun evaluateBundle(bundle: AirtimeBundle): MonetizationRecommendation

    /** Execute a monetization action (sell/transfer) via the configured VTU platform. */
    suspend fun executeAction(action: MonetizationAction): TransactionResult

    /** Observe the full transaction history as a reactive stream. */
    fun getTransactionHistory(): Flow<List<AirtimeTransaction>>
}
