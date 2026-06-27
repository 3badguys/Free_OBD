package com.freeobd.app.data.remote

import com.freeobd.app.domain.model.BluetoothDeviceInfo
import java.io.InputStream
import java.io.OutputStream

/**
 * Sealed interface abstracting the Bluetooth transport layer for OBD-II communication.
 *
 * Supports both classic Bluetooth SPP (RFCOMM) and BLE (GATT-based) adapters
 * behind a unified InputStream/OutputStream API that kotlin-obd-api can consume.
 */
sealed interface ObdTransport {

    /** Whether the transport is currently connected to a device. */
    val isConnected: Boolean

    /** The input stream for reading data from the OBD-II adapter. */
    val inputStream: InputStream

    /** The output stream for sending commands to the OBD-II adapter. */
    val outputStream: OutputStream

    /**
     * Connect to the specified Bluetooth device.
     *
     * @param device The target OBD-II adapter device info.
     * @return Success or failure with an error message.
     */
    suspend fun connect(device: BluetoothDeviceInfo): Result<Unit>

    /** Gracefully disconnect from the adapter and release resources. */
    fun disconnect()

    /** The address of the currently connected device, or null. */
    val connectedAddress: String?
}
