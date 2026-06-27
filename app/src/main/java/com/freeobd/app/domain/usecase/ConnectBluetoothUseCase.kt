package com.freeobd.app.domain.usecase

import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.ConnectionState
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
     * @param ecuAddress Optional ECU CAN ID address (e.g. "7DF").
     * @return Success or failure with a descriptive error message.
     */
    suspend operator fun invoke(
        device: BluetoothDeviceInfo,
        protocol: String = "ATSP0",
        ecuAddress: String? = null
    ): Result<Unit> {
        // Step 1: Establish Bluetooth connection
        bluetoothRepository.connect(device).getOrElse { error ->
            return Result.failure(
                ConnectionException("Bluetooth connection failed: ${error.message}", error)
            )
        }

        // Step 2: Initialize ELM327 with the selected protocol and ECU address
        return obdRepository.initELM327(protocol, ecuAddress).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                // If ELM327 init fails, disconnect Bluetooth
                bluetoothRepository.disconnect()
                Result.failure(
                    ConnectionException("ELM327 initialization failed: ${error.message}", error)
                )
            }
        )
    }
}

/** Custom exception for connection-related failures. */
class ConnectionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
