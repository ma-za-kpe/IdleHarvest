package com.maku.idleharvest.infrastructure.crypto

actual fun createPlatformCryptoProvider(): CryptoProvider = SimpleCryptoProvider()
