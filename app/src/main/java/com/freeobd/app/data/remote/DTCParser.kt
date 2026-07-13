package com.freeobd.app.data.remote

import com.freeobd.app.domain.model.DTC
import com.freeobd.app.domain.model.DTCCategory
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
     * @param rawData  The raw response bytes starting after the mode + count bytes.
     * @param maxCount Maximum number of DTCs to parse (from the response count byte).
     *                 Set to 0 to parse all available data.
     * @param status   The DTC status category (stored, pending, permanent).
     * @return List of parsed DTCs. Malformed codes are skipped with a log warning.
     */
    fun parse(rawData: ByteArray, maxCount: Int = 0, status: DTCStatus): List<DTC> {
        val dtcs = mutableListOf<DTC>()

        if (rawData.size < 2) return dtcs

        var offset = 0
        var parsed = 0
        while (offset + 1 < rawData.size && (maxCount == 0 || parsed < maxCount)) {
            val byte1 = rawData[offset].toInt() and 0xFF
            val byte2 = rawData[offset + 1].toInt() and 0xFF

            // Skip zero-pairs (no DTC / end of list marker)
            if (byte1 == 0 && byte2 == 0) {
                offset += 2
                continue
            }

            val dtc = parseDtcBytes(byte1, byte2, status)
            if (dtc != null) {
                dtcs.add(dtc)
                parsed++
            } else {
                android.util.Log.w("DTCParser", "Malformed DTC bytes: $byte1 $byte2")
            }

            offset += 2
        }

        return dtcs
    }

    /**
     * Format a 2-byte DTC into a human-readable code string (e.g. "P0301").
     *
     *   Bit: 15 14 13 12 11 10  9  8   7  6  5  4   3  2  1  0
     *        ├─┘ ├─┘ ├──────┘ ├──────────┘ ├──────────┘
     *        Cat  K   Hundred     Tens         Ones
     *
     *   Cat: 0=Powertrain, 1=Chassis, 2=Body, 3=Network
     *   K:   thousands digit (0-3)
     */
    fun formatDtcCode(byte1: Int, byte2: Int): String {
        val cat = when ((byte1 shr 6) and 0x03) {
            0 -> "P"; 1 -> "C"; 2 -> "B"; 3 -> "U"; else -> "?"
        }
        val d1 = (byte1 shr 4) and 0x03
        val d2 = (byte1 and 0x0F).toString(16).uppercase()
        val d3 = ((byte2 shr 4) and 0x0F).toString(16).uppercase()
        val d4 = (byte2 and 0x0F).toString(16).uppercase()
        return "$cat$d1$d2$d3$d4"
    }

    /**
     * Parse a single 2-byte DTC pair into a DTC object.
     *
     * @return Parsed DTC, or null if the bytes represent an invalid code.
     */
    private fun parseDtcBytes(byte1: Int, byte2: Int, status: DTCStatus): DTC? {
        val code = formatDtcCode(byte1, byte2)
        if (code[0] == '?' || code.length != 5) return null

        val category = when ((byte1 shr 6) and 0x03) {
            0 -> DTCCategory.POWERTRAIN
            1 -> DTCCategory.CHASSIS
            2 -> DTCCategory.BODY
            3 -> DTCCategory.NETWORK
            else -> return null
        }

        return DTC(
            code = code,
            category = category,
            status = status
        )
    }

    /**
     * Extract the DTC count from a raw ELM327 hex response (ASCII text).
     * Searches for the mode response marker (0x43–0x4A) and reads the
     * following byte as the DTC count. Works on both CAN and non-CAN protocols.
     */
    fun extractDtcCount(rawBytes: ByteArray): Int {
        val text = String(rawBytes, Charsets.US_ASCII).replace(">", "").trim()
        val hex = text.replace(" ", "")
        for (i in hex.indices step 2) {
            if (i + 4 > hex.length) break
            val b = hex.substring(i, i + 2).toIntOrNull(16) ?: continue
            if (b in 0x43..0x4A) {
                val countByte = hex.substring(i + 2, i + 4).toIntOrNull(16) ?: continue
                return (countByte and 0xFF).coerceIn(0, 255)
            }
        }
        return 0
    }
}
