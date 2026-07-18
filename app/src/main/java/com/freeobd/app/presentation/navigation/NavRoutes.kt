/*
 * Copyright 2026 3badguys <chuiC456@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    /** Live Data — Mode 01 PID discovery and display. */
    data object LiveData : NavRoutes("live_data")

    /** Freeze Frame data — Mode 02 PID discovery and display. */
    data object FreezeFrame : NavRoutes("freeze_frame")

    /** Diagnostic Trouble Codes (stored, pending, permanent). */
    data object DTC : NavRoutes("dtc")

    /** Vehicle information (VIN, calibration IDs, CVN). */
    data object VehicleInfo : NavRoutes("vehicle_info")

    /** Diagnostic Trouble Code reference lookup (offline, from dtc_codes.db). */
    data object DtcLookup : NavRoutes("dtc_lookup")

    /** Debug console — view raw ELM327 command/response log. */
    data object DebugConsole : NavRoutes("debug_console")
}
