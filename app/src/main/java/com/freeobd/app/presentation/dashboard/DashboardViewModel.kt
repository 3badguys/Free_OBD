package com.freeobd.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.domain.model.OBDData
import com.freeobd.app.domain.repository.OBDRepository
import com.freeobd.app.domain.usecase.DiscoverPIDsUseCase
import com.freeobd.app.domain.usecase.ReadLiveDataUseCase
import com.freeobd.app.utils.collectSafely
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ViewModel for the live data dashboard.
 *
 * Manages:
 * - PID selection for gauge display
 * - Real-time data polling lifecycle
 * - Polling rate control
 */
class DashboardViewModel(
    private val readLiveDataUseCase: ReadLiveDataUseCase,
    private val discoverPIDsUseCase: DiscoverPIDsUseCase,
    private val obdRepository: OBDRepository
) : ViewModel() {

    /** Resolves to mock repo in demo mode, real repo otherwise. */
    private val activeRepo: OBDRepository
        get() = DemoModeState.current ?: obdRepository

    private val _uiState = MutableStateFlow<DashboardUiState>(
        DashboardUiState.Loading
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Default PID set for initial display
    private val _selectedPids = MutableStateFlow(
        setOf(0x0C, 0x0D, 0x05, 0x11, 0x04, 0x2F)
    )
    val selectedPids: StateFlow<Set<Int>> = _selectedPids.asStateFlow()

    private var pollingJob: Job? = null
    private var pollingIntervalMs: Long = 250

    // Latest known values (preserved across start/stop)
    private val lastValues = mutableMapOf<Int, OBDData>()

    init {
        // Auto-start polling when the dashboard is first created
        startPolling()
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.StartPolling -> startPolling()
            DashboardEvent.StopPolling -> stopPolling()
            is DashboardEvent.AddPid -> addPid(event.pidId)
            is DashboardEvent.RemovePid -> removePid(event.pidId)
            is DashboardEvent.SetPollingInterval -> {
                pollingIntervalMs = event.intervalMs
                // Restart polling with new interval if active
                if (pollingJob?.isActive == true) {
                    stopPolling()
                    startPolling()
                }
            }
        }
    }

    private fun startPolling() {
        val pids = _selectedPids.value.toList()
        if (pids.isEmpty()) {
            _uiState.value = DashboardUiState.Error("No PIDs selected")
            return
        }

        lastValues.clear()

        _uiState.value = DashboardUiState.Active(
            pidValues = emptyMap(),
            isPolling = true,
            selectedPids = _selectedPids.value,
            pollingIntervalMs = pollingIntervalMs
        )

        // Use mock repo in demo mode, use case otherwise
        val dataFlow = if (DemoModeState.isDemoMode) {
            activeRepo.pollPIDs(pids, pollingIntervalMs)
        } else {
            readLiveDataUseCase(pids, pollingIntervalMs)
        }

        pollingJob = dataFlow.collectSafely(viewModelScope) { pidValues ->
            lastValues.putAll(pidValues)
            _uiState.value = DashboardUiState.Active(
                pidValues = lastValues.toMap(),
                isPolling = true,
                selectedPids = _selectedPids.value,
                pollingIntervalMs = pollingIntervalMs
            )
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null

        _uiState.value = DashboardUiState.Paused(
            lastValues = lastValues.toMap(),
            selectedPids = _selectedPids.value
        )
    }

    private fun addPid(pidId: Int) {
        _selectedPids.update { it + pidId }
        restartIfPolling()
    }

    private fun removePid(pidId: Int) {
        _selectedPids.update { it - pidId }
        restartIfPolling()
    }

    private fun restartIfPolling() {
        if (pollingJob?.isActive == true) {
            stopPolling()
            startPolling()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
