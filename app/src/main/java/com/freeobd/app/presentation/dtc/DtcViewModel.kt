package com.freeobd.app.presentation.dtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.domain.model.DTC
import com.freeobd.app.domain.usecase.ReadDTCUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Diagnostic Trouble Codes screen.
 */
class DtcViewModel(
    private val readDTCUseCase: ReadDTCUseCase
) : ViewModel() {

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

            val aggregate = readDTCUseCase()

            if (aggregate.isEmpty) {
                _uiState.value = DtcUiState.NoCodes
            } else {
                _uiState.value = DtcUiState.Loaded(
                    storedDTCs = aggregate.stored,
                    pendingDTCs = aggregate.pending,
                    permanentDTCs = aggregate.permanent,
                    selectedTab = DtcTab.STORED
                )
            }
        }
    }

    private fun clearCodes() {
        viewModelScope.launch {
            _uiState.value = DtcUiState.Loading

            readDTCUseCase.clear().fold(
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
