package com.freeobd.app.data.remote

import com.freeobd.app.domain.model.DTC
import com.freeobd.app.domain.model.DTCCategory
import com.freeobd.app.domain.model.DTCSeverity
import com.freeobd.app.domain.model.DTCStatus

/**
 * Parses Diagnostic Trouble Code data from Mode 03, Mode 07, and Mode 0A responses.
 *
 * Per SAE J2012, each DTC occupies 2 bytes:
 *   - The first 2 bits of byte 1 encode the category (P/B/C/U)
 *   - The remaining 14 bits encode the 4-digit numeric portion
 *
 * Response format (Mode 03):
 *   43 [count] [dtc1_byte1] [dtc1_byte2] [dtc2_byte1] [dtc2_byte2] ...
 *
 * DTC decoding:
 *   Bits A7-A6 of byte 1:
 *     00 = P (Powertrain)
 *     01 = C (Chassis)
 *     10 = B (Body)
 *     11 = U (Network)
 *   Bits A5-A4 of byte 1 = first digit (0-3)
 *   Bits A3-A0 of byte 1 = second digit (0-9, hex)
 *   Bits B7-B4 of byte 2 = third digit (0-9, hex)
 *   Bits B3-B0 of byte 2 = fourth digit (0-9, hex)
 */
object DTCParser {

    /**
     * Parse a raw Mode 03/07/0A response into a list of DTCs.
     *
     * @param rawData The raw response bytes starting after the mode + count bytes.
     * @param status The DTC status category (stored, pending, permanent).
     * @return List of parsed DTCs. Malformed codes are skipped with a log warning.
     */
    fun parse(rawData: ByteArray, status: DTCStatus): List<DTC> {
        val dtcs = mutableListOf<DTC>()

        if (rawData.size < 2) return dtcs

        // Walk through the data 2 bytes at a time
        var offset = 0
        while (offset + 1 < rawData.size) {
            val byte1 = rawData[offset].toInt() and 0xFF
            val byte2 = rawData[offset + 1].toInt() and 0xFF

            // Skip zero-pairs (end of list marker on some ECUs)
            if (byte1 == 0 && byte2 == 0) {
                offset += 2
                continue
            }

            val dtc = parseDtcBytes(byte1, byte2, status)
            if (dtc != null) {
                dtcs.add(dtc)
            } else {
                android.util.Log.w("DTCParser", "Malformed DTC bytes: $byte1 $byte2")
            }

            offset += 2
        }

        return dtcs
    }

    /**
     * Parse a single 2-byte DTC pair into a DTC object.
     *
     * @return Parsed DTC, or null if the bytes represent an invalid code.
     */
    private fun parseDtcBytes(byte1: Int, byte2: Int, status: DTCStatus): DTC? {
        val categoryBits = (byte1 shr 6) and 0x03
        val firstDigit = ((byte1 shr 4) and 0x03).toString()
        val secondDigit = (byte1 and 0x0F).toString(16).uppercase()
        val thirdDigit = ((byte2 shr 4) and 0x0F).toString(16).uppercase()
        val fourthDigit = (byte2 and 0x0F).toString(16).uppercase()

        val category = when (categoryBits) {
            0 -> DTCCategory.POWERTRAIN
            1 -> DTCCategory.CHASSIS
            2 -> DTCCategory.BODY
            3 -> DTCCategory.NETWORK
            else -> return null // Invalid category
        }

        val code = "${category.code}$firstDigit$secondDigit$thirdDigit$fourthDigit"

        // Basic validation: code should be 5 characters (letter + 4 hex digits)
        if (code.length != 5) return null

        return DTC(
            code = code,
            category = category,
            severity = inferSeverity(code),
            status = status
        )
    }

    /**
     * Infer a rough severity level from the DTC code prefix.
     *
     * This is a heuristic — actual severity depends on vehicle context.
     * - P0xxx = Generic powertrain (medium by default)
     * - P1xxx = Manufacturer-specific (low by default)
     * - P2xxx = Generic powertrain
     * - P3xxx = Manufacturer-specific
     * - U0xxx = Network communication (high — may indicate wiring issues)
     */
    private fun inferSeverity(code: String): DTCSeverity {
        if (code.length < 3) return DTCSeverity.MEDIUM

        return when {
            // Network codes are often serious (wiring, module failures)
            code.startsWith("U0") -> DTCSeverity.HIGH
            // Generic powertrain codes are well-defined, medium priority
            code.startsWith("P0") || code.startsWith("P2") -> DTCSeverity.MEDIUM
            // Manufacturer-specific codes vary widely; flag as low until description lookup
            code[0] == 'P' -> DTCSeverity.LOW
            // Chassis and body codes
            else -> DTCSeverity.MEDIUM
        }
    }

    /**
     * Try to extract the DTC count from a Mode 03 response header.
     * The count byte indicates how many DTCs follow (raw format: DTC_COUNT = byte - 0x40).
     */
    fun extractDtcCount(responseHeader: ByteArray): Int {
        if (responseHeader.isEmpty()) return 0
        // For CAN protocol: 43 xx yy zz, where the count is encoded
        val count = responseHeader[0].toInt() and 0xFF
        // On CAN, the actual count = (response byte) - 0x40
        // Each DTC is 2 bytes, so we can validate against data length
        return if (count > 0x40) count - 0x40 else count
    }
}
