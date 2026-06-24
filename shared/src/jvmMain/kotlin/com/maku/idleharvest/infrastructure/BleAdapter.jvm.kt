package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.PeerId

actual class BleAdapter {
    actual fun startScan(callback: (PeerId, Int, ByteArray) -> Unit) = Unit

    actual fun stopScan() = Unit

    actual suspend fun connect(peerId: PeerId): Result<BleConnection> = Result.failure(UnsupportedOperationException("BLE is not available on JVM backend"))

    actual fun disconnect(peerId: PeerId) = Unit

    actual fun advertise(data: ByteArray) = Unit

    actual fun stopAdvertise() = Unit
}
