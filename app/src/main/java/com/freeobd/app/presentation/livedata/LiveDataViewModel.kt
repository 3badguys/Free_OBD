package com.freeobd.app.presentation.livedata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.data.remote.NegativeResponseException
import com.freeobd.app.domain.model.LiveDataDiscovery
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LiveDataViewModel(
    private val obdRepository: OBDRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val activeRepo get() = DemoModeState.current ?: obdRepository

    private val _uiState = MutableStateFlow(LiveDataUiState())
    val uiState: StateFlow<LiveDataUiState> = _uiState.asStateFlow()

    private var pidNames: Map<Int, String> = emptyMap()

    fun onEvent(event: LiveDataEvent) {
        when (event) {
            LiveDataEvent.Load -> load()
            is LiveDataEvent.SelectSegment -> {
                _uiState.value = _uiState.value.copy(segment = event.segment, isLoading = true)
                load()
            }
            is LiveDataEvent.ScrollToPid -> {
                _uiState.value = _uiState.value.copy(scrollToPid = event.pidId)
            }
        }
    }

    fun onScrollConsumed() {
        _uiState.value = _uiState.value.copy(scrollToPid = null)
    }

    private fun load() {
        viewModelScope.launch {
            if (pidNames.isEmpty()) {
                pidNames = database.pidMetadataDao().getByMode(0x01).associate { it.pidId to it.description }
            }

            val segment = _uiState.value.segment
            activeRepo.discoverLiveDataPIDs(segment).fold(
                onSuccess = { discovery -> buildStates(discovery) },
                onFailure = { error ->
                    // Negative response or I/O error — show all PIDs as unsupported (red blocks)
                    buildErrorStates(segment, error)
                }
            )
        }
    }

    private fun buildErrorStates(segment: Int, error: Throwable) {
        val errorHex = (error as? NegativeResponseException)?.toHexString() ?: "ERR"
        val states = (1..32).map { offset ->
            val pidId = segment + offset
            val desc = pidNames[pidId] ?: String.format("PID 0x%02X", pidId)
            LiveDataPidState(pidId = pidId, description = desc, isSupported = false,
                result = LiveDataPidResult.Error("Segment not available"))
        }
        _uiState.value = LiveDataUiState(
            segment = segment, bitmapHex = errorHex,
            supportedPids = emptySet(), pidStates = states, isLoading = false
        )
    }

    private suspend fun buildStates(discovery: LiveDataDiscovery) {
        val segment = _uiState.value.segment
        val states = (1..32).map { offset ->
            val pidId = segment + offset
            val desc = pidNames[pidId] ?: String.format("PID 0x%02X", pidId)
            LiveDataPidState(pidId = pidId, description = desc, isSupported = pidId in discovery.supportedPids)
        }

        _uiState.value = LiveDataUiState(
            segment = segment,
            bitmapHex = discovery.rawHex,
            supportedPids = discovery.supportedPids,
            pidStates = states,
            isLoading = false
        )

        // Fetch supported PIDs in parallel
        val resultMap = coroutineScope {
            states.filter { it.isSupported }.map { s ->
                async {
                    s.pidId to activeRepo.readLiveDataPID(s.pidId)
                        .fold(
                            onSuccess = { LiveDataPidResult.Success(it) },
                            onFailure = { e -> LiveDataPidResult.Error(e.message ?: "Error") }
                        )
                }
            }.associate { it.await() }
        }

        val cur = _uiState.value
        _uiState.value = cur.copy(pidStates = cur.pidStates.map { s ->
            resultMap[s.pidId]?.let { s.copy(result = it) } ?: s
        })
    }
}
