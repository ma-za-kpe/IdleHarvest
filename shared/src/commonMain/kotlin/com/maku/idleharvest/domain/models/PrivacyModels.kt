package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * Types of data that can be exported from the Privacy_Vault (after anonymization).
 */
@Serializable
enum class DataType {
    RESOURCE_METRICS,
    EARNING_SUMMARY,
    CONTRIBUTION_PROOFS,
    BENCHMARK_RESULTS,
}

/**
 * Token representing user consent for data export.
 * Must be valid and unexpired for any data to leave the device.
 */
@Serializable
data class ConsentToken(
    val token: String,
    val grantedAt: Long,
    val expiresAt: Long,
    val allowedDataTypes: List<DataType>,
)
