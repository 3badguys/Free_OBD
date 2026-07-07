package com.freeobd.app.domain.model

/**
 * Result of Mode 09 PID 00 (InfoType bitmap discovery).
 *
 * @property rawHex Raw ELM327 hex response (e.g. "49 00 54 02").
 * @property supportedTypes Set of supported InfoType IDs.
 */
data class VehicleInfoDiscovery(
    val rawHex: String,
    val supportedTypes: Set<Int>
)
