package com.freeobd.app.data.remote

import com.freeobd.app.data.local.dao.DtcDao
import com.freeobd.app.domain.model.OBDData

/**
 * Formats raw PID data into human-readable display strings per SAE J1979.
 * Used by both real OBD responses and mock data.
 *
 * Accepts [OBDData] — [OBDData.RawBytes] for bit-field / multi-field PIDs,
 * [OBDData.Numeric] for simple sensor readings.
 *
 * Pass a [DtcDao] to enrich DTC codes (PID 0x02) with descriptions
 * from the database; pass null to skip the lookup.
 */
object PidFormatter {

    suspend fun format(data: OBDData, dao: DtcDao? = null): String = when (data) {
        is OBDData.RawBytes -> when (data.pidId) {
            0x01 -> formatMonitorStatus(data.bytes)
            0x02 -> {
                val code = formatDtcCode(data.bytes)
                if (dao != null) enrichDescription(code, dao) else code
            }
            0x03 -> formatFuelSystemStatus(data.bytes)
            else -> data.bytes.joinToString(" ") { String.format("%02X", it) }
        }
        is OBDData.Numeric -> "${formatNumeric(data.value)} ${data.unit}".trim()
        else -> "—"
    }

    private suspend fun enrichDescription(code: String, dao: DtcDao): String {
        val desc = dao.getByCode(code)?.description
        return if (desc != null) "$code — $desc" else code
    }

    private fun formatMonitorStatus(bytes: ByteArray): String {
        val raw = bytes[0].toInt() and 0xFF
        val mil = if ((raw shr 7) and 1 == 1) "MIL=ON" else "MIL=OFF"
        val count = raw and 0x7F
        return "$mil, DTCs=$count"
    }

    private fun formatFuelSystemStatus(bytes: ByteArray): String {
        val s1 = fuelSystemLabel(bytes[0].toInt() and 0xFF)
        val s2 = fuelSystemLabel(if (bytes.size > 1) bytes[1].toInt() and 0xFF else 0)
        return "$s1 / $s2"
    }

    private fun fuelSystemLabel(code: Int): String = when (code) {
        0 -> "Not used"
        1 -> "Open loop (temp)"
        2 -> "Closed loop (O₂)"
        4 -> "Open loop (decel)"
        8 -> "Open loop (fault)"
        16 -> "Closed loop (fault)"
        else -> "?($code)"
    }

    private fun formatDtcCode(bytes: ByteArray): String {
        val byte1 = bytes[0].toInt() and 0xFF
        val byte2 = bytes[1].toInt() and 0xFF
        return DTCParser.formatDtcCode(byte1, byte2)
    }

    private fun formatNumeric(value: Double): String = when {
        value >= 1000 -> String.format("%.0f", value)
        value >= 100 -> String.format("%.0f", value)
        else -> String.format("%.1f", value)
    }
}
