package com.freeobd.app.presentation.vehicle

/**
 * Metadata and result for a single Mode 09 InfoType.
 *
 * @param infoType The InfoType ID (0x01–0x20).
 * @param command  The OBD command string (e.g. "0902").
 * @param description Human-readable label.
 */
data class VehicleInfoTypeMeta(
    val infoType: Int,
    val command: String,
    val description: String
) {
    companion object {
        /** All Mode 09 InfoTypes covered by the 0900 bitmap (0x01–0x20). */
        val ALL: List<VehicleInfoTypeMeta> = listOf(
            VehicleInfoTypeMeta(0x01, "0901", "VIN Message Count"),
            VehicleInfoTypeMeta(0x02, "0902", "VIN"),
            VehicleInfoTypeMeta(0x03, "0903", "Calibration ID Count"),
            VehicleInfoTypeMeta(0x04, "0904", "Calibration IDs"),
            VehicleInfoTypeMeta(0x05, "0905", "CVN Count"),
            VehicleInfoTypeMeta(0x06, "0906", "CVN"),
            VehicleInfoTypeMeta(0x07, "0907", "In-Use Performance Tracking"),
            VehicleInfoTypeMeta(0x08, "0908", "In-Use Performance Tracking Data"),
            VehicleInfoTypeMeta(0x09, "0909", "Reserved (0x09)"),
            VehicleInfoTypeMeta(0x0A, "090A", "ECU Name"),
            VehicleInfoTypeMeta(0x0B, "090B", "In-use Perf (Compression Ignition)"),
            VehicleInfoTypeMeta(0x0C, "090C", "Reserved (0x0C)"),
            VehicleInfoTypeMeta(0x0D, "090D", "Reserved (0x0D)"),
            VehicleInfoTypeMeta(0x0E, "090E", "Reserved (0x0E)"),
            VehicleInfoTypeMeta(0x0F, "090F", "Reserved (0x0F)"),
            VehicleInfoTypeMeta(0x10, "0910", "Reserved (0x10)"),
            VehicleInfoTypeMeta(0x11, "0911", "Reserved (0x11)"),
            VehicleInfoTypeMeta(0x12, "0912", "Reserved (0x12)"),
            VehicleInfoTypeMeta(0x13, "0913", "Reserved (0x13)"),
            VehicleInfoTypeMeta(0x14, "0914", "Reserved (0x14)"),
            VehicleInfoTypeMeta(0x15, "0915", "Reserved (0x15)"),
            VehicleInfoTypeMeta(0x16, "0916", "Reserved (0x16)"),
            VehicleInfoTypeMeta(0x17, "0917", "Reserved (0x17)"),
            VehicleInfoTypeMeta(0x18, "0918", "Reserved (0x18)"),
            VehicleInfoTypeMeta(0x19, "0919", "Reserved (0x19)"),
            VehicleInfoTypeMeta(0x1A, "091A", "Reserved (0x1A)"),
            VehicleInfoTypeMeta(0x1B, "091B", "Reserved (0x1B)"),
            VehicleInfoTypeMeta(0x1C, "091C", "Reserved (0x1C)"),
            VehicleInfoTypeMeta(0x1D, "091D", "Reserved (0x1D)"),
            VehicleInfoTypeMeta(0x1E, "091E", "Reserved (0x1E)"),
            VehicleInfoTypeMeta(0x1F, "091F", "Reserved (0x1F)"),
            VehicleInfoTypeMeta(0x20, "0920", "Reserved (0x20)"),
        )
    }
}

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
    /** Error fetching — could be 7F negative response or I/O error. */
    data class Error(val message: String) : VehicleInfoTypeResult
}
