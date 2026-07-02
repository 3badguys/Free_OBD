package com.freeobd.app.data.remote

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BLE GATT-based transport for OBD-II adapters that use Bluetooth Low Energy.
 *
 * Uses a custom serial-port-over-GATT pattern where data is exchanged through
 * dedicated TX (write) and RX (notify/indicate) characteristics.
 *
 * NOTE: This is a stub implementation. Most ELM327 adapters use classic SPP.
 * BLE OBD adapters are less common and may use vendor-specific GATT profiles.
 * This transport will be fully implemented when BLE adapter hardware is available for testing.
 */
class BleTransport(private val context: Context) : ObdTransport {

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    override var isConnected: Boolean = false
        private set

    override var connectedAddress: String? = null
        private set

    override val inputStream: InputStream
        get() = BleInputStream(rxCharacteristic)
            ?: throw IllegalStateException("Not connected — no BLE RX characteristic available")

    override val outputStream: OutputStream
        get() = BleOutputStream(txCharacteristic, bluetoothGatt)
            ?: throw IllegalStateException("Not connected — no BLE TX characteristic available")

    override suspend fun connect(device: BluetoothDeviceInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = manager?.adapter
                    ?: throw IllegalStateException("Bluetooth not available on this device")

                val bluetoothDevice: BluetoothDevice = adapter.getRemoteDevice(device.address)

                bluetoothGatt = withTimeout(GATT_TIMEOUT_MS) {
                    connectGatt(bluetoothDevice)
                }

                // Discover services to find the serial port characteristics
                discoverServices()

                // Find and cache the TX/RX characteristics
                rxCharacteristic = findCharacteristic(SERVICE_UUID, RX_CHAR_UUID)
                txCharacteristic = findCharacteristic(SERVICE_UUID, TX_CHAR_UUID)

                // Enable notifications on the RX characteristic
                enableNotifications(rxCharacteristic!!)

                isConnected = true
                connectedAddress = device.address
            }.onFailure {
                disconnect()
            }
        }

    override fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {
            // Ignore cleanup errors
        } finally {
            bluetoothGatt = null
            rxCharacteristic = null
            txCharacteristic = null
            isConnected = false
            connectedAddress = null
        }
    }

    // --- Private helpers (stub implementations) ---

    private suspend fun connectGatt(device: BluetoothDevice): BluetoothGatt =
        suspendCancellableCoroutine { continuation ->
            device.connectGatt(
                context, false,
                object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            continuation.resume(gatt)
                        } else if (status != BluetoothGatt.GATT_SUCCESS) {
                            continuation.resumeWithException(
                                IllegalStateException("BLE connection failed: status=$status")
                            )
                        }
                    }
                }
            )
        }

    private suspend fun discoverServices() {
        suspendCancellableCoroutine { continuation ->
            bluetoothGatt?.discoverServices()
            // Stub: service discovery callback would resume here
            continuation.resume(Unit)
        }
    }

    private fun findCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID
    ): BluetoothGattCharacteristic? {
        val service = bluetoothGatt?.getService(serviceUuid) ?: return null
        return service.getCharacteristic(charUuid)
    }

    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, true)
        // Descriptor write for CCCD would go here
    }

    companion object {
        private const val GATT_TIMEOUT_MS = 10_000L

        /** Placeholder UUIDs — must be updated for specific BLE OBD adapter profiles. */
        private val SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        private val RX_CHAR_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
        private val TX_CHAR_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    }
}

/**
 * InputStream wrapper for BLE receive data.
 * Full implementation will buffer data from GATT notification callbacks.
 */
private class BleInputStream(
    private val rxCharacteristic: BluetoothGattCharacteristic?
) : InputStream() {
    override fun read(): Int {
        throw UnsupportedOperationException("BLE streaming not yet implemented")
    }
}

/**
 * OutputStream wrapper for BLE transmit data.
 * Full implementation will write data to the TX GATT characteristic.
 */
private class BleOutputStream(
    private val txCharacteristic: BluetoothGattCharacteristic?,
    private val gatt: BluetoothGatt?
) : OutputStream() {
    override fun write(b: Int) {
        throw UnsupportedOperationException("BLE streaming not yet implemented")
    }
}
