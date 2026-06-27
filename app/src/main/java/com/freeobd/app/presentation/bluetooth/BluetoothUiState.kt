package com.freeobd.app.presentation.bluetooth

import com.freeobd.app.domain.model.BluetoothDeviceInfo

/**
 * Sealed class hierarchy for the Bluetooth connection screen UI state.
 *
 * Follows the unidirectional data flow pattern:
 * - ViewModel exposes a single StateFlow<BluetoothUiState>
 * - Screen collects and renders based on the current state
 */
sealed interface BluetoothUiState {

    /** Initial state — no scan active, no connection. */
    data object Idle : BluetoothUiState

    /** Actively scanning for nearby Bluetooth devices. */
    data object Scanning : BluetoothUiState

    /** Scan completed with results. */
    data class DevicesFound(
        val devices: List<BluetoothDeviceInfo>,
        val isScanning: Boolean = false
    ) : BluetoothUiState

    /** Attempting to connect to a specific device. */
    data class Connecting(
        val device: BluetoothDeviceInfo
    ) : BluetoothUiState

    /** Successfully connected — OBD communication is ready. */
    data class Connected(
        val deviceName: String,
        val deviceAddress: String,
        val protocol: String = "Auto-detect"
    ) : BluetoothUiState

    /** An error occurred during scanning or connection. */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true
    ) : BluetoothUiState
}

/**
 * User actions that the Bluetooth screen can dispatch to the ViewModel.
 */
sealed interface BluetoothEvent {
    /** Start scanning for nearby devices. */
    data object StartScan : BluetoothEvent

    /** Stop an active scan. */
    data object StopScan : BluetoothEvent

    /** Connect to the selected device. */
    data class Connect(
        val device: BluetoothDeviceInfo,
        val protocol: String = "ATSP0",
        val ecuAddress: String? = null
    ) : BluetoothEvent

    /** Disconnect from the current device. */
    data object Disconnect : BluetoothEvent

    /** Dismiss an error and return to idle state. */
    data object DismissError : BluetoothEvent

    /** Select a specific OBD protocol. */
    data class SelectProtocol(val protocol: String) : BluetoothEvent

    /** Set a specific ECU CAN address. */
    data class SetEcuAddress(val address: String) : BluetoothEvent
}
