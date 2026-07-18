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
    /** Show InfoType metadata detail dialog. */
    data class ShowInfoTypeDetail(val infoType: Int) : VehicleEvent
}
