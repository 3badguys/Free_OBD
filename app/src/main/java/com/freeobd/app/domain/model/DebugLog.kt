package com.freeobd.app.domain.model

/**
 * A single debug console log entry.
 *
 * @property timestamp System time in millis when this entry was recorded.
 * @property type Whether this is a transmitted command, received response, or error.
 * @property message The log content (command string, response text, or error description).
 */
data class DebugLog(
    val id: Long,
    val timestamp: Long,
    val type: Type,
    val message: String
) {
    enum class Type { TX, RX, ERROR }
}
