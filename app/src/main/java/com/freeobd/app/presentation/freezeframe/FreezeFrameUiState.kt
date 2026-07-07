package com.freeobd.app.presentation.freezeframe

/**
 * Per-PID state for freeze frame display.
 *
 * @property pidId      The PID identifier (e.g. 0x0C).
 * @property description Human-readable name from metadata or fallback.
 * @property isSupported Whether this PID is in the freeze frame bitmap.
 * @property result     Fetch result (loading, success value, or error).
 */
data class FreezeFramePidState(
    val pidId: Int,
    val description: String,
    val isSupported: Boolean,
    val result: FreezeFramePidResult = FreezeFramePidResult.Loading
)

sealed interface FreezeFramePidResult {
    data object Loading : FreezeFramePidResult
    data class Success(val data: String) : FreezeFramePidResult
    data class Error(val message: String) : FreezeFramePidResult
}

/**
 * UI state for the Freeze Frame screen.
 */
data class FreezeFrameUiState(
    /** Current segment offset (0x00, 0x20, ... 0xE0). */
    val segment: Int = 0x00,
    /** Raw hex from 02XX00 response. */
    val bitmapHex: String = "",
    /** Supported PID IDs in the current segment. */
    val supportedPids: Set<Int> = emptySet(),
    /** Per-PID states for the current segment. */
    val pidStates: List<FreezeFramePidState> = emptyList(),
    /** Current freeze frame number (0 = first frame). */
    val frameNumber: Int = 0,
    /** Whether we can advance to the next frame. */
    val hasMoreFrames: Boolean = true,
    /** True during initial load. */
    val isLoading: Boolean = true,
    /** Fatal error message. */
    val error: String? = null,
    /** PID to scroll to after layout. */
    val scrollToPid: Int? = null
)

sealed interface FreezeFrameEvent {
    data object Load : FreezeFrameEvent
    /** Switch to a different bitmap segment. */
    data class SelectSegment(val segment: Int) : FreezeFrameEvent
    /** Go to previous freeze frame. */
    data object PrevFrame : FreezeFrameEvent
    /** Advance to the next freeze frame. */
    data object NextFrame : FreezeFrameEvent
    /** Scroll the detail list to a specific PID. */
    data class ScrollToPid(val pidId: Int) : FreezeFrameEvent
}
