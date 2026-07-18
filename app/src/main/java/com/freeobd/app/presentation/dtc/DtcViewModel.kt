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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.domain.model.DTC
import com.freeobd.app.domain.model.DTCStatus
import com.freeobd.app.domain.repository.OBDRepository
import com.freeobd.app.domain.usecase.ReadDTCUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Diagnostic Trouble Codes screen.
 */
class DtcViewModel(
    private val readDTCUseCase: ReadDTCUseCase,
    private val obdRepository: OBDRepository
) : ViewModel() {

    private val activeRepo get() = DemoModeState.current ?: obdRepository

    private val _uiState = MutableStateFlow<DtcUiState>(DtcUiState.Loading)
    val uiState: StateFlow<DtcUiState> = _uiState.asStateFlow()

    // Detail dialog state
    private val _selectedDtc = MutableStateFlow<DTC?>(null)
    val selectedDtc: StateFlow<DTC?> = _selectedDtc.asStateFlow()

    fun onEvent(event: DtcEvent) {
        when (event) {
            DtcEvent.LoadCodes -> loadCodes()
            DtcEvent.ClearCodes -> clearCodes()
            is DtcEvent.SelectTab -> selectTab(event.tab)
            is DtcEvent.ShowDetail -> {
                _selectedDtc.value = event.dtc
            }
        }
    }

    fun clearSelectedDtc() {
        _selectedDtc.value = null
    }

    private fun loadCodes() {
        viewModelScope.launch {
            _uiState.value = DtcUiState.Loading

            val repo = activeRepo
            val (stored, storedHex) = activeRepo.readDtcWithHex("03", DTCStatus.STORED)
            val (pending, pendingHex) = activeRepo.readDtcWithHex("07", DTCStatus.PENDING)
            val (permanent, permanentHex) = activeRepo.readDtcWithHex("0A", DTCStatus.PERMANENT)

            if (stored.isEmpty() && pending.isEmpty() && permanent.isEmpty()) {
                _uiState.value = DtcUiState.NoCodes
            } else {
                _uiState.value = DtcUiState.Loaded(
                    storedDTCs = stored,
                    pendingDTCs = pending,
                    permanentDTCs = permanent,
                    selectedTab = DtcTab.STORED,
                    responseHex = mapOf(
                        DtcTab.STORED to storedHex,
                        DtcTab.PENDING to pendingHex,
                        DtcTab.PERMANENT to permanentHex
                    )
                )
            }
        }
    }

    private fun clearCodes() {
        viewModelScope.launch {
            _uiState.value = DtcUiState.Loading

            activeRepo.clearDTCs().fold(
                onSuccess = {
                    _uiState.value = DtcUiState.Cleared
                },
                onFailure = { error ->
                    _uiState.value = DtcUiState.Error(
                        error.message ?: "Failed to clear codes"
                    )
                }
            )
        }
    }

    private fun selectTab(tab: DtcTab) {
        val current = _uiState.value
        if (current is DtcUiState.Loaded) {
            _uiState.value = current.copy(selectedTab = tab)
        }
    }
}
