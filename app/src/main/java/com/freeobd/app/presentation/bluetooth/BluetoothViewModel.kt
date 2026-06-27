package com.freeobd.app.presentation.bluetooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.domain.model.BluetoothDeviceInfo
import com.freeobd.app.domain.model.ConnectionState
import com.freeobd.app.domain.repository.BluetoothRepository
import com.freeobd.app.domain.usecase.ConnectBluetoothUseCase
import com.freeobd.app.domain.usecase.DiscoverPIDsUseCase
import com.freeobd.app.utils.collectSafely
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Bluetooth connection screen.
 *
 * Manages:
 * - Device scanning lifecycle
 * - Connection flow (device → ELM327 init → PID discovery)
 * - Protocol and ECU address configuration
 * - UI state updates based on Bluetooth connection state
 */
class BluetoothViewModel(
    private val connectBluetoothUseCase: ConnectBluetoothUseCase,
    private val bluetoothRepository: BluetoothRepository,
    private val discoverPIDsUseCase: DiscoverPIDsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<BluetoothUiState>(BluetoothUiState.Idle)
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    // Current configuration
    private var selectedProtocol: String = "ATSP0"
    private var ecuAddress: String? = null

    // Accumulated scan results
    private val discoveredDevices = mutableSetOf<BluetoothDeviceInfo>()

    init {
        // Observe connection state changes
        observeConnectionState()
    }

    /**
     * Handle user actions dispatched from the UI.
     */
    fun onEvent(event: BluetoothEvent) {
        when (event) {
            BluetoothEvent.StartScan -> startScan()
            BluetoothEvent.StopScan -> stopScan()
            is BluetoothEvent.Connect -> connect(event.device, event.protocol, event.ecuAddress)
            BluetoothEvent.Disconnect -> disconnect()
            BluetoothEvent.DismissError -> dismissError()
            is BluetoothEvent.SelectProtocol -> {
                selectedProtocol = event.protocol
            }
            is BluetoothEvent.SetEcuAddress -> {
                ecuAddress = event.address.ifBlank { null }
            }
        }
    }

    private fun startScan() {
        viewModelScope.launch {
            discoveredDevices.clear()
            _uiState.value = BluetoothUiState.Scanning

            bluetoothRepository.startScan().onFailure { error ->
                _uiState.value = BluetoothUiState.Error(
                    message = error.message ?: "Failed to start scan",
                    isRecoverable = true
                )
            }

            // Collect scan results
            bluetoothRepository.scanResults.collectSafely(viewModelScope) { device ->
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
            bluetoothRepository.stopScan()
            _uiState.value = BluetoothUiState.DevicesFound(
                devices = discoveredDevices.toList().sortedByDescending { it.rssi ?: -100 },
                isScanning = false
            )
        }
    }

    private fun connect(device: BluetoothDeviceInfo, protocol: String, ecuAddress: String?) {
        viewModelScope.launch {
            _uiState.value = BluetoothUiState.Connecting(device)

            connectBluetoothUseCase(device, protocol, ecuAddress).fold(
                onSuccess = {
                    _uiState.value = BluetoothUiState.Connected(
                        deviceName = device.name ?: device.address,
                        deviceAddress = device.address,
                        protocol = protocol
                    )

                    // Automatically discover supported PIDs after connection
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

    private fun disconnect() {
        viewModelScope.launch {
            bluetoothRepository.disconnect()
            _uiState.value = BluetoothUiState.Idle
        }
    }

    private fun dismissError() {
        _uiState.value = BluetoothUiState.Idle
    }

    /**
     * Observe the underlying Bluetooth connection state and update UI accordingly.
     */
    private fun observeConnectionState() {
        bluetoothRepository.connectionState.collectSafely(viewModelScope) { state ->
            when (state) {
                ConnectionState.DISCONNECTED,
                ConnectionState.DISCONNECTING ->
                    if (_uiState.value !is BluetoothUiState.Error) {
                        _uiState.value = BluetoothUiState.Idle
                    }

                ConnectionState.RECONNECTING ->
                    _uiState.value = BluetoothUiState.Error(
                        message = "Connection lost. Tap to reconnect.",
                        isRecoverable = true
                    )

                else -> {
                    // Other states are handled by the connect() flow
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            bluetoothRepository.stopScan()
        }
    }
}
