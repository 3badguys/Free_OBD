package com.freeobd.app.data.remote

/**
 * Parses Mode 01 PID bitmap responses per SAE J1979.
 *
 * Each query (0100, 0120, 0140...) returns 4 bytes (32 bits) of data.
 * Each bit from MSB of byte 1 to LSB of byte 4 indicates support for
 * PIDs offset+1 through offset+32.
 *
 * Example: 0100 response = 0xBE1FA813
 *   Byte 1 (0xBE) = bits for PIDs 0x01-0x08
 *   Byte 2 (0x1F) = bits for PIDs 0x09-0x10
 *   Byte 3 (0xA8) = bits for PIDs 0x11-0x18
 *   Byte 4 (0x13) = bits for PIDs 0x19-0x20
 *
 * If bit N is set, PID (offset + N) is supported.
 */
object PIDBitmapParser {

    private const val BITS_PER_BYTE = 8
    private const val BYTES_PER_RESPONSE = 4
    private const val PIDS_PER_GROUP = BYTES_PER_RESPONSE * BITS_PER_BYTE // 32

    /**
     * Parse a 4-byte bitmap response into a set of supported PID IDs.
     *
     * @param groupOffset The base PID offset (e.g. 0x00 for the first group).
     * @param dataBytes The 4-byte response data from the ECU.
     * @return Set of PID IDs that are supported by the vehicle in this group.
     */
    fun parse(groupOffset: Int, dataBytes: ByteArray): Set<Int> {
        require(dataBytes.size >= BYTES_PER_RESPONSE) {
            "Bitmap response must contain at least $BYTES_PER_RESPONSE bytes, got ${dataBytes.size}"
        }

        val supportedPids = mutableSetOf<Int>()

        for (byteIndex in 0 until BYTES_PER_RESPONSE) {
            val byte = dataBytes[byteIndex].toInt() and 0xFF
            for (bitIndex in 0 until BITS_PER_BYTE) {
                // MSB corresponds to the first PID in the byte group
                val mask = 1 shl (BITS_PER_BYTE - 1 - bitIndex)
                if ((byte and mask) != 0) {
                    val pid = groupOffset + (byteIndex * BITS_PER_BYTE) + bitIndex + 1
                    supportedPids.add(pid)
                }
            }
        }

        return supportedPids
    }

    /**
     * Check if all 4 bytes in the bitmap are zero.
     * A zero bitmap indicates no further PIDs are supported beyond this group.
     */
    fun isBitmapEmpty(dataBytes: ByteArray): Boolean {
        if (dataBytes.size < BYTES_PER_RESPONSE) return true
        return dataBytes.take(BYTES_PER_RESPONSE).all { it.toInt() == 0 }
    }

    /**
     * Get the next group offset in the discovery chain.
     * The chain follows 0x00 → 0x20 → 0x40 → 0x60 → 0x80 → 0xA0 → 0xC0.
     *
     * @return The next offset, or null if 0xC0 has been reached.
     */
    fun nextGroupOffset(currentOffset: Int): Int? {
        val next = currentOffset + PIDS_PER_GROUP
        return if (next > MAX_GROUP_OFFSET) null else next
    }

    /** Maximum group offset defined by SAE J1979 for Mode 01. */
    const val MAX_GROUP_OFFSET = 0xC0
}
