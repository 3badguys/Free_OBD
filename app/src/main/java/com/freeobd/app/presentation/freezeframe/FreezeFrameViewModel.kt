package com.freeobd.app.presentation.freezeframe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.data.remote.NegativeResponseException
import com.freeobd.app.domain.model.FreezeFrameDiscovery
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FreezeFrameViewModel(
    private val obdRepository: OBDRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val activeRepo get() = DemoModeState.current ?: obdRepository

    private val _uiState = MutableStateFlow(FreezeFrameUiState())
    val uiState: StateFlow<FreezeFrameUiState> = _uiState.asStateFlow()

    private var pidNames: Map<Int, String> = emptyMap()

    /**
     * When navigating frames, the target frame number is staged here.
     * Only committed to [FreezeFrameUiState.frameNumber] after a successful request.
     * null means "no pending frame change" — use current frameNumber.
     */
    private var pendingFrame: Int? = null

    fun onEvent(event: FreezeFrameEvent) {
        when (event) {
            FreezeFrameEvent.Load -> loadFreezeFrame(targetFrame = null)
            is FreezeFrameEvent.SelectSegment -> {
                _uiState.value = _uiState.value.copy(
                    segment = event.segment, isLoading = true
                )
                loadFreezeFrame(targetFrame = null)
            }
            FreezeFrameEvent.PrevFrame -> {
                val prev = (_uiState.value.frameNumber - 1).coerceAtLeast(0)
                pendingFrame = prev
                _uiState.value = _uiState.value.copy(isLoading = true)
                loadFreezeFrame(targetFrame = prev)
            }
            FreezeFrameEvent.NextFrame -> {
                val next = _uiState.value.frameNumber + 1
                pendingFrame = next
                _uiState.value = _uiState.value.copy(isLoading = true)
                loadFreezeFrame(targetFrame = next)
            }
            is FreezeFrameEvent.ScrollToPid -> {
                _uiState.value = _uiState.value.copy(scrollToPid = event.pidId)
            }
        }
    }

    fun onScrollConsumed() {
        _uiState.value = _uiState.value.copy(scrollToPid = null)
    }

    /**
     * @param targetFrame Frame number to query. null = use current frameNumber (Load / segment switch).
     */
    private fun loadFreezeFrame(targetFrame: Int?) {
        viewModelScope.launch {
            val state = _uiState.value
            val queryFrame = targetFrame ?: state.frameNumber

            if (pidNames.isEmpty()) loadPidNames()

            activeRepo.discoverFreezeFramePIDs(state.segment, queryFrame).fold(
                onSuccess = { discovery ->
                    // Request succeeded — commit the frame number if it was a pending change.
                    // hasMoreFrames: reset to true only on frame navigation (targetFrame != null);
                    // preserve current value for Load / segment switch.
                    val committed = targetFrame ?: state.frameNumber
                    val moreFrames = if (targetFrame != null) true else state.hasMoreFrames
                    pendingFrame = null
                    buildStates(discovery, committed, moreFrames)
                },
                onFailure = { error ->
                    pendingFrame = null
                    if (targetFrame != null) {
                        // Frame navigation failed — stay on current frame, disable forward
                        _uiState.value = state.copy(
                            isLoading = false,
                            hasMoreFrames = false
                        )
                    } else {
                        // Initial load or segment switch failed — show all red blocks
                        buildErrorStates(state.segment, error)
                    }
                }
            )
        }
    }

    private fun buildErrorStates(segment: Int, error: Throwable) {
        val errorHex = (error as? NegativeResponseException)?.toHexString() ?: "ERR"
        val states = (1..32).map { offset ->
            val pidId = segment + offset
            val desc = pidNames[pidId] ?: String.format("PID 0x%02X", pidId)
            FreezeFramePidState(pidId = pidId, description = desc, isSupported = false,
                result = FreezeFramePidResult.Error("Segment not available"))
        }
        _uiState.value = FreezeFrameUiState(
            segment = segment, bitmapHex = errorHex,
            supportedPids = emptySet(), pidStates = states,
            frameNumber = _uiState.value.frameNumber,
            hasMoreFrames = _uiState.value.hasMoreFrames, isLoading = false
        )
    }

    private suspend fun buildStates(discovery: FreezeFrameDiscovery, committedFrame: Int, hasMoreFrames: Boolean) {
        val segment = _uiState.value.segment
        val states = (1..32).map { offset ->
            val pidId = segment + offset
            val desc = pidNames[pidId] ?: String.format("PID 0x%02X", pidId)
            FreezeFramePidState(
                pidId = pidId,
                description = desc,
                isSupported = pidId in discovery.supportedPids
            )
        }

        _uiState.value = FreezeFrameUiState(
            segment = segment,
            bitmapHex = discovery.rawHex,
            supportedPids = discovery.supportedPids,
            pidStates = states,
            frameNumber = committedFrame,
            hasMoreFrames = hasMoreFrames,
            isLoading = false
        )

        // Fetch supported PIDs in parallel
        val supported = states.filter { it.isSupported }
        val resultMap = coroutineScope {
            supported.map { s ->
                async {
                    s.pidId to activeRepo.readFreezeFramePID(s.pidId, committedFrame)
                        .fold(
                            onSuccess = { FreezeFramePidResult.Success(it) },
                            onFailure = { e -> FreezeFramePidResult.Error(e.message ?: "Error") }
                        )
                }
            }.associate { it.await() }
        }

        val current = _uiState.value
        _uiState.value = current.copy(
            pidStates = current.pidStates.map { s ->
                resultMap[s.pidId]?.let { s.copy(result = it) } ?: s
            }
        )
    }

    private suspend fun loadPidNames() {
        pidNames = database.pidMetadataDao().getByMode(0x01).associate {
            it.pidId to it.description
        }
    }
}
