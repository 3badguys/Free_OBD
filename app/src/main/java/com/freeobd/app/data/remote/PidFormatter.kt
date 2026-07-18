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

    /**
     * Compute the numeric value from PID data bytes using SAE J1979 formulas.
     * For non-numeric PIDs (bit-fields, enums) returns [OBDData.RawBytes]
     * so the formatting layer can parse individual fields.
     */
    fun compute(pidId: Int, data: ByteArray, unit: String): OBDData {
        val a = (data.getOrNull(0)?.toInt()?.and(0xFF) ?: 0)
        val b = (data.getOrNull(1)?.toInt()?.and(0xFF) ?: 0)
        val c = (data.getOrNull(2)?.toInt()?.and(0xFF) ?: 0)
        val d = (data.getOrNull(3)?.toInt()?.and(0xFF) ?: 0)

        return when (pidId) {
            // Non-numeric PIDs — keep raw bytes for structured formatting
            0x01, 0x02, 0x03, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B ->
                OBDData.RawBytes(bytes = data, pidId = pidId)

            // 1-byte — percentage: A*100/255
            0x04, 0x11, 0x2F, 0x2C, 0x2E, 0x45, 0x47, 0x49, 0x4A, 0x4C, 0x5A ->
                OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)
            // 1-byte — temperature: A-40
            0x05, 0x0F, 0x46, 0x5C ->
                OBDData.Numeric(a - 40.0, unit, pidId)
            // 1-byte — timing: A/2-64
            0x0E -> OBDData.Numeric(a / 2.0 - 64.0, unit, pidId)
            // 1-byte — linear
            0x0A -> OBDData.Numeric(a * 3.0, unit, pidId)
            0x0B, 0x0D, 0x33, 0x30 -> OBDData.Numeric(a.toDouble(), unit, pidId)
            // 1-byte — fuel trim: (A/1.28)-100
            0x06, 0x07, 0x08, 0x09 ->
                OBDData.Numeric(a / 1.28 - 100.0, unit, pidId)
            // 2-byte — (A*256+B)/4
            0x0C -> OBDData.Numeric(((a * 256) + b) / 4.0, unit, pidId)
            // 2-byte — (A*256+B)/100
            0x10, 0x43 -> OBDData.Numeric(((a * 256) + b) / 100.0, unit, pidId)
            // 2-byte — (A*256+B) raw
            0x1F, 0x21, 0x31 -> OBDData.Numeric(((a * 256) + b).toDouble(), unit, pidId)
            // 2-byte — specific multipliers
            0x22 -> OBDData.Numeric(((a * 256) + b) * 0.079, unit, pidId)
            0x23 -> OBDData.Numeric(((a * 256) + b) * 10.0, unit, pidId)
            0x42 -> OBDData.Numeric(((a * 256) + b) / 1000.0, unit, pidId)
            0x5E -> OBDData.Numeric(((a * 256) + b) / 20.0, unit, pidId)
            // Default: big-endian unsigned int
            else -> {
                var result = 0L
                for (i in data.indices) {
                    result = (result shl 8) or (data[i].toLong() and 0xFF)
                }
                OBDData.Numeric(result.toDouble(), unit, pidId)
            }
        }
    }

    suspend fun format(data: OBDData, dao: DtcDao? = null): String = when (data) {
        is OBDData.RawBytes -> when (data.pidId) {
            0x01 -> formatMonitorStatus(data.bytes)
            0x02 -> {
                val code = formatDtcCode(data.bytes)
                if (dao != null) enrichDescription(code, dao) else code
            }
            0x03 -> formatFuelSystemStatus(data.bytes)
            0x13 -> formatOxygenSensorPresence(data.bytes)
            in 0x14..0x1B -> formatOxygenSensorData(data.pidId, data.bytes)
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

    private fun formatOxygenSensorPresence(bytes: ByteArray): String {
        val raw = bytes[0].toInt() and 0xFF
        if (raw == 0) return "None"
        val sensors = mutableListOf<String>()
        for (bank in 1..2) {
            for (sensor in 1..4) {
                val bit = ((bank - 1) * 4) + (sensor - 1)
                if ((raw shr bit) and 1 == 1) sensors.add("B${bank}S$sensor")
            }
        }
        return sensors.joinToString(" ")
    }

    private fun formatOxygenSensorData(pidId: Int, bytes: ByteArray): String {
        if (bytes.size < 2) return bytes.joinToString(" ") { String.format("%02X", it) }
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        val voltage = a / 200.0
        val sb = StringBuilder()
        sb.append(String.format("%.3fV", voltage))
        if (b != 0xFF) {
            val stft = (b - 128) * 100.0 / 128.0
            sb.append("  STFT: ${String.format("%.1f", stft)}%")
        }
        return sb.toString()
    }

    private fun formatNumeric(value: Double): String = when {
        value >= 1000 -> String.format("%.0f", value)
        value >= 100 -> String.format("%.0f", value)
        else -> String.format("%.1f", value)
    }
}
