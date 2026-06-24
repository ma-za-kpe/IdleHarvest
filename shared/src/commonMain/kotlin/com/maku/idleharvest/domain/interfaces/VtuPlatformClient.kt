package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.VtuPlatform

/**
 * Client interface for interacting with Virtual Top-Up (VTU) platform APIs.
 * Supports selling, transferring airtime/data, and checking balance.
 *
 * Validates: Requirements 2.2, 2.3
 */
interface VtuPlatformClient {
    /** The platform this client connects to. */
    val platform: VtuPlatform

    /** Sell airtime/data on the VTU marketplace. */
    suspend fun sell(request: SellRequest): Result<SellResponse>

    /** Transfer airtime/data to a recipient. */
    suspend fun transfer(request: TransferRequest): Result<TransferResponse>

    /** Check the current balance available on the platform. */
    suspend fun checkBalance(): Result<BalanceResponse>
}

/**
 * Request to sell airtime/data on a VTU platform.
 */
data class SellRequest(
    val bundleId: String,
    val amount: Long,
    val currency: String,
    val carrier: String,
)

/**
 * Response from a successful sell operation.
 */
data class SellResponse(
    val transactionId: String,
    val amountSettled: Long,
    val fee: Long,
    val timestamp: Long,
)

/**
 * Request to transfer airtime/data to a recipient.
 */
data class TransferRequest(
    val bundleId: String,
    val amount: Long,
    val recipient: String,
    val carrier: String,
)

/**
 * Response from a successful transfer operation.
 */
data class TransferResponse(
    val transactionId: String,
    val amountTransferred: Long,
    val recipient: String,
    val timestamp: Long,
)

/**
 * Balance response from a VTU platform.
 */
data class BalanceResponse(
    val available: Long,
    val currency: String,
    val timestamp: Long,
)
