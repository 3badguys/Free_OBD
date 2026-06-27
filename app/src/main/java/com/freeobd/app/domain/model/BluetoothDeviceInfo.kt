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
