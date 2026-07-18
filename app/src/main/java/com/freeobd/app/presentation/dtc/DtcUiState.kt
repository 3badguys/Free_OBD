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

package com.freeobd.app.presentation.dtc

import com.freeobd.app.domain.model.DTC

sealed interface DtcUiState {
    data object Loading : DtcUiState

    data class Loaded(
        val storedDTCs: List<DTC>,
        val pendingDTCs: List<DTC>,
        val permanentDTCs: List<DTC>,
        val selectedTab: DtcTab = DtcTab.STORED,
        /** Raw ELM327 hex response for each mode (for display). */
        val responseHex: Map<DtcTab, String> = emptyMap()
    ) : DtcUiState

    data object NoCodes : DtcUiState
    data object Cleared : DtcUiState
    data class Error(val message: String) : DtcUiState
}

enum class DtcTab(val label: String, val command: String) {
    STORED("Stored", "03"),
    PENDING("Pending", "07"),
    PERMANENT("Permanent", "0A")
}

sealed interface DtcEvent {
    data object LoadCodes : DtcEvent
    data object ClearCodes : DtcEvent
    data class SelectTab(val tab: DtcTab) : DtcEvent
    data class ShowDetail(val dtc: DTC) : DtcEvent
}
