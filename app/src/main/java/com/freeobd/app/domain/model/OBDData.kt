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
 * Sealed hierarchy representing all types of OBD-II data values.
 */
sealed interface OBDData {

    /** Numeric sensor reading with a unit. */
    data class Numeric(
        val value: Double,
        val unit: String,
        val pidId: Int
    ) : OBDData

    /** String value (e.g. VIN, calibration ID). */
    data class StringValue(
        val value: String,
        val pidId: Int
    ) : OBDData

    /** Boolean status flag. */
    data class Flag(
        val description: String,
        val isActive: Boolean,
        val pidId: Int
    ) : OBDData

    /** Raw byte array for non-numeric PIDs (bit-fields, enums, DTCs). */
    data class RawBytes(
        val bytes: ByteArray,
        val pidId: Int
    ) : OBDData

    /** Bitmap representing support status for a group of PIDs. */
    data class Bitmap(
        val supportedPids: Set<Int>,
        val mode: Int
    ) : OBDData

    /** Represents an unavailable or unsupported PID. */
    data object Unavailable : OBDData
}
