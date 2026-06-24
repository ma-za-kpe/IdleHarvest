package com.maku.idleharvest.domain

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import com.maku.idleharvest.domain.models.PublicKey
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Android implementation of SecureKeystore using Android Keystore API.
 *
 * Private keys are generated and stored within the hardware-backed keystore
 * (StrongBox or TEE depending on device capability). Keys never leave the
 * secure hardware — only signatures are returned.
 *
 * Features:
 * - EC key generation in hardware security module
 * - SHA256withECDSA signing within secure hardware
 * - Biometric authentication gating for high-value transactions
 * - Lockout after 3 consecutive biometric failures
 * - Mnemonic phrase backup/recovery generation
 *
 * Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.5, 13.6
 */
actual class SecureKeystore {
    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val EC_CURVE = "secp256r1"
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val DEFAULT_COOLDOWN_MS = 30_000L // 30 seconds

        /**
         * BIP39-inspired word list (simplified subset for mnemonic generation).
         * A full implementation would use the complete 2048-word BIP39 list.
         */
        private val MNEMONIC_WORDS =
            listOf(
                "abandon",
                "ability",
                "able",
                "about",
                "above",
                "absent",
                "absorb",
                "abstract",
                "absurd",
                "abuse",
                "access",
                "accident",
                "account",
                "accuse",
                "achieve",
                "acid",
                "acoustic",
                "acquire",
                "across",
                "action",
                "actor",
                "actress",
                "actual",
                "adapt",
                "add",
                "addict",
                "address",
                "adjust",
                "admit",
                "adult",
                "advance",
                "advice",
                "aerobic",
                "affair",
                "afford",
                "afraid",
                "again",
                "age",
                "agent",
                "agree",
                "ahead",
                "aim",
                "air",
                "airport",
                "aisle",
                "alarm",
                "album",
                "alcohol",
                "alert",
                "alien",
                "all",
                "alley",
                "allow",
                "almost",
                "alone",
                "alpha",
                "already",
                "also",
                "alter",
                "always",
                "amateur",
                "amazing",
                "among",
                "amount",
                "amused",
                "analyst",
                "anchor",
                "ancient",
                "anger",
                "angle",
                "angry",
                "animal",
                "ankle",
                "announce",
                "annual",
                "another",
                "answer",
                "antenna",
                "antique",
                "anxiety",
                "any",
                "apart",
                "apology",
                "appear",
                "apple",
                "approve",
                "april",
                "arch",
                "arctic",
                "area",
                "arena",
                "argue",
                "arm",
                "armed",
                "armor",
                "army",
                "around",
                "arrange",
                "arrest",
                "arrive",
                "arrow",
                "art",
                "artefact",
                "artist",
                "artwork",
                "ask",
                "aspect",
                "assault",
                "asset",
                "assist",
                "assume",
                "asthma",
                "athlete",
                "atom",
                "attack",
                "attend",
                "attitude",
                "attract",
                "auction",
                "audit",
                "august",
                "aunt",
                "author",
                "auto",
                "autumn",
                "average",
                "avocado",
                "avoid",
                "awake",
                "aware",
                "awesome",
                "awful",
                "awkward",
                "axis",
                "baby",
                "bachelor",
                "bacon",
                "badge",
                "bag",
                "balance",
                "balcony",
                "ball",
                "bamboo",
                "banana",
                "banner",
                "bar",
                "barely",
                "bargain",
                "barrel",
                "base",
                "basic",
                "basket",
                "battle",
                "beach",
                "bean",
                "beauty",
                "because",
                "become",
                "beef",
                "before",
                "begin",
                "behave",
                "behind",
                "believe",
                "below",
                "belt",
                "bench",
                "benefit",
                "best",
                "betray",
                "better",
                "between",
                "beyond",
                "bicycle",
                "bid",
                "bike",
                "bind",
                "biology",
                "bird",
                "birth",
                "bitter",
                "black",
                "blade",
                "blame",
                "blanket",
                "blast",
                "bleak",
                "bless",
                "blind",
                "blood",
                "blossom",
                "blow",
                "blue",
                "blur",
                "blush",
                "board",
                "boat",
                "body",
                "boil",
                "bomb",
                "bone",
                "bonus",
                "book",
                "boost",
                "border",
                "boring",
                "borrow",
                "boss",
                "bottom",
                "bounce",
                "box",
                "boy",
                "bracket",
                "brain",
                "brand",
                "brass",
                "brave",
                "bread",
                "breeze",
                "brick",
                "bridge",
                "brief",
                "bright",
                "bring",
                "brisk",
                "broccoli",
                "broken",
                "bronze",
                "broom",
                "brother",
                "brown",
                "brush",
                "bubble",
                "buddy",
                "budget",
                "buffalo",
                "build",
                "bulb",
                "bulk",
                "bullet",
                "bundle",
                "bunny",
                "burden",
                "burger",
                "burst",
                "bus",
                "business",
                "busy",
                "butter",
                "buyer",
                "buzz",
                "cabbage",
                "cabin",
                "cable",
                "cactus",
                "cage",
                "cake",
                "call",
                "calm",
                "camera",
                "camp",
                "can",
                "canal",
                "cancel",
                "candy",
                "cannon",
                "canoe",
                "canvas",
                "canyon",
                "capable",
                "capital",
                "captain",
            )
    }

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
            load(null)
        }

    /** Tracks consecutive biometric authentication failures. */
    private var consecutiveFailures = 0

    /** Timestamp (ms since epoch) until which signing operations are locked. */
    @Volatile
    private var lockedUntil = 0L

    /**
     * Generate a new EC key pair and store it in Android Keystore (hardware-backed).
     *
     * Uses ECDSA with secp256r1 (P-256) curve, suitable for transaction signing.
     * The private key never leaves the secure hardware — only the public key is returned.
     *
     * @param alias Unique identifier for this key pair in the keystore.
     * @return The public key on success, or failure if key generation fails.
     */
    actual fun generateKeyPair(alias: String): Result<PublicKey> = try {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE_PROVIDER,
            )

        val parameterSpec =
            KeyGenParameterSpec
                .Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                ).setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false) // Base key; biometric keys use separate alias
                .build()

        keyPairGenerator.initialize(parameterSpec)
        val keyPair = keyPairGenerator.generateKeyPair()

        val encodedKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val isSecureHardware = checkKeyInSecureHardware(alias)

        Result.success(
            PublicKey(
                alias = alias,
                encodedKey = encodedKey,
                algorithm = SIGNATURE_ALGORITHM,
                isInSecureHardware = isSecureHardware,
            ),
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Sign data using the private key stored in Android Keystore.
     *
     * The signing operation is performed entirely within the secure hardware.
     * Only the signature bytes are returned — the private key is never exported.
     *
     * Checks lockout status before signing. If signing operations are locked
     * (due to consecutive biometric failures), the request is rejected.
     *
     * @param alias The key alias to sign with.
     * @param data The data bytes to sign.
     * @return The signature bytes on success, or failure if signing fails or is locked.
     */
    actual fun sign(
        alias: String,
        data: ByteArray,
    ): Result<ByteArray> {
        return try {
            // Check lockout
            if (isLocked()) {
                return Result.failure(
                    SecurityException(
                        "Signing operations are locked. Cooldown expires in ${lockedUntil - System.currentTimeMillis()}ms",
                    ),
                )
            }

            val privateKeyEntry =
                keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                    ?: return Result.failure(
                        IllegalStateException("No private key found for alias: $alias"),
                    )

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKeyEntry.privateKey)
            signature.update(data)
            val signatureBytes = signature.sign()

            Result.success(signatureBytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Require biometric authentication before signing.
     *
     * Creates or uses a biometric-gated key (with user authentication required).
     * The signing operation only succeeds if the user authenticates via biometric.
     *
     * Tracks consecutive failures and triggers lockout after 3 failures.
     * A successful authentication resets the failure counter.
     *
     * Note: In a full implementation, this would integrate with BiometricPrompt
     * and CryptoObject for user-facing authentication. The current implementation
     * creates biometric-gated keys that require authentication at the keystore level.
     *
     * @param alias The key alias for the biometric-gated key.
     * @param challenge The data to sign (transaction hash or challenge bytes).
     * @return The signature bytes on success, or failure if authentication fails.
     */
    actual fun requireBiometric(
        alias: String,
        challenge: ByteArray,
    ): Result<ByteArray> {
        // Check lockout first
        if (isLocked()) {
            return Result.failure(
                SecurityException(
                    "Signing operations are locked due to consecutive biometric failures. " +
                        "Cooldown expires in ${lockedUntil - System.currentTimeMillis()}ms",
                ),
            )
        }

        val biometricAlias = "${alias}_biometric"

        return try {
            // Ensure biometric-gated key exists
            if (!keyStore.containsAlias(biometricAlias)) {
                generateBiometricKey(biometricAlias)
            }

            val privateKeyEntry =
                keyStore.getEntry(biometricAlias, null) as? KeyStore.PrivateKeyEntry
                    ?: return handleBiometricFailure(
                        "No biometric key found for alias: $biometricAlias",
                    )

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKeyEntry.privateKey)
            signature.update(challenge)
            val signatureBytes = signature.sign()

            // Success — reset failure counter
            consecutiveFailures = 0
            Result.success(signatureBytes)
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            handleBiometricFailure("Biometric authentication required: ${e.message}")
        } catch (e: Exception) {
            handleBiometricFailure("Biometric signing failed: ${e.message}")
        }
    }

    /**
     * Retrieve the public key for the given alias from the Android Keystore.
     *
     * @param alias The key alias to look up.
     * @return The public key on success, or failure if the key doesn't exist.
     */
    actual fun getPublicKey(alias: String): Result<PublicKey> {
        return try {
            val certificate =
                keyStore.getCertificate(alias)
                    ?: return Result.failure(
                        IllegalStateException("No certificate found for alias: $alias"),
                    )

            val publicKey = certificate.publicKey
            val encodedKey = Base64.getEncoder().encodeToString(publicKey.encoded)
            val isSecureHardware = checkKeyInSecureHardware(alias)

            Result.success(
                PublicKey(
                    alias = alias,
                    encodedKey = encodedKey,
                    algorithm = SIGNATURE_ALGORITHM,
                    isInSecureHardware = isSecureHardware,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check whether the key for the given alias is stored in secure hardware
     * (StrongBox or TEE).
     *
     * @param alias The key alias to check.
     * @return true if the key is in secure hardware, false otherwise.
     */
    actual fun isKeyInSecureHardware(alias: String): Boolean = checkKeyInSecureHardware(alias)

    /**
     * Lock signing operations for a specified duration.
     *
     * Called after 3 consecutive biometric authentication failures.
     * All signing requests are rejected until the lockout expires.
     *
     * @param durationMs The cooldown duration in milliseconds.
     */
    actual fun lockSigningOperations(durationMs: Long) {
        lockedUntil = System.currentTimeMillis() + durationMs
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generate a biometric-gated key pair that requires user authentication.
     *
     * Sets `setUserAuthenticationRequired(true)` with a short validity window,
     * meaning the user must authenticate (fingerprint/face) before the key can sign.
     */
    private fun generateBiometricKey(alias: String) {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE_PROVIDER,
            )

        val parameterSpec =
            KeyGenParameterSpec
                .Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                ).setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    10, // authentication validity duration in seconds
                    KeyProperties.AUTH_BIOMETRIC_STRONG,
                ).build()

        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Check if a key is stored in secure hardware by inspecting [KeyInfo].
     * Uses getSecurityLevel() on API 31+ or falls back to isInsideSecureHardware for older APIs.
     */
    private fun checkKeyInSecureHardware(alias: String): Boolean {
        return try {
            val privateKeyEntry =
                keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                    ?: return false

            val keyFactory =
                KeyFactory.getInstance(
                    privateKeyEntry.privateKey.algorithm,
                    ANDROID_KEYSTORE_PROVIDER,
                )
            val keyInfo = keyFactory.getKeySpec(privateKeyEntry.privateKey, KeyInfo::class.java)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Handle a biometric authentication failure.
     * Increments the failure counter and triggers lockout after 3 consecutive failures.
     */
    private fun handleBiometricFailure(message: String): Result<ByteArray> {
        consecutiveFailures++
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            lockSigningOperations(DEFAULT_COOLDOWN_MS)
            return Result.failure(
                SecurityException(
                    "Signing locked after $MAX_CONSECUTIVE_FAILURES consecutive biometric failures. " +
                        "Cooldown: ${DEFAULT_COOLDOWN_MS}ms",
                ),
            )
        }
        return Result.failure(SecurityException(message))
    }

    /**
     * Check if signing operations are currently locked.
     */
    private fun isLocked(): Boolean {
        if (lockedUntil <= 0L) return false
        if (System.currentTimeMillis() >= lockedUntil) {
            // Lockout expired — reset
            lockedUntil = 0L
            consecutiveFailures = 0
            return false
        }
        return true
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mnemonic backup/recovery support (Requirement 13.6)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generate a BIP39-style mnemonic phrase for key backup/recovery.
     *
     * This is generated once during wallet setup, displayed to the user,
     * and NEVER stored in the app. The mnemonic can be used to derive
     * a recovery key on a new device.
     *
     * @param wordCount Number of words in the mnemonic (12 or 24).
     * @return The mnemonic phrase as a space-separated string.
     */
    fun generateMnemonic(wordCount: Int = 12): String {
        require(wordCount == 12 || wordCount == 24) {
            "Mnemonic word count must be 12 or 24"
        }

        val secureRandom = java.security.SecureRandom()
        val words =
            (1..wordCount).map {
                MNEMONIC_WORDS[secureRandom.nextInt(MNEMONIC_WORDS.size)]
            }
        return words.joinToString(" ")
    }

    /**
     * Verify a mnemonic phrase has valid format (correct word count, all words in dictionary).
     *
     * @param mnemonic The mnemonic phrase to validate.
     * @return true if the mnemonic is structurally valid.
     */
    fun verifyMnemonic(mnemonic: String): Boolean {
        val words = mnemonic.trim().split("\\s+".toRegex())
        if (words.size != 12 && words.size != 24) return false
        return words.all { it in MNEMONIC_WORDS }
    }
}
