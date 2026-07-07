package com.freeobd.app.domain.model

/** Result of Mode 01 PID 00 (Live Data PID bitmap discovery). */
data class LiveDataDiscovery(
    val rawHex: String,
    val supportedPids: Set<Int>
)
