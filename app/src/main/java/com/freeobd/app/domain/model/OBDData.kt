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

    /** Bitmap representing support status for a group of PIDs. */
    data class Bitmap(
        val supportedPids: Set<Int>,
        val mode: Int
    ) : OBDData

    /** Represents an unavailable or unsupported PID. */
    data object Unavailable : OBDData
}
