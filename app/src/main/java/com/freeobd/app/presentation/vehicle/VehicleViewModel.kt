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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.data.local.entity.PidMetadataEntity
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VehicleViewModel(
    private val obdRepository: OBDRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val activeRepo get() = DemoModeState.current ?: obdRepository

    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    /** InfoType metadata loaded from pid_definitions.json (mode 9). */
    private var infoTypeMeta: Map<Int, VehicleInfoTypeMeta> = emptyMap()

    private val _selectedPidMetadata = MutableStateFlow<PidMetadataEntity?>(null)
    val selectedPidMetadata: StateFlow<PidMetadataEntity?> = _selectedPidMetadata.asStateFlow()

    fun onEvent(event: VehicleEvent) {
        when (event) {
            VehicleEvent.Load -> load()
            is VehicleEvent.ScrollToType -> {
                _uiState.value = _uiState.value.copy(scrollToInfoType = event.infoType)
            }
            is VehicleEvent.ShowInfoTypeDetail -> {
                _selectedPidMetadata.value = infoTypeMeta[event.infoType]?.entity
            }
        }
    }

    fun dismissPidDetail() {
        _selectedPidMetadata.value = null
    }

    fun onScrollConsumed() {
        _uiState.value = _uiState.value.copy(scrollToInfoType = null)
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = VehicleUiState(isLoading = true)

            // Load metadata once from DB
            if (infoTypeMeta.isEmpty()) loadInfoTypeMeta()

            // Step 1: Discover supported InfoTypes via 0900
            val discovery = activeRepo.discoverVehicleInfoTypes().getOrElse { error ->
                _uiState.value = VehicleUiState(
                    isLoading = false,
                    error = error.message ?: "Failed to query vehicle info types"
                )
                return@launch
            }

            // Step 2: Build type states from DB metadata + bitmap result
            val initialStates = buildTypeStates(discovery.supportedTypes)

            _uiState.value = VehicleUiState(
                bitmapHex = discovery.rawHex,
                supportedTypes = discovery.supportedTypes,
                typeStates = initialStates,
                isLoading = false
            )

            // Step 3: Fetch each supported InfoType in parallel
            val supportedMetas = initialStates.filter { it.isSupported }
            if (supportedMetas.isEmpty()) return@launch

            val resultMap = coroutineScope {
                supportedMetas.map { state ->
                    async {
                        state.meta.infoType to activeRepo.readVehicleInfoType(state.meta.infoType)
                            .fold(
                                onSuccess = { VehicleInfoTypeResult.Success(it) },
                                onFailure = { e -> VehicleInfoTypeResult.Error(e.message ?: "Error") }
                            )
                    }
                }.associate { it.await() }
            }

            val current = _uiState.value
            _uiState.value = current.copy(
                typeStates = current.typeStates.map { s ->
                    resultMap[s.meta.infoType]?.let { s.copy(result = it) } ?: s
                }
            )
        }
    }

    /**
     * Build the full InfoType list (0x01–0x20) from DB metadata + bitmap.
     * Types not found in the DB get a "Reserved (0xXX)" fallback.
     */
    private fun buildTypeStates(supported: Set<Int>): List<VehicleInfoTypeState> {
        return (1..0x20).map { infoType ->
            val meta = infoTypeMeta[infoType] ?: VehicleInfoTypeMeta(
                infoType = infoType,
                command = String.format("09%02X", infoType),
                description = String.format("PID 0x%02X", infoType)
            )
            VehicleInfoTypeState(
                meta = meta,
                isSupported = infoType in supported,
                result = if (infoType in supported) VehicleInfoTypeResult.Loading
                else VehicleInfoTypeResult.Error("Not supported by ECU")
            )
        }
    }

    /** Load Mode 09 InfoType metadata from pid_definitions.json via Room. */
    private suspend fun loadInfoTypeMeta() {
        infoTypeMeta = database.pidMetadataDao().getByMode(0x09).associate { entity ->
            entity.pidId to VehicleInfoTypeMeta(
                infoType = entity.pidId,
                command = String.format("09%02X", entity.pidId),
                description = entity.description,
                entity = entity
            )
        }
    }
}
