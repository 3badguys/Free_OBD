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
