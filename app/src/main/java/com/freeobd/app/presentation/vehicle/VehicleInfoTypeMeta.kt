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
 * Metadata for a single Mode 09 InfoType, loaded from [PidMetadataDao].
 *
 * @param infoType The InfoType ID (0x01–0x20).
 * @param command  The OBD command string (e.g. "0902").
 * @param description Human-readable label from pid_definitions.json.
 */
data class VehicleInfoTypeMeta(
    val infoType: Int,
    val command: String,
    val description: String,
    val entity: com.freeobd.app.data.local.entity.PidMetadataEntity? = null
)

/** Per-type state combining support info and fetch result. */
data class VehicleInfoTypeState(
    val meta: VehicleInfoTypeMeta,
    val isSupported: Boolean,
    val result: VehicleInfoTypeResult = VehicleInfoTypeResult.Loading
)

/** Result of fetching a single InfoType. */
sealed interface VehicleInfoTypeResult {
    data object Loading : VehicleInfoTypeResult
    data class Success(val data: String) : VehicleInfoTypeResult
    data class Error(val message: String) : VehicleInfoTypeResult
}
