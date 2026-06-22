package com.maku.idleharvest.infrastructure

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.maku.idleharvest.domain.models.PeerId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Android-specific BLE adapter implementation.
 *
 * Wraps Android's [BluetoothLeScanner], [BluetoothLeAdvertiser], and [BluetoothGattCallback]
 * for BLE scanning, advertising, and GATT connections.
 *
 * Requires runtime permissions (API 31+):
 * - `BLUETOOTH_SCAN`
 * - `BLUETOOTH_CONNECT`
 * - `BLUETOOTH_ADVERTISE`
 *
 * For API < 31, legacy `BLUETOOTH` and `BLUETOOTH_ADMIN` permissions are used.
 *
 * Validates: Requirements 4.1
 */
@SuppressLint("MissingPermission")
actual class BleAdapter(private val context: Context) {

    companion object {
        /**
         * IdleHarvest service UUID used for BLE scanning filters and advertising.
         * This UUID uniquely identifies IdleHarvest peers during BLE discovery.
         */
        val IDLE_HARVEST_SERVICE_UUID: UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

        /** Characteristic UUID for data exchange between peers. */
        val DATA_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter = bluetoothManager.adapter

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    /** Active scan callback reference for stopping scans. */
    private var activeScanCallback: ScanCallback? = null

    /** Active advertise callback reference for stopping advertising. */
    private var activeAdvertiseCallback: AdvertiseCallback? = null

    /** Active GATT connections keyed by peer address. */
    private val activeGattConnections = mutableMapOf<String, BluetoothGatt>()

    /**
     * Start scanning for nearby IdleHarvest peers using BLE.
     *
     * Uses a [ScanFilter] targeting the IdleHarvest service UUID to limit results
     * to relevant peers. The scan operates in low-latency mode for faster peer discovery.
     *
     * @param callback Invoked for each discovered peer with (peerId, signalStrength, advertisingData).
     */
    actual fun startScan(callback: (PeerId, Int, ByteArray) -> Unit) {
        val bleScanner = scanner ?: return

        // Stop any existing scan before starting a new one
        stopScan()

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(IDLE_HARVEST_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val peerId = PeerId(result.device.address)
                val rssi = result.rssi
                val data = result.scanRecord?.serviceData?.get(
                    ParcelUuid(IDLE_HARVEST_SERVICE_UUID)
                ) ?: result.scanRecord?.bytes ?: byteArrayOf()
                callback(peerId, rssi, data)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    val peerId = PeerId(result.device.address)
                    val rssi = result.rssi
                    val data = result.scanRecord?.serviceData?.get(
                        ParcelUuid(IDLE_HARVEST_SERVICE_UUID)
                    ) ?: result.scanRecord?.bytes ?: byteArrayOf()
                    callback(peerId, rssi, data)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                // Scan failed — log silently. The MeshCoordinator can retry via
                // its adaptive scan cycle.
            }
        }

        activeScanCallback = scanCallback
        bleScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    /**
     * Stop the ongoing BLE scan.
     */
    actual fun stopScan() {
        activeScanCallback?.let { callback ->
            scanner?.stopScan(callback)
            activeScanCallback = null
        }
    }

    /**
     * Connect to a peer via GATT.
     *
     * Uses [BluetoothDevice.connectGatt] with [BluetoothGattCallback] to establish
     * a connection. Returns a [BleConnection] wrapping the GATT instance on success.
     *
     * @param peerId The peer to connect to (device MAC address).
     * @return A [BleConnection] on success, or failure if connection cannot be established.
     */
    actual suspend fun connect(peerId: PeerId): Result<BleConnection> {
        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(peerId.value)
        } catch (e: IllegalArgumentException) {
            return Result.failure(
                IllegalArgumentException("Invalid Bluetooth address: ${peerId.value}", e)
            )
        }

        return suspendCancellableCoroutine { continuation ->
            var resumed = false

            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int,
                ) {
                    if (resumed) return

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            // Discover services after connection
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            resumed = true
                            gatt.close()
                            activeGattConnections.remove(peerId.value)
                            continuation.resume(
                                Result.failure(
                                    BleConnectionException(
                                        "Connection to ${peerId.value} failed or was disconnected (status=$status)"
                                    )
                                )
                            )
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (resumed) return
                    resumed = true

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        activeGattConnections[peerId.value] = gatt
                        val connection = AndroidBleConnection(
                            peerId = peerId,
                            gatt = gatt,
                            serviceUuid = IDLE_HARVEST_SERVICE_UUID,
                            characteristicUuid = DATA_CHARACTERISTIC_UUID,
                        )
                        continuation.resume(Result.success(connection))
                    } else {
                        gatt.close()
                        continuation.resume(
                            Result.failure(
                                BleConnectionException(
                                    "Service discovery failed for ${peerId.value} (status=$status)"
                                )
                            )
                        )
                    }
                }
            }

            val gatt = device.connectGatt(context, false, gattCallback)

            if (gatt == null) {
                resumed = true
                continuation.resume(
                    Result.failure(
                        BleConnectionException("Failed to initiate GATT connection to ${peerId.value}")
                    )
                )
            }

            continuation.invokeOnCancellation {
                if (!resumed) {
                    gatt?.close()
                    activeGattConnections.remove(peerId.value)
                }
            }
        }
    }

    /**
     * Disconnect from a specific peer and release GATT resources.
     */
    actual fun disconnect(peerId: PeerId) {
        activeGattConnections.remove(peerId.value)?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
    }

    /**
     * Start BLE advertising with the provided data payload.
     *
     * Advertises the IdleHarvest service UUID along with the given data bytes
     * (encoded resource profile, peer identity, etc.) so nearby peers can
     * discover and identify this device.
     *
     * @param data The advertising data payload.
     */
    actual fun advertise(data: ByteArray) {
        val bleAdvertiser = advertiser ?: return

        // Stop any existing advertising before starting a new one
        stopAdvertise()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(IDLE_HARVEST_SERVICE_UUID))
            .addServiceData(ParcelUuid(IDLE_HARVEST_SERVICE_UUID), truncateForAdvertising(data))
            .build()

        val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                // Advertising started successfully
            }

            override fun onStartFailure(errorCode: Int) {
                // Advertising failed — the MeshCoordinator will handle retries
                // via its periodic refresh mechanism.
                activeAdvertiseCallback = null
            }
        }

        activeAdvertiseCallback = advertiseCallback
        bleAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)
    }

    /**
     * Stop BLE advertising.
     */
    actual fun stopAdvertise() {
        activeAdvertiseCallback?.let { callback ->
            advertiser?.stopAdvertising(callback)
            activeAdvertiseCallback = null
        }
    }

    /**
     * Truncate data to fit within BLE advertising payload limits.
     * BLE 4.x advertising data is limited to ~27 bytes of service data.
     * BLE 5.x extended advertising allows more, but we target the minimum.
     */
    private fun truncateForAdvertising(data: ByteArray): ByteArray {
        val maxServiceDataLength = 24 // Safe limit for service data in advertising packet
        return if (data.size > maxServiceDataLength) {
            data.copyOf(maxServiceDataLength)
        } else {
            data
        }
    }
}

