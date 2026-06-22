package com.maku.idleharvest.infrastructure.crypto

/**
 * Abstraction for cryptographic operations.
 *
 * Production code should obtain an instance via [createPlatformCryptoProvider], which
 * returns a real AES-256-GCM + HMAC-SHA256 implementation backed by the platform's
 * native crypto library (Android: javax.crypto; iOS: CryptoKit/CommonCrypto; Web:
 * SubtleCrypto). The XOR-based [SimpleCryptoProvider] in commonMain is for tests only
 * and is NOT cryptographically secure.
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

/**
 * Create the platform-backed [CryptoProvider] for production use.
 *
 * Each platform provides a real AES-256-GCM (with a random 12-byte IV prepended to the
 * ciphertext) and HMAC-SHA256 implementation. Callers must supply 32-byte keys.
 */
expect fun createPlatformCryptoProvider(): CryptoProvider
