package com.freeobd.app.presentation.vehicle

/**
 * UI state for the Vehicle Information screen.
 *
 * @property bitmapHex       Raw hex from 0900 response (e.g. "49 00 54 02").
 * @property supportedTypes  InfoType IDs the ECU reported as supported.
 * @property typeStates      Per-InfoType fetch state, in display order.
 * @property isLoading       True during initial discovery or refresh.
 * @property error           Fatal error message (e.g. 0900 itself failed).
 */
data class VehicleUiState(
    val bitmapHex: String = "",
    val supportedTypes: Set<Int> = emptySet(),
    val typeStates: List<VehicleInfoTypeState> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** InfoType to scroll to after layout (consumed on first render). */
    val scrollToInfoType: Int? = null
)

sealed interface VehicleEvent {
    /** Load / refresh all vehicle information. */
    data object Load : VehicleEvent
    /** Scroll the detail list to a specific InfoType. */
    data class ScrollToType(val infoType: Int) : VehicleEvent
}
