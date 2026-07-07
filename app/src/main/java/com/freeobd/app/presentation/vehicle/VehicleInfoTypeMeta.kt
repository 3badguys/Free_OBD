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
    val description: String
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
