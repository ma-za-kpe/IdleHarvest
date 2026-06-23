package com.maku.idleharvest.infrastructure.crypto

import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider

actual fun createPlatformCryptoProvider(): CryptoProvider = SimpleCryptoProvider()
