package com.freeobd.app.domain.model

/**
 * Represents a Diagnostic Trouble Code (DTC) per SAE J2012.
 *
 * @property code The human-readable code (e.g. "P0301").
 * @property description Text description of the fault.
 * @property category Top-level category: P (Powertrain), B (Body), C (Chassis), U (Network).
 * @property system Subsystem classification (e.g. "Ignition", "Fuel/Air").
 * @property severity Indicative severity level.
 * @property status Current status of the DTC (stored, pending, permanent).
 */
data class DTC(
    val code: String,
    val description: String = "Unknown code",
    val category: DTCCategory = DTCCategory.UNKNOWN,
    val system: String? = null,
    val severity: DTCSeverity = DTCSeverity.MEDIUM,
    val status: DTCStatus = DTCStatus.STORED
)

enum class DTCCategory(val code: String) {
    POWERTRAIN("P"),
    BODY("B"),
    CHASSIS("C"),
    NETWORK("U"),
    UNKNOWN("?")
}

enum class DTCSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class DTCStatus {
    STORED,
    PENDING,
    PERMANENT
}
