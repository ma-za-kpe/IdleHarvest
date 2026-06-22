package com.maku.idleharvest.domain

import com.maku.idleharvest.domain.models.PublicKey

/**
 * iOS implementation of SecureKeystore using Security framework and Secure Enclave.
 * Private keys are generated and stored within the Secure Enclave where available.
 *
 * TODO: Implement with SecKeyCreateRandomKey and kSecAttrTokenIDSecureEnclave
 */
actual class SecureKeystore {
    actual fun generateKeyPair(alias: String): Result<PublicKey> {
        // TODO: Implement using Security framework + Secure Enclave
        return Result.failure(NotImplementedError("iOS Keychain implementation pending"))
    }

    actual fun sign(
        alias: String,
        data: ByteArray,
    ): Result<ByteArray> {
        // TODO: Implement signing via SecKeyCreateSignature
        return Result.failure(NotImplementedError("iOS Keychain implementation pending"))
    }

    actual fun requireBiometric(
        alias: String,
        challenge: ByteArray,
    ): Result<ByteArray> {
        // TODO: Implement with LAContext + biometryType
        return Result.failure(NotImplementedError("iOS Keychain implementation pending"))
    }

    actual fun getPublicKey(alias: String): Result<PublicKey> {
        // TODO: Retrieve public key from Keychain
        return Result.failure(NotImplementedError("iOS Keychain implementation pending"))
    }

    actual fun isKeyInSecureHardware(alias: String): Boolean {
        // TODO: Check if Secure Enclave is available
        return false
    }

    actual fun lockSigningOperations(durationMs: Long) {
        // TODO: Implement cooldown timer for signing operations
    }
}
