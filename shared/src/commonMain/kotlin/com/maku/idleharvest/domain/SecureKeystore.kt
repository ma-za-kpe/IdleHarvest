package com.maku.idleharvest.domain

import com.maku.idleharvest.domain.models.PublicKey

/**
 * Hardware-backed secure keystore for wallet key management.
 * Private signing keys are generated and stored within the device's hardware
 * security module (Android Keystore / iOS Secure Enclave) and never exported.
 *
 * Platform-specific implementations:
 * - Android: Uses Android Keystore API
 * - iOS: Uses Security framework + Secure Enclave
 *
 * Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.5
 */
expect class SecureKeystore {
    /** Generate a new key pair and store it in secure hardware. Returns the public key. */
    fun generateKeyPair(alias: String): Result<PublicKey>

    /** Sign data using the private key for the given alias. Returns the signature bytes. */
    fun sign(
        alias: String,
        data: ByteArray,
    ): Result<ByteArray>

    /**
     * Require biometric authentication before signing.
     * Used for high-value transactions above user-configured thresholds.
     */
    fun requireBiometric(
        alias: String,
        challenge: ByteArray,
    ): Result<ByteArray>

    /** Retrieve the public key for the given alias. */
    fun getPublicKey(alias: String): Result<PublicKey>

    /** Check whether the key for the given alias is stored in secure hardware. */
    fun isKeyInSecureHardware(alias: String): Boolean

    /**
     * Lock signing operations for a specified duration.
     * Triggered after 3 consecutive biometric authentication failures.
     */
    fun lockSigningOperations(durationMs: Long)
}
