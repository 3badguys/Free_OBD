package com.freeobd.app.domain.model

/**
 * Metadata definition for a single OBD-II PID per SAE J1979.
 *
 * @property pidId The hex PID identifier (e.g. 0x0C for RPM).
 * @property mode The OBD mode this PID belongs to (01, 02, 09, etc.).
 * @property name Human-readable name (e.g. "Engine RPM").
 * @property description Detailed explanation of what this PID measures.
 * @property unit Unit of measurement (e.g. "rpm", "°C", "kPa", "%").
 * @property minValue The minimum expected value after conversion.
 * @property maxValue The maximum expected value after conversion.
 * @property formula Text description of the conversion formula (e.g. "((A*256)+B)/4").
 * @property bytesCount Number of data bytes in the response (1, 2, or 4).
 */
data class PIDDefinition(
    val pidId: Int,
    val mode: Int = 0x01,
    val name: String,
    val description: String = "",
    val unit: String = "",
    val minValue: Double = 0.0,
    val maxValue: Double = 0.0,
    val formula: String = "",
    val bytesCount: Int = 2
)
