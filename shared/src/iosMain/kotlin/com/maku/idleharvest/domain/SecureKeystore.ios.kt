package com.maku.idleharvest.domain

import com.maku.idleharvest.domain.models.PublicKey

/**
 * iOS SecureKeystore.
 *
 * IdleHarvest now delegates signing and identity to the user's connected wallet
 * (see domain/auth: WalletConnector + AuthManager) rather than managing its own keys.
 * On iOS this keystore is therefore intentionally inert and reports unavailability,
 * directing callers to the wallet-based signing path. (Android retains a real
 * hardware-backed keystore for its legacy on-device signing use case.)
 */
private const val WALLET_DELEGATED = "Signing is delegated to the connected wallet on iOS; SecureKeystore is unused."

actual class SecureKeystore {
    actual fun generateKeyPair(alias: String): Result<PublicKey> = Result.failure(UnsupportedOperationException(WALLET_DELEGATED))

    actual fun sign(
        alias: String,
        data: ByteArray,
    ): Result<ByteArray> = Result.failure(UnsupportedOperationException(WALLET_DELEGATED))

    actual fun requireBiometric(
        alias: String,
        challenge: ByteArray,
    ): Result<ByteArray> = Result.failure(UnsupportedOperationException(WALLET_DELEGATED))

    actual fun getPublicKey(alias: String): Result<PublicKey> = Result.failure(UnsupportedOperationException(WALLET_DELEGATED))

    actual fun isKeyInSecureHardware(alias: String): Boolean = false

    actual fun lockSigningOperations(durationMs: Long) = Unit
}
