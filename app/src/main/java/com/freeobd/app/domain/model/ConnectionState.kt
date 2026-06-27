package com.freeobd.app.domain.model

/**
 * Represents the current state of the OBD-II adapter connection.
 */
enum class ConnectionState {
    /** No active connection and not attempting to connect. */
    DISCONNECTED,

    /** Bluetooth scanning in progress. */
    SCANNING,

    /** Attempting to establish a connection to the selected device. */
    CONNECTING,

    /** Successfully connected and OBD communication is ready. */
    CONNECTED,

    /** Gracefully disconnecting. */
    DISCONNECTING,

    /** Connection lost unexpectedly. */
    RECONNECTING
}
