package com.freeobd.app.presentation.bluetooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.data.mock.MockBluetoothRepository
import com.freeobd.app.data.mock.MockOBDRepository
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.ConnectionState
import com.freeobd.app.domain.repository.BluetoothRepository
import com.freeobd.app.domain.repository.OBDRepository
import com.freeobd.app.domain.usecase.ConnectBluetoothUseCase
import com.freeobd.app.domain.usecase.DiscoverPIDsUseCase
import com.freeobd.app.utils.collectSafely
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Bluetooth connection screen.
 *
 * Supports two modes:
 * - **Live mode**: uses real Bluetooth and OBD repositories
 * - **Demo mode**: uses mock repositories generating simulated vehicle data
 */
class BluetoothViewModel(
    private val connectBluetoothUseCase: ConnectBluetoothUseCase,
    private val bluetoothRepository: BluetoothRepository,
    private val discoverPIDsUseCase: DiscoverPIDsUseCase,
    private val obdRepository: OBDRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BluetoothUiState>(BluetoothUiState.Idle)
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    // Demo mode — exposed as StateFlow for Compose reactivity
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val mockBtRepo by lazy { MockBluetoothRepository() }
    private val mockObdRepo by lazy { MockOBDRepository() }

    /** Active repositories — switches between real and mock based on demo mode. */
    private val activeBtRepo: BluetoothRepository
        get() = if (_isDemoMode.value) mockBtRepo else bluetoothRepository

    private val activeObdRepo: OBDRepository
        get() = if (_isDemoMode.value) mockObdRepo else obdRepository

    // Configuration
    private var selectedProtocol: String = "ATSP0"
    private var ecuAddress: String? = null
    private val discoveredDevices = mutableSetOf<BluetoothDeviceInfo>()

    init {
        observeConnectionState()
    }

    fun onEvent(event: BluetoothEvent) {
        when (event) {
            BluetoothEvent.StartScan -> startScan()
            BluetoothEvent.StopScan -> stopScan()
            is BluetoothEvent.Connect -> connect(event.device, event.protocol, event.ecuAddress)
            BluetoothEvent.Disconnect -> disconnect()
            BluetoothEvent.DismissError -> dismissError()
            is BluetoothEvent.SelectProtocol -> { selectedProtocol = event.protocol }
            is BluetoothEvent.SetEcuAddress -> { ecuAddress = event.address.ifBlank { null } }
            is BluetoothEvent.ToggleDemoMode -> toggleDemoMode()
        }
    }

    private fun toggleDemoMode() {
        _isDemoMode.value = !_isDemoMode.value

        if (_isDemoMode.value) {
            DemoModeState.enableDemoMode()
            viewModelScope.launch { bluetoothRepository.stopScan() }
        } else {
            DemoModeState.disableDemoMode()
            viewModelScope.launch { mockBtRepo.stopScan() }
        }

        discoveredDevices.clear()
        _uiState.value = BluetoothUiState.Idle
        observeConnectionState()
    }

    private fun startScan() {
        viewModelScope.launch {
            discoveredDevices.clear()
            _uiState.value = BluetoothUiState.Scanning

            activeBtRepo.startScan().onFailure { error ->
                _uiState.value = BluetoothUiState.Error(
                    message = error.message ?: "Failed to start scan",
                    isRecoverable = true
                )
            }

            activeBtRepo.scanResults.collectSafely(viewModelScope) { device ->
                discoveredDevices.add(device)
                _uiState.value = BluetoothUiState.DevicesFound(
                    devices = discoveredDevices.toList().sortedByDescending { it.rssi ?: -100 },
                    isScanning = true
                )
            }
        }
    }

    private fun stopScan() {
        viewModelScope.launch {
            activeBtRepo.stopScan()
            _uiState.value = BluetoothUiState.DevicesFound(
                devices = discoveredDevices.toList().sortedByDescending { it.rssi ?: -100 },
                isScanning = false
            )
        }
    }

    private fun connect(device: BluetoothDeviceInfo, protocol: String, ecuAddress: String?) {
        viewModelScope.launch {
            _uiState.value = BluetoothUiState.Connecting(device)

            if (_isDemoMode.value) {
                // Demo mode: connect via mock, then init mock OBD
                activeBtRepo.connect(device, protocol, ecuAddress).fold(
                    onSuccess = {
                        activeObdRepo.initELM327(protocol, ecuAddress).fold(
                            onSuccess = {
                                _uiState.value = BluetoothUiState.Connected(
                                    deviceName = device.name ?: device.address,
                                    deviceAddress = device.address,
                                    protocol = protocol
                                )
                                launch {
                                    activeObdRepo.discoverSupportedPIDs().onFailure { /* ignore */ }
                                }
                            },
                            onFailure = { error ->
                                activeBtRepo.disconnect()
                                _uiState.value = BluetoothUiState.Error(
                                    message = "ELM327 init failed: ${error.message}",
                                    isRecoverable = true
                                )
                            }
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = BluetoothUiState.Error(
                            message = error.message ?: "Connection failed",
                            isRecoverable = true
                        )
                    }
                )
            } else {
                // Live mode: use the ConnectBluetoothUseCase
                connectBluetoothUseCase(device, protocol, ecuAddress).fold(
                    onSuccess = {
                        _uiState.value = BluetoothUiState.Connected(
                            deviceName = device.name ?: device.address,
                            deviceAddress = device.address,
                            protocol = protocol
                        )
                        launch {
                            discoverPIDsUseCase().onFailure { error ->
                                android.util.Log.w("BluetoothVM",
                                    "PID discovery failed: ${error.message}")
                            }
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = BluetoothUiState.Error(
                            message = error.message ?: "Connection failed",
                            isRecoverable = true
                        )
                    }
                )
            }
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            activeBtRepo.disconnect()
            _uiState.value = BluetoothUiState.Idle
        }
    }

    private fun dismissError() {
        _uiState.value = BluetoothUiState.Idle
    }

    private fun observeConnectionState() {
        activeBtRepo.connectionState.collectSafely(viewModelScope) { state ->
            when (state) {
                ConnectionState.DISCONNECTED, ConnectionState.DISCONNECTING ->
                    if (_uiState.value !is BluetoothUiState.Error) {
                        _uiState.value = BluetoothUiState.Idle
                    }
                ConnectionState.RECONNECTING ->
                    _uiState.value = BluetoothUiState.Error(
                        message = "Connection lost. Tap to reconnect.",
                        isRecoverable = true
                    )
                else -> { /* handled by connect() flow */ }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            bluetoothRepository.stopScan()
        }
    }

    /** Get the active OBD repository (mock or real) for downstream screens. */
    fun getActiveObdRepository(): OBDRepository = activeObdRepo
}
