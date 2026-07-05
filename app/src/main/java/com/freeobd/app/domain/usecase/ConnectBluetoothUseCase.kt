package com.freeobd.app.domain.usecase

import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.ConnectionState
import com.freeobd.app.domain.model.DeviceType
import com.freeobd.app.domain.model.ProtocolInfo
import com.freeobd.app.domain.repository.BluetoothRepository
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.flow.Flow

/**
 * Orchestrates the full Bluetooth connection flow:
 * 1. Connect to the selected adapter via Bluetooth
 * 2. Initialize the ELM327 with the chosen protocol
 * 3. Optionally set the ECU CAN address
 */
class ConnectBluetoothUseCase(
    private val bluetoothRepository: BluetoothRepository,
    private val obdRepository: OBDRepository
) {
    /** Observable stream of connection state changes. */
    val connectionState: Flow<ConnectionState> = bluetoothRepository.connectionState

    /**
     * Execute the full connection flow.
     *
     * @param device The Bluetooth device to connect to.
     * @param protocol ELM327 protocol command (e.g. "ATSP0"). Use "ATSP0" for auto-detect.
     * @param ecuAddress Optional ECU address (e.g. "7DF" for CAN, "33" for KWP).
     * @param transportType Transport to use — SPP (classic) or BLE.
     * @param cryptoKey Optional crypto key for Chinese clone adapters (AT+SETCRYPT).
     * @return The negotiated protocol info on success, or failure.
     */
    suspend operator fun invoke(
        device: BluetoothDeviceInfo,
        protocol: String = "ATSP0",
        ecuAddress: String? = null,
        transportType: DeviceType = DeviceType.SPP,
        cryptoKey: String? = null
    ): Result<ProtocolInfo> {
        // Step 1: Establish Bluetooth connection
        bluetoothRepository.connect(device, protocol, ecuAddress, transportType).getOrElse { error ->
            return Result.failure(
                ConnectionException("Bluetooth connection failed: ${error.message}", error)
            )
        }

        // Step 2: Initialize ELM327 with the selected protocol and ECU address
        obdRepository.initELM327(protocol, ecuAddress, cryptoKey).getOrElse { error ->
            bluetoothRepository.disconnect()
            return Result.failure(
                ConnectionException("ELM327 initialization failed: ${error.message}", error)
            )
        }

        // Step 3: Query the actual negotiated protocol
        return obdRepository.getProtocolInfo()
    }
}

/** Custom exception for connection-related failures. */
class ConnectionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
