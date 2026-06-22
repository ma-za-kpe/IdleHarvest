package com.maku.idleharvest.infrastructure.crypto

import kotlin.random.Random

/**
 * A simple reversible crypto provider for commonMain testing.
 *
 * This implementation uses XOR-based encryption with a nonce, which is NOT cryptographically
 * secure but demonstrates the encrypt/decrypt pattern and is fully reversible.
 * Real AES-256-GCM implementations live in androidMain/iosMain.
 *
 * Encryption format: [4-byte nonce] + [XOR-encrypted data]
 * The nonce is mixed with the key to produce a per-message keystream.
 */
class SimpleCryptoProvider(
    private val random: Random = Random.Default,
) : CryptoProvider {

    companion object {
        private const val NONCE_SIZE = 4
        private const val KEY_SIZE = 32 // 256 bits
    }

    override fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes, got ${key.size}" }

        // Generate a random nonce for this encryption
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }

        // Derive a keystream from key + nonce
        val keystream = deriveKeystream(key, nonce, plaintext.size)

        // XOR plaintext with keystream
        val ciphertext = ByteArray(plaintext.size) { i ->
            (plaintext[i].toInt() xor keystream[i].toInt()).toByte()
        }

        // Prepend nonce to ciphertext
        return nonce + ciphertext
    }

    override fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes, got ${key.size}" }

        if (ciphertext.size < NONCE_SIZE) {
            throw CryptoException("Ciphertext too short: must be at least $NONCE_SIZE bytes")
        }

        // Extract nonce and encrypted data
        val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
        val encryptedData = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size)

        // Derive the same keystream
        val keystream = deriveKeystream(key, nonce, encryptedData.size)

        // XOR to recover plaintext
        return ByteArray(encryptedData.size) { i ->
            (encryptedData[i].toInt() xor keystream[i].toInt()).toByte()
        }
    }

    override fun generateKey(): ByteArray {
        return ByteArray(KEY_SIZE).also { random.nextBytes(it) }
    }

    override fun computeHash(key: ByteArray, data: ByteArray): ByteArray {
        // Simple keyed hash: iterative XOR mixing (not cryptographically strong, but deterministic
        // and suitable for testing the integrity check pattern).
        val hashSize = KEY_SIZE
        val hash = ByteArray(hashSize)

        // Initialize with key
        for (i in 0 until hashSize) {
            hash[i] = key[i % key.size]
        }

        // Mix in data bytes
        for (i in data.indices) {
            val idx = i % hashSize
            hash[idx] = (hash[idx].toInt() xor data[i].toInt()).toByte()
            // Rotate to add diffusion
            val nextIdx = (idx + 1) % hashSize
            hash[nextIdx] = (hash[nextIdx].toInt() xor (hash[idx].toInt() + i)).toByte()
        }

        // Final mixing pass
        for (i in 0 until hashSize) {
            val prev = hash[(i + hashSize - 1) % hashSize].toInt() and 0xFF
            hash[i] = (hash[i].toInt() xor (prev * 31 + i)).toByte()
        }

        return hash
    }

    /**
     * Derive a deterministic keystream from key + nonce.
     * Uses iterative mixing to produce [length] bytes.
     */
    private fun deriveKeystream(key: ByteArray, nonce: ByteArray, length: Int): ByteArray {
        val stream = ByteArray(length)
        // Seed state from key and nonce
        var state = 0
        for (b in key) state = state * 31 + b.toInt()
        for (b in nonce) state = state * 37 + b.toInt()

        for (i in 0 until length) {
            // Simple PRNG-like expansion
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            stream[i] = (state and 0xFF).toByte()
        }
        return stream
    }
}
