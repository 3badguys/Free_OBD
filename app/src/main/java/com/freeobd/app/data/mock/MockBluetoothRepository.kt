package com.freeobd.app.data.mock

import com.freeobd.app.data.remote.ObdTransport
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.ConnectionState
import com.freeobd.app.domain.model.DeviceType
import com.freeobd.app.domain.repository.BluetoothRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/**
 * Mock Bluetooth repository for demo/testing without real OBD hardware.
 *
 * Simulates device scanning with fake adapters, instant connection,
 * and provides a no-op transport for the mock OBD layer.
 */
class MockBluetoothRepository : BluetoothRepository {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scanResults = MutableSharedFlow<BluetoothDeviceInfo>(
        replay = 8,
        extraBufferCapacity = 8
    )
    override val scanResults: Flow<BluetoothDeviceInfo> = _scanResults.asSharedFlow()

    override val transport: ObdTransport? = null // Mock OBD layer doesn't need a real transport

    @Volatile
    override var isConnected: Boolean = false
        private set

    override var connectedDeviceAddress: String? = null
        private set

    override suspend fun startScan(): Result<Unit> {
        _connectionState.value = ConnectionState.SCANNING

        // Emit fake OBD adapter devices with a small delay to simulate discovery
        val fakeDevices = listOf(
            BluetoothDeviceInfo(
                address = "00:11:22:33:44:01",
                name = "OBDII ELM327 v1.5",
                type = DeviceType.SPP,
                rssi = -45,
                isPaired = true
            ),
            BluetoothDeviceInfo(
                address = "00:11:22:33:44:02",
                name = "Vgate iCar Pro BLE",
                type = DeviceType.BLE,
                rssi = -62,
                isPaired = false
            ),
            BluetoothDeviceInfo(
                address = "00:11:22:33:44:03",
                name = "V-LINK OBD2",
                type = DeviceType.SPP,
                rssi = -70,
                isPaired = false
            ),
            BluetoothDeviceInfo(
                address = "00:11:22:33:44:04",
                name = "Carista OBD2 BLE",
                type = DeviceType.BLE,
                rssi = -78,
                isPaired = false
            )
        )

        for (device in fakeDevices) {
            _scanResults.emit(device)
            delay(400) // Simulate staggered discovery
        }

        _connectionState.value = ConnectionState.DISCONNECTED
        return Result.success(Unit)
    }

    override suspend fun stopScan() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun connect(
        device: BluetoothDeviceInfo,
        protocol: String,
        ecuAddress: String?
    ): Result<Unit> {
        _connectionState.value = ConnectionState.CONNECTING
        delay(500) // Simulate connection time
        isConnected = true
        connectedDeviceAddress = device.address
        _connectionState.value = ConnectionState.CONNECTED
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        isConnected = false
        connectedDeviceAddress = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
