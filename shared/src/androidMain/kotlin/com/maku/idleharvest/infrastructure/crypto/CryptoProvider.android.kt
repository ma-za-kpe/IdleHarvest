package com.maku.idleharvest.infrastructure.crypto

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

actual fun createPlatformCryptoProvider(): CryptoProvider = AndroidCryptoProvider()

private class AndroidCryptoProvider : CryptoProvider {

    override fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = Random.Default.nextBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    override fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        if (ciphertext.size < 12) throw CryptoException("Ciphertext too short")
        val iv = ciphertext.copyOfRange(0, 12)
        val data = ciphertext.copyOfRange(12, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(data)
        } catch (e: Exception) {
            throw CryptoException("Decryption failed", e)
        }
    }

    override fun generateKey(): ByteArray {
        val gen = KeyGenerator.getInstance("AES")
        gen.init(256)
        return gen.generateKey().encoded
    }

    override fun computeHash(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
