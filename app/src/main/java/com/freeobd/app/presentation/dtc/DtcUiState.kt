package com.freeobd.app.presentation.dtc

import com.freeobd.app.domain.model.DTC

/**
 * UI state for the Diagnostic Trouble Codes screen.
 */
sealed interface DtcUiState {
    /** Loading DTC data from the vehicle. */
    data object Loading : DtcUiState

    /** DTC data loaded. */
    data class Loaded(
        val storedDTCs: List<DTC>,
        val pendingDTCs: List<DTC>,
        val permanentDTCs: List<DTC>,
        val selectedTab: DtcTab = DtcTab.STORED
    ) : DtcUiState

    /** No DTCs found — clean bill of health. */
    data object NoCodes : DtcUiState

    /** Codes have been successfully cleared. */
    data object Cleared : DtcUiState

    /** An error occurred. */
    data class Error(val message: String) : DtcUiState
}

enum class DtcTab(val label: String) {
    STORED("Stored"),
    PENDING("Pending"),
    PERMANENT("Permanent")
}

/**
 * User actions for the DTC screen.
 */
sealed interface DtcEvent {
    /** Load DTCs from all modes. */
    data object LoadCodes : DtcEvent

    /** Clear all stored DTCs. */
    data object ClearCodes : DtcEvent

    /** Switch between stored/pending/permanent tabs. */
    data class SelectTab(val tab: DtcTab) : DtcEvent

    /** View details for a specific DTC. */
    data class ShowDetail(val dtc: DTC) : DtcEvent
}
