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
    data class ShowPidDetail(val pidId: Int) : LiveDataEvent
}
