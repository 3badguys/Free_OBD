package com.freeobd.app.domain.model

/**
 * The actual OBD protocol negotiated between the ELM327 adapter and the vehicle.
 *
 * Obtained via ATDP (description) and ATDPN (numeric identifier) after
 * the adapter has auto-detected or been set to a specific protocol.
 *
 * @property description Human-readable protocol name from ATDP,
 *   e.g. "ISO 15765-4 CAN (11 bit ID, 500 kbaud)".
 * @property number Single-character numeric identifier from ATDPN,
 *   e.g. "A0" for auto-detected, "1" = SAE J1850 PWM, "6" = ISO 15765-4 CAN.
 */
data class ProtocolInfo(
    val description: String,
    val number: String
)
