package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * A public key retrieved from the Secure_Keystore.
 * Encoded as a Base64 string for cross-platform serialization.
 */
@Serializable
data class PublicKey(
    val alias: String,
    val encodedKey: String,
    val algorithm: String,
    val isInSecureHardware: Boolean,
)
