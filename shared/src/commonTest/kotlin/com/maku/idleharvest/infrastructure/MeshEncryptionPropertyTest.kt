package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 11: Mesh Communication Encryption
 *
 * *For any* message transmitted between peers via BLE, the message payload SHALL be
 * encrypted using AES-GCM authenticated encryption. Decryption by an authenticated
 * peer SHALL recover the original message exactly.
 *
 * **Validates: Requirements 4.5**
 */
class MeshEncryptionPropertyTest {

    private val cryptoProvider = SimpleCryptoProvider()

    /**
     * For any plaintext message, encrypting and then decrypting with the same key
     * SHALL recover the original plaintext exactly.
     */
    @Test
    fun encryptDecryptRoundTrip() = runTest {
        val key = cryptoProvider.generateKey()

        forAll(Arb.byteArray(Arb.int(1..500), Arb.byte())) { plaintext ->
            val encrypted = cryptoProvider.encrypt(key, plaintext)
            val decrypted = cryptoProvider.decrypt(key, encrypted)
            decrypted.contentEquals(plaintext)
        }
    }

    /**
     * For any plaintext message, the encrypted output SHALL differ from the plaintext,
     * proving that encryption is actually applied to the data before transmission.
     */
    @Test
    fun encryptedDataDiffersFromPlaintext() = runTest {
        val key = cryptoProvider.generateKey()

        forAll(Arb.byteArray(Arb.int(1..500), Arb.byte())) { plaintext ->
            val encrypted = cryptoProvider.encrypt(key, plaintext)
            !encrypted.contentEquals(plaintext)
        }
    }
}
