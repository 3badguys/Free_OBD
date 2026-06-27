package com.freeobd.app.utils

import android.bluetooth.BluetoothDevice
import java.util.Locale

/**
 * General-purpose Kotlin extensions used across the app.
 */

// ── String Extensions ──────────────────────────────────────

/** Safely parse a string to Double, returning null on failure. */
fun String?.toDoubleOrNullSafe(): Double? = this?.toDoubleOrNull()

/** Safely parse a string to Int, returning null on failure. */
fun String?.toIntOrNullSafe(): Int? = this?.toIntOrNull()

/** Capitalize the first character of each word. */
fun String.toTitleCase(): String = this
    .split(" ")
    .joinToString(" ") { word ->
        word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

/** Pad a hex integer to 2 characters. */
fun Int.toHex2(): String = String.format("%02X", this)

/** Pad a hex integer to 4 characters. */
fun Int.toHex4(): String = String.format("%04X", this)

// ── BluetoothDevice Extensions ─────────────────────────────

/**
 * Get a user-friendly display name for a Bluetooth device.
 * Falls back to address if name is null or blank.
 */
val BluetoothDevice.displayName: String
    get() = if (name.isNullOrBlank()) address else name

/**
 * Check if this device is likely to be an OBD-II adapter based on its name.
 */
val BluetoothDevice.isObdDevice: Boolean
    get() {
        val n = name?.uppercase() ?: return false
        return n.contains("OBD") || n.contains("ELM") || n.contains("V-LINK") ||
            n.contains("VGATE") || n.contains("HC-05") || n.contains("HC-06")
    }

// ── Double Extensions ──────────────────────────────────────

/** Round to the specified number of decimal places. */
fun Double.roundTo(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return kotlin.math.round(this * factor) / factor
}

/** Format as a display string with optional unit suffix. */
fun Double.toDisplayString(unit: String = "", decimals: Int = 1): String {
    val rounded = this.roundTo(decimals)
    val integerPart = rounded.toLong()
    // Use integer display if the value is effectively an integer
    return if (rounded == integerPart.toDouble()) {
        if (unit.isNotEmpty()) "$integerPart $unit" else "$integerPart"
    } else {
        if (unit.isNotEmpty()) "$rounded $unit" else "$rounded"
    }
}

// ── Number Extensions ──────────────────────────────────────

/** Kotlin stdlib Math.pow replacement using Double extension. */
private fun Double.pow(exponent: Int): Double {
    var result = 1.0
    for (i in 0 until exponent) {
        result *= this
    }
    return result
}

/** Kotlin stdlib Math.pow replacement using Int extension. */
private fun Int.pow(exponent: Int): Double = this.toDouble().pow(exponent)
