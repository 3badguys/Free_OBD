/*
 * Copyright 2026 3badguys <chuiC456@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.freeobd.app.presentation.bluetooth

import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.DeviceType
import com.freeobd.app.domain.model.ProtocolInfo

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
        val protocol: String = "Auto-detect",
        val protocolInfo: ProtocolInfo? = null,
        val adapterInfo: String? = null,
        val voltage: Double? = null
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
        val ecuAddress: String? = null,
        val transportType: DeviceType = DeviceType.SPP
    ) : BluetoothEvent

    /** Disconnect from the current device. */
    data object Disconnect : BluetoothEvent

    /** Dismiss an error and return to idle state. */
    data object DismissError : BluetoothEvent

    /** Select a specific OBD protocol. */
    data class SelectProtocol(val protocol: String) : BluetoothEvent

    /** Select the Bluetooth transport type (SPP or BLE). */
    data class SelectTransport(val transportType: DeviceType) : BluetoothEvent

    /** Set a specific ECU CAN address. */
    data class SetEcuAddress(val address: String) : BluetoothEvent

    /** Enable debug logging for the Debug Console. */
    data object EnableDebugLogging : BluetoothEvent

    /** Disable debug logging for the Debug Console. */
    data object DisableDebugLogging : BluetoothEvent

    /** Toggle demo mode on/off. */
    data object ToggleDemoMode : BluetoothEvent
}
