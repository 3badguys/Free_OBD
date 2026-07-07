package com.freeobd.app.presentation.livedata

data class LiveDataPidState(
    val pidId: Int,
    val description: String,
    val isSupported: Boolean,
    val result: LiveDataPidResult = LiveDataPidResult.Loading
)

sealed interface LiveDataPidResult {
    data object Loading : LiveDataPidResult
    data class Success(val data: String) : LiveDataPidResult
    data class Error(val message: String) : LiveDataPidResult
}

data class LiveDataUiState(
    val segment: Int = 0x00,
    val bitmapHex: String = "",
    val supportedPids: Set<Int> = emptySet(),
    val pidStates: List<LiveDataPidState> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val scrollToPid: Int? = null
)

sealed interface LiveDataEvent {
    data object Load : LiveDataEvent
    data class SelectSegment(val segment: Int) : LiveDataEvent
    data class ScrollToPid(val pidId: Int) : LiveDataEvent
}
