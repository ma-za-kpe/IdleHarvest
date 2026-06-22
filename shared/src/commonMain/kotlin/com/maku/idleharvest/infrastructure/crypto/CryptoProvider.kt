package com.maku.idleharvest.infrastructure.crypto

/**
 * Abstraction for cryptographic operations.
 * Platform-specific implementations (androidMain/iosMain) provide real AES-256-GCM.
 * A simple reversible implementation is provided in commonMain for testing.
 */
interface CryptoProvider {
    /**
     * Encrypt plaintext bytes using the provided key.
     * @param key The encryption key (32 bytes for AES-256).
     * @param plaintext The data to encrypt.
     * @return The ciphertext bytes (may include IV/nonce prepended).
     */
    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
    ): ByteArray

    /**
     * Decrypt ciphertext bytes using the provided key.
     * @param key The decryption key (must match the encryption key).
     * @param ciphertext The encrypted data (may include IV/nonce prepended).
     * @return The original plaintext bytes.
     * @throws CryptoException if decryption fails (tampered data, wrong key, etc.)
     */
    fun decrypt(
        key: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray

    /**
     * Generate a new random encryption key suitable for AES-256 (32 bytes).
     */
    fun generateKey(): ByteArray

    /**
     * Compute a keyed hash (HMAC-like) for integrity verification.
     * @param key The HMAC key.
     * @param data The data to hash.
     * @return The hash bytes.
     */
    fun computeHash(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray
}

/**
 * Exception thrown when a crypto operation fails.
 */
class CryptoException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
