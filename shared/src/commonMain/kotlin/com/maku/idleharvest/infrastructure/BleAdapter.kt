package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.PeerId

/**
 * Platform-specific BLE adapter abstraction.
 *
 * Each platform (Android, iOS) provides an actual implementation that wraps
 * native BLE scanning, advertising, and GATT connection APIs.
 *
 * - Android: BluetoothLeScanner, BluetoothGattServer, BluetoothGattCallback
 * - iOS: CBCentralManager, CBPeripheralManager
 *
 * Validates: Requirements 4.1, 4.7
 */
expect class BleAdapter {
    /**
     * Start scanning for nearby IdleHarvest peers.
     * @param callback Invoked for each discovered peer with (peerId, signalStrength, advertisingData).
     */
    fun startScan(callback: (PeerId, Int, ByteArray) -> Unit)

    /** Stop the ongoing BLE scan. */
    fun stopScan()

    /**
     * Connect to a peer via GATT.
     * @param peerId The peer to connect to.
     * @return A [BleConnection] on success, or failure if connection cannot be established.
     */
    suspend fun connect(peerId: PeerId): Result<BleConnection>

    /** Disconnect from a specific peer. */
    fun disconnect(peerId: PeerId)

    /**
     * Start BLE advertising with the provided data payload.
     * @param data The advertising data (encoded resource profile, peer identity, etc.)
     */
    fun advertise(data: ByteArray)

    /** Stop BLE advertising. */
    fun stopAdvertise()
}

/**
 * Represents an active BLE GATT connection to a peer device.
 * Provides send/receive operations for inter-device communication.
 */
interface BleConnection {
    /** The peer this connection is associated with. */
    val peerId: PeerId

    /**
     * Send encrypted data to the connected peer.
     * @param data The data bytes to send.
     * @return Success or failure result.
     */
    suspend fun send(data: ByteArray): Result<Unit>

    /**
     * Receive data from the connected peer.
     * Suspends until data is available.
     * @return The received data bytes.
     */
    suspend fun receive(): ByteArray

    /** Close this connection and release resources. */
    fun close()
}
