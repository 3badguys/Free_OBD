package com.freeobd.app.utils

/**
 * Utility functions for byte manipulation, hex conversion,
 * and OBD-II specific binary operations.
 */
object ByteUtils {

    /** Lookup table for uppercase hex characters. */
    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    /**
     * Convert a byte array to a hex string.
     *
     * @param bytes The byte array to convert.
     * @param delimiter Delimiter between bytes (default: space).
     */
    fun toHexString(bytes: ByteArray, delimiter: String = " "): String {
        val sb = StringBuilder(bytes.size * 3)
        bytes.forEachIndexed { index, byte ->
            if (index > 0) sb.append(delimiter)
            sb.append(HEX_CHARS[(byte.toInt() shr 4) and 0x0F])
            sb.append(HEX_CHARS[byte.toInt() and 0x0F])
        }
        return sb.toString()
    }

    /**
     * Parse a hex string (with optional spaces) into a byte array.
     *
     * @param hex The hex string (e.g. "41 0C 1B 88").
     * @throws IllegalArgumentException if the string contains non-hex characters.
     */
    fun fromHexString(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace("\n", "").replace("\r", "")
        require(cleaned.length % 2 == 0) {
            "Hex string must have an even number of characters, got ${cleaned.length}"
        }

        val bytes = ByteArray(cleaned.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            val byteVal = cleaned.substring(index, index + 2).toInt(16)
            bytes[i] = byteVal.toByte()
        }
        return bytes
    }

    /**
     * Convert an array of bytes to an unsigned integer value.
     * Uses big-endian ordering (MSB first).
     *
     * Used extensively for OBD data: e.g. RPM formula ((A*256)+B)/4
     * where A is byte 0 (MSB) and B is byte 1 (LSB).
     */
    fun bytesToUInt(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long {
        var result = 0L
        for (i in offset until (offset + length)) {
            result = (result shl 8) or (bytes[i].toLong() and 0xFF)
        }
        return result
    }

    /**
     * Apply the standard DTC formula to compute a human-readable code from 2 raw bytes.
     *
     * SAE J2012 DTC encoding (2 bytes):
     *   Bits A7-A6 → Category: 00=P, 01=C, 10=B, 11=U
     *   Bits A5-A4 → First digit (0-3)
     *   Bits A3-A0 → Second digit (0-F hex)
     *   Bits B7-B4 → Third digit (0-F hex)
     *   Bits B3-B0 → Fourth digit (0-F hex)
     */
    fun dtcBytesToCode(byte1: Int, byte2: Int): String {
        val categoryLetter = when ((byte1 shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            3 -> 'U'
            else -> '?'
        }
        val digit1 = ((byte1 shr 4) and 0x03)
        val digit2 = byte1 and 0x0F
        val digit3 = (byte2 shr 4) and 0x0F
        val digit4 = byte2 and 0x0F
        return "$categoryLetter$digit1${digit2.toString(16).uppercase()}${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}"
    }

    /**
     * Check if a specific bit is set in a byte.
     *
     * @param byte The byte value.
     * @param bitIndex Bit index 0-7 (0 = LSB, 7 = MSB).
     */
    fun isBitSet(byte: Int, bitIndex: Int): Boolean {
        require(bitIndex in 0..7) { "Bit index must be 0-7, got $bitIndex" }
        return (byte and (1 shl bitIndex)) != 0
    }

    /**
     * Compute a simple checksum over a byte array (sum of all bytes, low byte).
     */
    fun checksum(bytes: ByteArray): Int {
        return bytes.fold(0) { acc, byte -> (acc + (byte.toInt() and 0xFF)) and 0xFF }
    }
}
