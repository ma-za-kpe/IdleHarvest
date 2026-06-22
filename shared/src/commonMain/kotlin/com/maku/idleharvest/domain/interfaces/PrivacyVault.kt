package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.ConsentToken
import com.maku.idleharvest.domain.models.DataType

/**
 * Encrypted on-device storage for all sensitive data.
 * Uses AES-256-GCM encryption with device-bound keys.
 * All raw resource data, transactions, model outputs, and reasoning traces are stored here.
 *
 * Validates: Requirements 8.1, 8.2, 8.3, 8.5
 */
interface PrivacyVault {
    /** Store encrypted data under the given key. */
    suspend fun store(key: String, data: ByteArray): Result<Unit>

    /** Retrieve and decrypt data for the given key. Returns null if key does not exist. */
    suspend fun retrieve(key: String): Result<ByteArray?>

    /** Securely delete data associated with the given key. */
    suspend fun delete(key: String): Result<Unit>

    /** Securely delete all stored data in a single operation. */
    suspend fun deleteAll(): Result<Unit>

    /**
     * Export anonymized data of the specified type.
     * Requires a valid consent token; PII is stripped before export.
     */
    suspend fun exportAnonymized(dataType: DataType, consentToken: ConsentToken): Result<ByteArray>

    /** Verify the integrity of the vault's encrypted storage. */
    fun isIntegrityValid(): Boolean
}