/**
 * Android BLE connection wrapping a [BluetoothGatt] instance.
 *
 * Provides send/receive operations for inter-device communication
 * over the IdleHarvest GATT service characteristic.
 */
@SuppressLint("MissingPermission")
class AndroidBleConnection(
    override val peerId: PeerId,
    private val gatt: BluetoothGatt,
    private val serviceUuid: UUID,
    private val characteristicUuid: UUID,
) : BleConnection {

    /** Channel for receiving data from the remote peer via notifications. */
    private val receiveChannel = Channel<ByteArray>(Channel.BUFFERED)

    /** Whether this connection has been closed. */
    @Volatile
    private var isClosed = false

    /**
     * Send encrypted data to the connected peer by writing to the
     * GATT characteristic.
     *
     * @param data The data bytes to send.
     * @return Success or failure result.
     */
    override suspend fun send(data: ByteArray): Result<Unit> {
        if (isClosed) {
            return Result.failure(IllegalStateException("Connection is closed"))
        }

        val service: BluetoothGattService = gatt.getService(serviceUuid)
            ?: return Result.failure(
                BleConnectionException("IdleHarvest GATT service not found on peer ${peerId.value}")
            )

        val characteristic: BluetoothGattCharacteristic =
            service.getCharacteristic(characteristicUuid)
                ?: return Result.failure(
                    BleConnectionException("Data characteristic not found on peer ${peerId.value}")
                )

        return try {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(
                    BleConnectionException("Failed to write characteristic to peer ${peerId.value}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Receive data from the connected peer.
     * Suspends until data is available via GATT characteristic notification.
     *
     * @return The received data bytes.
     */
    override suspend fun receive(): ByteArray {
        if (isClosed) {
            throw IllegalStateException("Connection is closed")
        }
        return receiveChannel.receive()
    }

    /**
     * Close this connection and release GATT resources.
     */
    override fun close() {
        if (isClosed) return
        isClosed = true
        receiveChannel.close()
        gatt.disconnect()
        gatt.close()
    }

    /**
     * Called by the GATT callback when a characteristic notification is received.
     * Enqueues the data into the receive channel.
     */
    internal fun onDataReceived(data: ByteArray) {
        if (!isClosed) {
            receiveChannel.trySend(data)
        }
    }
}

/**
 * Exception indicating a BLE connection failure.
 */
class BleConnectionException(message: String) : Exception(message)
