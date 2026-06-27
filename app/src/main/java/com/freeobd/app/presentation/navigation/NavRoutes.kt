package com.freeobd.app.presentation.navigation

/**
 * Sealed class defining all navigation routes in the app.
 *
 * Uses a simple string-based route scheme with Compose Navigation.
 */
sealed class NavRoutes(val route: String) {
    /** Bluetooth connection & device scanning screen. */
    data object Bluetooth : NavRoutes("bluetooth")

    /** Live data dashboard with configurable gauges. */
    data object Dashboard : NavRoutes("dashboard")

    /** Diagnostic Trouble Codes (stored, pending, permanent). */
    data object DTC : NavRoutes("dtc")

    /** Vehicle information (VIN, calibration IDs, CVN). */
    data object VehicleInfo : NavRoutes("vehicle_info")
}
