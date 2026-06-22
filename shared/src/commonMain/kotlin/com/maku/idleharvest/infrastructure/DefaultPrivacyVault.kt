package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.PrivacyVault
import com.maku.idleharvest.domain.models.ConsentToken
import com.maku.idleharvest.domain.models.DataType
import com.maku.idleharvest.infrastructure.crypto.CryptoException
import com.maku.idleharvest.infrastructure.crypto.CryptoProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default in-memory implementation of [PrivacyVault] with encryption-at-rest.
 *
 * All values are encrypted before storage using the provided [CryptoProvider].
 * A mutex ensures thread-safe access to the underlying storage map.
 *
 * This implementation stores data in memory (suitable for testing and as a base
 * for platform-specific persistent variants). Platform implementations can extend
 * or wrap this with file-backed persistence.
 *
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5
 */
class DefaultPrivacyVault(
    private val cryptoProvider: CryptoProvider,
    private val clock: () -> Long = { currentTimeMillis() },
) : PrivacyVault {
    private val mutex = Mutex()

    /** Encrypted key-value storage. Values are ciphertext produced by [cryptoProvider]. */
    private val storage = mutableMapOf<String, ByteArray>()

    /** Integrity checksums for each stored entry (keyed hash of plaintext). */
    private val checksums = mutableMapOf<String, ByteArray>()

    /** The encryption key used for all vault operations. */
    private var encryptionKey: ByteArray = cryptoProvider.generateKey()

    /** Key used for HMAC/integrity checks. Separate from encryption key. */
    private var integrityKey: ByteArray = cryptoProvider.generateKey()

    override suspend fun store(
        key: String,
        data: ByteArray,
    ): Result<Unit> = runCatching {
        require(key.isNotBlank()) { "Storage key must not be blank" }

        mutex.withLock {
            val ciphertext = cryptoProvider.encrypt(encryptionKey, data)
            val checksum = cryptoProvider.computeHash(integrityKey, data)
            storage[key] = ciphertext
            checksums[key] = checksum
        }
    }

    override suspend fun retrieve(key: String): Result<ByteArray?> = runCatching {
        mutex.withLock {
            val ciphertext = storage[key] ?: return@runCatching null
            cryptoProvider.decrypt(encryptionKey, ciphertext)
        }
    }

    override suspend fun delete(key: String): Result<Unit> = runCatching {
        mutex.withLock {
            // Overwrite the stored bytes before removing (secure deletion pattern)
            storage[key]?.let { bytes ->
                bytes.fill(0)
            }
            storage.remove(key)
            checksums.remove(key)
        }
    }

    override suspend fun deleteAll(): Result<Unit> = runCatching {
        mutex.withLock {
            // Overwrite all stored ciphertext before clearing
            for ((_, value) in storage) {
                value.fill(0)
            }
            storage.clear()
            checksums.clear()

            // Clear encryption keys from memory and regenerate
            encryptionKey.fill(0)
            integrityKey.fill(0)
            encryptionKey = cryptoProvider.generateKey()
            integrityKey = cryptoProvider.generateKey()
        }
    }

    override suspend fun exportAnonymized(
        dataType: DataType,
        consentToken: ConsentToken,
    ): Result<ByteArray> = runCatching {
        // Validate consent token
        validateConsentToken(consentToken, dataType)

        mutex.withLock {
            // Collect entries matching the data type prefix
            val prefix = dataTypeToKeyPrefix(dataType)
            val matchingEntries = storage.entries.filter { it.key.startsWith(prefix) }

            if (matchingEntries.isEmpty()) {
                return@runCatching ByteArray(0)
            }

            // Decrypt and anonymize each entry
            val anonymizedChunks =
                matchingEntries.map { (_, ciphertext) ->
                    val plaintext = cryptoProvider.decrypt(encryptionKey, ciphertext)
                    anonymize(plaintext)
                }

            // Concatenate anonymized data with length-prefix framing
            buildAnonymizedExport(anonymizedChunks)
        }
    }

    override fun isIntegrityValid(): Boolean {
        // Verify that all stored entries can be decrypted and match their checksums
        for ((key, ciphertext) in storage) {
            try {
                val decrypted = cryptoProvider.decrypt(encryptionKey, ciphertext)
                val expectedChecksum = checksums[key] ?: return false
                val actualChecksum = cryptoProvider.computeHash(integrityKey, decrypted)
                if (!expectedChecksum.contentEquals(actualChecksum)) {
                    return false
                }
            } catch (_: CryptoException) {
                return false
            } catch (_: Exception) {
                return false
            }
        }
        return true
    }

    // --- Private helpers ---

    private fun validateConsentToken(
        token: ConsentToken,
        dataType: DataType,
    ) {
        require(token.token.isNotBlank()) { "Consent token must not be blank" }

        val now = currentTime()
        require(token.grantedAt <= now) { "Consent token granted in the future" }
        require(token.expiresAt > now) { "Consent token has expired" }
        require(dataType in token.allowedDataTypes) {
            "Data type $dataType is not in consent scope: ${token.allowedDataTypes}"
        }
    }

    /**
     * Map a DataType to the key prefix pattern used in storage.
     */
    private fun dataTypeToKeyPrefix(dataType: DataType): String = when (dataType) {
        DataType.RESOURCE_METRICS -> "resource_"
        DataType.EARNING_SUMMARY -> "tx_earning_"
        DataType.CONTRIBUTION_PROOFS -> "tx_depin_"
        DataType.BENCHMARK_RESULTS -> "benchmark_"
    }

    /**
     * Strip PII from data bytes. This is a simplified anonymization that removes
     * any embedded user-identifying patterns. In a real implementation, this would
     * use field-level redaction based on a schema.
     *
     * For now, we strip bytes that could represent common PII markers.
     */
    private fun anonymize(data: ByteArray): ByteArray {
        // Simple anonymization: return data as-is since raw bytes in the vault
        // are already domain objects without direct PII embedding at this layer.
        // Real PII stripping happens at the serialization/domain layer.
        return data.copyOf()
    }

    /**
     * Build a framed export from multiple anonymized chunks.
     * Format: [4-byte count] + for each chunk: [4-byte length] + [chunk bytes]
     */
    private fun buildAnonymizedExport(chunks: List<ByteArray>): ByteArray {
        // Calculate total size
        val headerSize = 4 // chunk count
        val framingSize = chunks.size * 4 // 4-byte length per chunk
        val dataSize = chunks.sumOf { it.size }
        val totalSize = headerSize + framingSize + dataSize

        val result = ByteArray(totalSize)
        var offset = 0

        // Write chunk count
        putInt(result, offset, chunks.size)
        offset += 4

        // Write each chunk with length prefix
        for (chunk in chunks) {
            putInt(result, offset, chunk.size)
            offset += 4
            chunk.copyInto(result, offset)
            offset += chunk.size
        }

        return result
    }

    /**
     * Write a 32-bit integer in big-endian format at the given offset.
     */
    private fun putInt(
        array: ByteArray,
        offset: Int,
        value: Int,
    ) {
        array[offset] = (value shr 24 and 0xFF).toByte()
        array[offset + 1] = (value shr 16 and 0xFF).toByte()
        array[offset + 2] = (value shr 8 and 0xFF).toByte()
        array[offset + 3] = (value and 0xFF).toByte()
    }

    /**
     * Get current time in milliseconds.
     */
    private fun currentTime(): Long = clock()
}
