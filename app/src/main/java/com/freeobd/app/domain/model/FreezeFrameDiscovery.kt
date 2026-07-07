package com.freeobd.app.domain.model

/**
 * Result of Mode 02 PID 00 (Freeze Frame PID bitmap discovery).
 *
 * @property rawHex Raw ELM327 hex response (e.g. "42 00 BE 1F A8 13").
 * @property supportedPids Set of supported PID IDs in the current freeze frame.
 */
data class FreezeFrameDiscovery(
    val rawHex: String,
    val supportedPids: Set<Int>
)
