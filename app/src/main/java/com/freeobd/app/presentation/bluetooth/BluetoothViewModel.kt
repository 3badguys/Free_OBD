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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BluetoothViewModel(
    private val connectBluetoothUseCase: ConnectBluetoothUseCase,
    private val bluetoothRepository: BluetoothRepository,
    private val discoverPIDsUseCase: DiscoverPIDsUseCase,
    private val obdRepository: OBDRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BluetoothUiState>(BluetoothUiState.Idle)
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    // Demo mode
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val mockBtRepo by lazy { MockBluetoothRepository() }
    private val mockObdRepo by lazy { MockOBDRepository() }

    private val activeBtRepo: BluetoothRepository
        get() = if (_isDemoMode.value) mockBtRepo else bluetoothRepository

    private val activeObdRepo: OBDRepository
        get() = if (_isDemoMode.value) mockObdRepo else obdRepository

    // Track running jobs so they can be cancelled cleanly
    private var connectionObserverJob: Job? = null
    private var scanCollectorJob: Job? = null
    private var scanLaunchJob: Job? = null
    private var toggleInProgress = false

    private var selectedProtocol: String = "ATSP0"
    private var ecuAddress: String? = null
    // Use map keyed by address to deduplicate — data class equals/hashCode includes
    // all fields (including rssi), so a Set can't guarantee uniqueness by address alone.
    private val discoveredDevices = linkedMapOf<String, BluetoothDeviceInfo>()

    init {
        startConnectionObserver()
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
        if (toggleInProgress) return
        toggleInProgress = true

        // Cancel ALL running operations before switching
        cancelAllOperations()

        _isDemoMode.value = !_isDemoMode.value

        if (_isDemoMode.value) {
            DemoModeState.enableDemoMode()
        } else {
            DemoModeState.disableDemoMode()
        }

        discoveredDevices.clear()
        _uiState.value = BluetoothUiState.Idle

        // Restart connection observer for the newly active repo
        startConnectionObserver()
        toggleInProgress = false
    }

    /** Cancel all active jobs to prevent overlapping subscriptions. */
    private fun cancelAllOperations() {
        connectionObserverJob?.cancel()
        scanCollectorJob?.cancel()
        scanLaunchJob?.cancel()
        connectionObserverJob = null
        scanCollectorJob = null
        scanLaunchJob = null
    }

    private fun startScan() {
        // Cancel any previous scan before starting a new one
        scanCollectorJob?.cancel()
        scanLaunchJob?.cancel()

        val repo = activeBtRepo
        discoveredDevices.clear()
        _uiState.value = BluetoothUiState.Scanning

        scanLaunchJob = viewModelScope.launch {
            // Start collecting BEFORE calling startScan() so we don't miss events
            scanCollectorJob = repo.scanResults.collectSafely(viewModelScope) { device ->
                discoveredDevices[device.address] = device
                _uiState.value = BluetoothUiState.DevicesFound(
                    devices = discoveredDevices.values.toList().sortedByDescending { it.rssi ?: -100 },
                    isScanning = true
                )
            }

            repo.startScan().onFailure { error ->
                _uiState.value = BluetoothUiState.Error(
                    message = error.message ?: "Failed to start scan",
                    isRecoverable = true
                )
            }
        }
    }

    private fun stopScan() {
        viewModelScope.launch {
            activeBtRepo.stopScan()
            _uiState.value = BluetoothUiState.DevicesFound(
                devices = discoveredDevices.values.toList().sortedByDescending { it.rssi ?: -100 },
                isScanning = false
            )
        }
    }

    private fun connect(device: BluetoothDeviceInfo, protocol: String, ecuAddress: String?) {
        val repo = activeBtRepo
        viewModelScope.launch {
            _uiState.value = BluetoothUiState.Connecting(device)

            if (_isDemoMode.value) {
                repo.connect(device, protocol, ecuAddress).fold(
                    onSuccess = {
                        activeObdRepo.initELM327(protocol, ecuAddress).fold(
                            onSuccess = {
                                _uiState.value = BluetoothUiState.Connected(
                                    deviceName = device.name ?: device.address,
                                    deviceAddress = device.address,
                                    protocol = protocol
                                )
                                launch { activeObdRepo.discoverSupportedPIDs().onFailure { } }
                            },
                            onFailure = { error ->
                                repo.disconnect()
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
                connectBluetoothUseCase(device, protocol, ecuAddress).fold(
                    onSuccess = {
                        _uiState.value = BluetoothUiState.Connected(
                            deviceName = device.name ?: device.address,
                            deviceAddress = device.address,
                            protocol = protocol
                        )
                        launch {
                            discoverPIDsUseCase().onFailure { error ->
                                android.util.Log.w("BluetoothVM", "PID discovery failed: ${error.message}")
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

    private fun startConnectionObserver() {
        connectionObserverJob?.cancel()
        connectionObserverJob = activeBtRepo.connectionState.collectSafely(viewModelScope) { state ->
            when (state) {
                ConnectionState.RECONNECTING ->
                    _uiState.value = BluetoothUiState.Error(
                        message = "Connection lost. Tap to reconnect.",
                        isRecoverable = true
                    )
                else -> { /* handled by explicit events */ }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllOperations()
        viewModelScope.launch { bluetoothRepository.stopScan() }
    }

    fun getActiveObdRepository(): OBDRepository = activeObdRepo
}
