package com.freeobd.app.domain.model

/**
 * Vehicle information retrieved via Mode 09 commands.
 *
 * @property vin Vehicle Identification Number (17 characters).
 * @property calibrationIds List of calibration IDs from various ECUs.
 * @property cvns List of Calibration Verification Numbers.
 * @property inServicePerformanceTracking Optional emissions-related tracking data.
 */
data class VehicleInfo(
    val vin: String? = null,
    val calibrationIds: List<CalibrationId> = emptyList(),
    val cvns: List<CalibrationVerificationNumber> = emptyList(),
    val inServicePerformanceTracking: String? = null
)

/**
 * Calibration ID assigned by the manufacturer for a specific ECU software version.
 */
data class CalibrationId(
    val ecuName: String = "ECM",
    val calibrationId: String
)

/**
 * Calibration Verification Number used to verify calibration integrity.
 */
data class CalibrationVerificationNumber(
    val ecuName: String = "ECM",
    val cvn: String
)
