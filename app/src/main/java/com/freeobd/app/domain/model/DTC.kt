package com.freeobd.app.domain.model

/**
 * Represents a Diagnostic Trouble Code (DTC) per SAE J2012.
 *
 * @property code The human-readable code (e.g. "P0301").
 * @property description Text description of the fault.
 * @property category Top-level category: P (Powertrain), B (Body), C (Chassis), U (Network).
 * @property status Current status of the DTC (stored, pending, permanent).
 */
data class DTC(
    val code: String,
    val description: String = "Unknown code",
    val category: DTCCategory = DTCCategory.UNKNOWN,
    val status: DTCStatus = DTCStatus.STORED
)

enum class DTCCategory(val code: String) {
    POWERTRAIN("P"),
    BODY("B"),
    CHASSIS("C"),
    NETWORK("U"),
    UNKNOWN("?")
}

enum class DTCStatus {
    STORED,
    PENDING,
    PERMANENT
}
