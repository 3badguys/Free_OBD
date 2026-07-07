package com.freeobd.app.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VehicleViewModel(
    private val obdRepository: OBDRepository
) : ViewModel() {

    private val activeRepo get() = DemoModeState.current ?: obdRepository

    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    fun onEvent(event: VehicleEvent) {
        when (event) {
            VehicleEvent.Load -> loadVehicleInfo()
            is VehicleEvent.ScrollToType -> {
                _uiState.value = _uiState.value.copy(scrollToInfoType = event.infoType)
            }
        }
    }

    /** Called by the Screen after consuming a scroll event. */
    fun onScrollConsumed() {
        _uiState.value = _uiState.value.copy(scrollToInfoType = null)
    }

    private fun loadVehicleInfo() {
        viewModelScope.launch {
            _uiState.value = VehicleUiState(isLoading = true)

            // Step 1: Discover supported InfoTypes via 0900
            val discovery = activeRepo.discoverVehicleInfoTypes().getOrElse { error ->
                _uiState.value = VehicleUiState(
                    isLoading = false,
                    error = error.message ?: "Failed to query vehicle info types"
                )
                return@launch
            }

            // Step 2: Build initial type states
            val initialStates = VehicleInfoTypeMeta.ALL.map { meta ->
                VehicleInfoTypeState(
                    meta = meta,
                    isSupported = meta.infoType in discovery.supportedTypes,
                    result = if (meta.infoType in discovery.supportedTypes)
                        VehicleInfoTypeResult.Loading
                    else
                        VehicleInfoTypeResult.Error("Not supported by ECU")
                )
            }

            _uiState.value = VehicleUiState(
                bitmapHex = discovery.rawHex,
                supportedTypes = discovery.supportedTypes,
                typeStates = initialStates,
                isLoading = false
            )

            // Step 3: Fetch each supported InfoType in parallel.
            val supportedMetas = VehicleInfoTypeMeta.ALL.filter {
                it.infoType in discovery.supportedTypes
            }
            if (supportedMetas.isEmpty()) return@launch

            val deferred = supportedMetas.map { meta ->
                async {
                    meta.infoType to activeRepo.readVehicleInfoType(meta.infoType)
                        .fold(
                            onSuccess = { data ->
                                VehicleInfoTypeResult.Success(data)
                            },
                            onFailure = { error ->
                                VehicleInfoTypeResult.Error(
                                    error.message ?: "Unknown error"
                                )
                            }
                        )
                }
            }

            // Step 4: Merge results into state
            val results = deferred.associate { it.await() }
            val current = _uiState.value
            _uiState.value = current.copy(
                typeStates = current.typeStates.map { state ->
                    results[state.meta.infoType]?.let { result ->
                        state.copy(result = result)
                    } ?: state
                }
            )
        }
    }
}
