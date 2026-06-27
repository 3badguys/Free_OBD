package com.freeobd.app.data.remote

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Classic Bluetooth SPP (RFCOMM) transport implementation.
 *
 * Establishes an RFCOMM socket connection to ELM327-style OBD-II adapters.
 * Includes socket timeout, fallback to insecure RFCOMM for older devices,
 * and proper resource cleanup on disconnect.
 */
class SppTransport : ObdTransport {

    private var socket: BluetoothSocket? = null

    @Volatile
    override var isConnected: Boolean = false
        private set

    override var connectedAddress: String? = null
        private set

    override val inputStream: InputStream
        get() = socket?.inputStream
            ?: throw IllegalStateException("Not connected — no input stream available")

    override val outputStream: OutputStream
        get() = socket?.outputStream
            ?: throw IllegalStateException("Not connected — no output stream available")

    override suspend fun connect(device: BluetoothDeviceInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("Bluetooth not available on this device")

                val bluetoothDevice: BluetoothDevice = adapter.getRemoteDevice(device.address)
                val sppUuid = UUID.fromString(SPP_UUID)

                // Attempt connection with socket timeout
                socket = withTimeout(SOCKET_TIMEOUT_MS) {
                    try {
                        // Primary method: standard secure RFCOMM socket
                        bluetoothDevice.createRfcommSocketToServiceRecord(sppUuid).also {
                            it.connect()
                        }
                    } catch (e: Exception) {
                        // Fallback: insecure RFCOMM socket for older adapters
                        try {
                            bluetoothDevice.createInsecureRfcommSocketToServiceRecord(sppUuid)
                                .also { it.connect() }
                        } catch (e2: Exception) {
                            // Ultimate fallback: reflection-based socket for very old devices
                            createReflectionSocket(bluetoothDevice)
                        }
                    }
                }

                isConnected = true
                connectedAddress = device.address
            }.onFailure {
                disconnect()
            }
        }

    override fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {
            // Socket may already be closed — ignore
        } finally {
            socket = null
            isConnected = false
            connectedAddress = null
        }
    }

    /**
     * Fallback method using reflection to create a socket on older Samsung/HTC devices
     * where the standard RFCOMM creation methods may fail.
     */
    private fun createReflectionSocket(device: BluetoothDevice): BluetoothSocket {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return (method.invoke(device, REFLECTION_CHANNEL) as BluetoothSocket).also {
            it.connect()
        }
    }

    companion object {
        /** Well-known SPP UUID for serial port profile. */
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

        /** Socket connection timeout in milliseconds. */
        private const val SOCKET_TIMEOUT_MS = 5_000L

        /** RFCOMM channel used in reflection-based fallback. */
        private const val REFLECTION_CHANNEL = 1
    }
}
