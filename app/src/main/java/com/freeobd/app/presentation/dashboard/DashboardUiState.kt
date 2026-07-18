/*
 * Copyright 2026 3badguys <chuiC456@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
