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

package com.freeobd.app.domain.model

/**
 * Represents a discovered Bluetooth OBD-II adapter device.
 *
 * @property address MAC address (classic Bluetooth) or hardware ID (BLE).
 * @property name Human-readable device name, if available.
 * @property type Bluetooth transport type.
 * @property rssi Signal strength in dBm (optional).
 * @property isPaired Whether the device is already paired with this phone.
 */
data class BluetoothDeviceInfo(
    val address: String,
    val name: String? = null,
    val type: DeviceType = DeviceType.UNKNOWN,
    val rssi: Int? = null,
    val isPaired: Boolean = false
)

enum class DeviceType {
    /** Classic Bluetooth SPP (RFCOMM) device. */
    SPP,

    /** Bluetooth Low Energy (GATT-based) device. */
    BLE,

    /** Unknown or undetermined device type. */
    UNKNOWN
}
