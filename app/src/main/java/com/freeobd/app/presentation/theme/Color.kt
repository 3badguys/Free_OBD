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

package com.freeobd.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Automotive-inspired dark theme color palette for Free OBD.
 *
 * Uses deep navy backgrounds, teal/cyan accents reminiscent of
 * digital dashboards, and high-contrast red/yellow for warnings.
 */

// ── Primary palette ────────────────────────────────────────
val Primary = Color(0xFF00D4AA)          // Teal/cyan accent — primary interactive elements
val PrimaryVariant = Color(0xFF00A88A)    // Darker teal — pressed/selected state
val OnPrimary = Color(0xFF0D1B2A)         // Dark text on teal backgrounds

// ── Background palette ─────────────────────────────────────
val Background = Color(0xFF0D1B2A)        // Deep navy — main background
val Surface = Color(0xFF1B2838)           // Slightly lighter — card surfaces
val SurfaceVariant = Color(0xFF243447)    // Elevated surfaces — dialogs, sheets

// ── Text and content ───────────────────────────────────────
val OnBackground = Color(0xFFE0E6ED)      // Primary text on dark background
val OnSurface = Color(0xFFB0BEC5)         // Secondary/muted text
val OnSurfaceVariant = Color(0xFF78909C)  // Hint/disclaimer text

// ── Status colors ──────────────────────────────────────────
val StatusGreen = Color(0xFF00E676)       // Normal/OK status
val StatusYellow = Color(0xFFFFCA28)      // Warning/caution status
val StatusRed = Color(0xFFFF5252)         // Error/critical status
val StatusBlue = Color(0xFF448AFF)        // Informational status

// ── Gauge colors ───────────────────────────────────────────
val GaugeNeedle = Color(0xFFFF5252)       // Gauge needle — red for visibility
val GaugeArc = Color(0xFF00D4AA)          // Gauge arc — matches primary teal
val GaugeArcBackground = Color(0xFF2C3E50) // Gauge arc background track
val GaugeTick = Color(0xFF78909C)         // Tick marks

// ── Bluetooth status ───────────────────────────────────────
val BtConnected = Color(0xFF00E676)       // Connected — green
val BtConnecting = Color(0xFFFFCA28)      // Connecting — amber
val BtDisconnected = Color(0xFF78909C)    // Disconnected — grey
val BtScanning = Color(0xFF448AFF)        // Scanning — blue
