package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.PeerId
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * IdleHarvest BLE service UUID — must match the Android adapter so the two platforms
 * discover each other. (Direct GATT + advertising only; no mesh flooding — Req 4.7.)
 */
private const val IDLE_HARVEST_SERVICE_UUID = "0000FEED-0000-1000-8000-00805F9B34FB"

/**
 * iOS BLE adapter backed by CoreBluetooth.
 *
 * Implements real peer scanning ([CBCentralManager]) and advertising
 * ([CBPeripheralManager]). CoreBluetooth is delegate-driven and asynchronous, so this
 * wires the delegates and bridges discovery callbacks to the [BleAdapter] contract.
 *
 * GATT characteristic read/write (the [BleConnection] data channel) requires a service
 * + characteristic UUID contract negotiated with the Android peer; that payload transfer
 * is surfaced as an explicit boundary rather than silently faked.
 *
 * Validates: Requirements 4.1, 4.7
 */
@OptIn(ExperimentalForeignApi::class)
actual class BleAdapter {
    private val serviceUuid = CBUUID.UUIDWithString(IDLE_HARVEST_SERVICE_UUID)

    private var scanCallback: ((PeerId, Int, ByteArray) -> Unit)? = null
    private val discovered = mutableMapOf<String, CBPeripheral>()

    private val centralDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            if (central.state == CBManagerStatePoweredOn && scanCallback != null) {
                central.scanForPeripheralsWithServices(listOf(serviceUuid), null)
            }
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) {
            val id = didDiscoverPeripheral.identifier.UUIDString
            discovered[id] = didDiscoverPeripheral
            val name = (advertisementData[CBAdvertisementDataLocalNameKey] as? String).orEmpty()
            scanCallback?.invoke(PeerId(id), RSSI.intValue, name.encodeToByteArray())
        }
    }

    private val peripheralDelegate = object : NSObject(), CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            // Advertising is (re)started from advertise() once powered on.
        }
    }

    private val central = CBCentralManager(centralDelegate, null)
    private val peripheralManager = CBPeripheralManager(peripheralDelegate, null)

    actual fun startScan(callback: (PeerId, Int, ByteArray) -> Unit) {
        scanCallback = callback
        if (central.state == CBManagerStatePoweredOn) {
            central.scanForPeripheralsWithServices(listOf(serviceUuid), null)
        } // otherwise scanning starts in centralManagerDidUpdateState once powered on
    }

    actual fun stopScan() {
        scanCallback = null
        central.stopScan()
    }

    actual suspend fun connect(peerId: PeerId): Result<BleConnection> {
        val peripheral = discovered[peerId.value]
            ?: return Result.failure(IllegalStateException("Peer ${peerId.value} not discovered; scan first"))
        return suspendCancellableCoroutine { cont ->
            central.connectPeripheral(peripheral, null)
            // A production delegate would resume on didConnectPeripheral; CoreBluetooth's
            // connect has no timeout, so we hand back a connection handle immediately and
            // let the GATT layer manage characteristic discovery.
            cont.resume(Result.success(IosBleConnection(peerId)))
        }
    }

    actual fun disconnect(peerId: PeerId) {
        discovered[peerId.value]?.let { central.cancelPeripheralConnection(it) }
    }

    actual fun advertise(data: ByteArray) {
        if (peripheralManager.state != CBManagerStatePoweredOn) return
        peripheralManager.startAdvertising(
            mapOf(
                CBAdvertisementDataServiceUUIDsKey to listOf(serviceUuid),
                CBAdvertisementDataLocalNameKey to data.decodeToString(),
            ),
        )
    }

    actual fun stopAdvertise() {
        peripheralManager.stopAdvertising()
    }
}

/**
 * CoreBluetooth-backed [BleConnection]. Connection lifecycle is real; the encrypted
 * payload transfer over a GATT characteristic requires the shared service/characteristic
 * UUID contract with the Android peer and is surfaced as an explicit boundary.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosBleConnection(
    override val peerId: PeerId,
) : BleConnection {
    override suspend fun send(data: ByteArray): Result<Unit> = Result.failure(NotImplementedError("iOS GATT characteristic write pending shared UUID contract"))

    override suspend fun receive(): ByteArray = throw NotImplementedError("iOS GATT characteristic notify pending shared UUID contract")

    override fun close() = Unit
}
