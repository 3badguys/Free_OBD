package com.freeobd.app.data.remote

import com.freeobd.app.data.local.dao.DtcDefinitionDao

/**
 * Formats raw PID numeric values into human-readable display strings
 * per SAE J1979. Used by both real OBD responses and mock data.
 */
object PidFormatter {

    fun format(pidId: Int, value: Double, unit: String): String = when (pidId) {
        0x01 -> formatMonitorStatus(value.toInt())
        0x03 -> formatFuelSystemStatus(value.toInt())
        0x02 -> formatDtcCode(value.toInt())
        else -> "${formatNumeric(value)} $unit".trim()
    }

    /** Append DTC description from database to a formatted DTC code. */
    suspend fun enrichDescription(code: String, dao: DtcDefinitionDao): String {
        val desc = dao.getByCode(code)?.description
        return if (desc != null) "$code — $desc" else code
    }

    private fun formatMonitorStatus(raw: Int): String {
        val mil = if ((raw shr 7) and 1 == 1) "MIL=ON" else "MIL=OFF"
        val count = raw and 0x7F
        return "$mil, DTCs=$count"
    }

    private fun formatFuelSystemStatus(raw: Int): String {
        val s1 = fuelSystemLabel((raw shr 8) and 0xFF)
        val s2 = fuelSystemLabel(raw and 0xFF)
        return if (s2.isNotEmpty()) "$s1 / $s2" else s1
    }

    private fun fuelSystemLabel(code: Int): String = when (code) {
        0 -> ""; 1 -> "Open loop (temp)"; 2 -> "Closed loop (O₂)"
        4 -> "Open loop (decel)"; 8 -> "Open loop (fault)"; 16 -> "Closed loop (fault)"
        else -> "?($code)"
    }

    private fun formatDtcCode(value: Int): String {
        val cat = when ((value shr 14) and 0x03) { 0 -> "P"; 1 -> "C"; 2 -> "B"; 3 -> "U"; else -> "?" }
        return "$cat${(value shr 12) and 0x03}${(value shr 8) and 0x0F}${(value shr 4) and 0x0F}${value and 0x0F}"
    }

    private fun formatNumeric(value: Double): String = when {
        value >= 1000 -> String.format("%.0f", value)
        value >= 100 -> String.format("%.0f", value)
        else -> String.format("%.1f", value)
    }
}
