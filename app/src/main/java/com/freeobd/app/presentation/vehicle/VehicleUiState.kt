package com.freeobd.app.presentation.vehicle

import com.freeobd.app.domain.model.VehicleInfo

/**
 * UI state for the Vehicle Information screen.
 */
sealed interface VehicleUiState {
    data object Loading : VehicleUiState
    data class Success(val info: VehicleInfo) : VehicleUiState
    data class Error(val message: String) : VehicleUiState
}

sealed interface VehicleEvent {
    data object Load : VehicleEvent
}
