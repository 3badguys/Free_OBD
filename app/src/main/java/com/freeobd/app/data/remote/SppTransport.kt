package com.freeobd.app.data.remote

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Classic Bluetooth SPP (RFCOMM) transport implementation.
 *
 * Establishes an RFCOMM socket connection to ELM327-style OBD-II adapters.
 * Includes socket timeout, fallback to insecure RFCOMM for older devices,
 * and proper resource cleanup on disconnect.
 *
 * Connection strategy (each fallback cleans up its failed socket before
 * trying the next method, to avoid leaked sockets blocking the RFCOMM channel):
 * 1. [tryConnectWithServiceRecord] — standard secure RFCOMM via SDP lookup
 * 2. [tryConnectInsecure] — insecure RFCOMM (some adapters lack SDP)
 * 3. [tryConnectReflection] — direct RFCOMM channel via reflection (1, 3, 5, 7, 10)
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

    @Suppress("DEPRECATION")
    override suspend fun connect(device: BluetoothDeviceInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("Bluetooth not available on this device")

                val bluetoothDevice: BluetoothDevice = adapter.getRemoteDevice(device.address)

                // Try each connection method; each closes its own socket on failure
                socket = tryConnectWithServiceRecord(bluetoothDevice)
                    ?: tryConnectInsecure(bluetoothDevice)
                    ?: tryConnectReflection(bluetoothDevice)
                    ?: throw IllegalStateException(
                        "Failed to connect to ${device.address}: all RFCOMM methods exhausted"
                    )

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

    // ── Connection methods ────────────────────────────────────
    //
    // Each returns a connected BluetoothSocket on success, or null on
    // failure. On failure, the socket is closed before returning null
    // so it doesn't leak and block the RFCOMM channel for subsequent attempts.

    /**
     * Primary method: standard secure RFCOMM socket via SDP service lookup.
     */
    private suspend fun tryConnectWithServiceRecord(
        device: BluetoothDevice
    ): BluetoothSocket? {
        val sppUuid = UUID.fromString(SPP_UUID)
        var sock: BluetoothSocket? = null
        return try {
            sock = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                device.createRfcommSocketToServiceRecord(sppUuid)
            } ?: return null // socket creation timed out

            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                sock!!.connect()
            }

            if (connected == null) { // connect() timed out
                closeQuietly(sock)
                return null
            }
            sock
        } catch (e: Exception) {
            closeQuietly(sock)
            null
        }
    }

    /**
     * Fallback 1: insecure RFCOMM socket (for adapters without proper SDP records).
     */
    private suspend fun tryConnectInsecure(
        device: BluetoothDevice
    ): BluetoothSocket? {
        val sppUuid = UUID.fromString(SPP_UUID)
        var sock: BluetoothSocket? = null
        return try {
            sock = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                device.createInsecureRfcommSocketToServiceRecord(sppUuid)
            } ?: return null // socket creation timed out

            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                sock!!.connect()
            }

            if (connected == null) { // connect() timed out
                closeQuietly(sock)
                return null
            }
            sock
        } catch (e: Exception) {
            closeQuietly(sock)
            null
        }
    }

    /**
     * Fallback 2: reflection-based socket for very old or non-standard adapters.
     *
     * Tries multiple common RFCOMM channels [1, 3, 5, 7, 10] since the adapter
     * may not use the default channel 1.
     */
    private fun tryConnectReflection(device: BluetoothDevice): BluetoothSocket? {
        for (channel in REFLECTION_CHANNELS) {
            var sock: BluetoothSocket? = null
            try {
                val method = device.javaClass.getMethod(
                    "createRfcommSocket", Int::class.javaPrimitiveType
                )
                sock = method.invoke(device, channel) as BluetoothSocket
                sock.connect()
                return sock // success on this channel
            } catch (e: Exception) {
                closeQuietly(sock)
                // Try next channel after a short delay
                runCatching { Thread.sleep(CHANNEL_RETRY_DELAY_MS) }
            }
        }
        return null // all channels exhausted
    }

    /** Close a socket silently; never throws. */
    private fun closeQuietly(sock: BluetoothSocket?) {
        try {
            sock?.close()
        } catch (_: Exception) {
            // Already closed or invalid — ignore
        }
    }

    companion object {
        /** Well-known SPP UUID for serial port profile. */
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

        /** Timeout for individual socket creation and connect() calls. */
        private const val CONNECT_TIMEOUT_MS = 4_000L

        /** Common RFCOMM channels to try via the reflection method. */
        private val REFLECTION_CHANNELS = intArrayOf(1, 3, 5, 7, 10)

        /** Delay between channel retries in the reflection fallback. */
        private const val CHANNEL_RETRY_DELAY_MS = 200L
    }
}
