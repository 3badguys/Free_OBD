package com.freeobd.app.presentation.dashboard

import com.freeobd.app.domain.model.OBDData

/**
 * UI state for the live data dashboard screen.
 */
sealed interface DashboardUiState {
    /** Initial loading / waiting for data. */
    data object Loading : DashboardUiState

    /** Live data is flowing. */
    data class Active(
        val pidValues: Map<Int, OBDData>,
        val isPolling: Boolean,
        val selectedPids: Set<Int>,
        val pollingIntervalMs: Long = 250
    ) : DashboardUiState

    /** Data polling stopped. */
    data class Paused(
        val lastValues: Map<Int, OBDData>,
        val selectedPids: Set<Int>
    ) : DashboardUiState

    /** An error occurred during data polling. */
    data class Error(val message: String) : DashboardUiState
}

/**
 * User actions for the dashboard.
 */
sealed interface DashboardEvent {
    /** Start live data polling for the selected PIDs. */
    data object StartPolling : DashboardEvent

    /** Stop live data polling. */
    data object StopPolling : DashboardEvent

    /** Add a PID to the dashboard gauge grid. */
    data class AddPid(val pidId: Int) : DashboardEvent

    /** Remove a PID from the dashboard gauge grid. */
    data class RemovePid(val pidId: Int) : DashboardEvent

    /** Change the polling interval. */
    data class SetPollingInterval(val intervalMs: Long) : DashboardEvent
}
