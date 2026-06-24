package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.PeerId

actual class BleAdapter {
    actual fun startScan(callback: (PeerId, Int, ByteArray) -> Unit) {}

    actual fun stopScan() {}

    actual suspend fun connect(peerId: PeerId): Result<BleConnection> = Result.failure(UnsupportedOperationException("BLE not available on Web"))

    actual fun disconnect(peerId: PeerId) {}

    actual fun advertise(data: ByteArray) {}

    actual fun stopAdvertise() {}
}
