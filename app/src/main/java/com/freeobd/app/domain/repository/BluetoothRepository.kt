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

package com.freeobd.app.domain.repository

import com.freeobd.app.data.remote.ObdTransport
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.ConnectionState
import com.freeobd.app.domain.model.DeviceType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Bluetooth OBD-II adapter management.
 * Abstracts both SPP (classic) and BLE transports behind a unified API.
 */
interface BluetoothRepository {

    /** Observable stream of the current connection state. */
    val connectionState: Flow<ConnectionState>

    /**
     * Start scanning for nearby Bluetooth OBD-II adapter devices.
     * Emits discovered devices via [scanResults] Flow.
     */
    suspend fun startScan(): Result<Unit>

    /** Stop an active device scan. */
    suspend fun stopScan()

    /** Flow of discovered devices during an active scan. */
    val scanResults: Flow<BluetoothDeviceInfo>

    /**
     * Connect to the specified OBD-II adapter device.
     *
     * @param device The target Bluetooth device to connect to.
     * @param protocol The ELM327 protocol string (e.g. "ATSP0"). Use "ATSP0" for auto-detect.
     * @param ecuAddress The CAN ID address in hex format (e.g. "7DF"). Null for default.
     * @param transportType The transport to use — SPP (classic) or BLE. Defaults to SPP
     *   since the vast majority of OBD-II adapters use classic Bluetooth.
     */
    suspend fun connect(
        device: BluetoothDeviceInfo,
        protocol: String = "ATSP0",
        ecuAddress: String? = null,
        transportType: DeviceType = DeviceType.SPP
    ): Result<Unit>

    /** Disconnect from the currently connected adapter. */
    suspend fun disconnect()

    /** Returns true if currently connected to an OBD-II adapter. */
    val isConnected: Boolean

    /** The MAC/address of the currently connected device, or null. */
    val connectedDeviceAddress: String?

    /** The active OBD transport, available only when connected. */
    val transport: ObdTransport?
}
