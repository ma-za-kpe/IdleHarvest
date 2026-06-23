package com.maku.idleharvest.domain

import com.maku.idleharvest.domain.models.PublicKey

actual class SecureKeystore {
    actual fun generateKeyPair(alias: String): Result<PublicKey> = Result.failure(UnsupportedOperationException("SecureKeystore is not available on JVM backend"))

    actual fun sign(
        alias: String,
        data: ByteArray,
    ): Result<ByteArray> = Result.failure(UnsupportedOperationException("SecureKeystore is not available on JVM backend"))

    actual fun requireBiometric(
        alias: String,
        challenge: ByteArray,
    ): Result<ByteArray> = Result.failure(UnsupportedOperationException("SecureKeystore is not available on JVM backend"))

    actual fun getPublicKey(alias: String): Result<PublicKey> = Result.failure(UnsupportedOperationException("SecureKeystore is not available on JVM backend"))

    actual fun isKeyInSecureHardware(alias: String): Boolean = false

    actual fun lockSigningOperations(durationMs: Long) = Unit
}
