package com.freeobd.app.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.domain.repository.OBDRepository
import com.freeobd.app.domain.usecase.ReadVehicleInfoUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VehicleViewModel(
    private val readVehicleInfoUseCase: ReadVehicleInfoUseCase,
    private val obdRepository: OBDRepository
) : ViewModel() {

    private val activeRepo get() = DemoModeState.current ?: obdRepository

    private val _uiState = MutableStateFlow<VehicleUiState>(VehicleUiState.Loading)
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    fun onEvent(event: VehicleEvent) {
        when (event) {
            VehicleEvent.Load -> loadVehicleInfo()
        }
    }

    private fun loadVehicleInfo() {
        viewModelScope.launch {
            _uiState.value = VehicleUiState.Loading

            activeRepo.readVehicleInfo().fold(
                onSuccess = { info ->
                    _uiState.value = VehicleUiState.Success(info)
                },
                onFailure = { error ->
                    _uiState.value = VehicleUiState.Error(
                        error.message ?: "Failed to read vehicle information"
                    )
                }
            )
        }
    }
}
