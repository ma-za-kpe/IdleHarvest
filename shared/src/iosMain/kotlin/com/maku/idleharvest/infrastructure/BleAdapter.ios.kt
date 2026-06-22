package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.PeerId

/**
 * iOS-specific BLE adapter implementation.
 *
 * Wraps CoreBluetooth's CBCentralManager and CBPeripheralManager
 * for BLE scanning, advertising, and GATT connections.
 *
 * This is a stub implementation — real CoreBluetooth logic will be added
 * when iOS platform support is prioritized.
 *
 * Validates: Requirements 4.1
 */
actual class BleAdapter {

    actual fun startScan(callback: (PeerId, Int, ByteArray) -> Unit) {
        // TODO: Implement using CBCentralManager.scanForPeripherals with
        // IdleHarvest service UUID. Parse discovered peripherals and invoke callback.
    }

    actual fun stopScan() {
        // TODO: Implement using CBCentralManager.stopScan
    }

    actual suspend fun connect(peerId: PeerId): Result<BleConnection> {
        // TODO: Implement using CBCentralManager.connect with CBPeripheralDelegate.
        // Return a BleConnection wrapping the CBPeripheral instance.
        return Result.failure(NotImplementedError("iOS BLE connect not yet implemented"))
    }

    actual fun disconnect(peerId: PeerId) {
        // TODO: Implement using CBCentralManager.cancelPeripheralConnection
    }

    actual fun advertise(data: ByteArray) {
        // TODO: Implement using CBPeripheralManager.startAdvertising with
        // CBAdvertisementDataServiceUUIDsKey containing the payload.
    }

    actual fun stopAdvertise() {
        // TODO: Implement using CBPeripheralManager.stopAdvertising
    }
}
