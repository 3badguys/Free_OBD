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

/**
 * Crypto key generator for Shenzhen Yuming Electronics ELM327 adapters.
 *
 * These adapters require an AT+SETCRYPT handshake before they will relay OBD data.
 * The challenge value is provided in the AT+VERSION response under the "crypt:" field,
 * and changes on every connection.
 *
 * Algorithm reverse-engineered from libobd_logic.so via Ghidra.
 * See AT+SETCRYPT.md for the full analysis.
 */
object YMOBDCrypto {
    private const val MAGIC = 0x263d9a7e

    /**
     * Generate the AT+SETCRYPT key from an 8-character hex challenge string.
     *
     * @param challenge 8-character uppercase hex string (e.g. "844C10BB")
     * @return 8-character uppercase hex key (e.g. "80FFBF49")
     */
    fun generateKey(challenge: String): String {
        // Convert 8-character hex string to 32-bit unsigned integer (represented as signed Int)
        val input = challenge.toLong(16).toInt()

        // Extract four bytes (unsigned)
        val b0 = input and 0xFF
        val b1 = (input ushr 8) and 0xFF
        val b2 = (input ushr 16) and 0xFF
        val b3 = input ushr 24

        // Compute four parts according to the assembly logic
        val part1 = (b2 ushr (b0 / 0x32)) or ((MAGIC shl (b3 / 0x0c)) xor (b1 shl 2))
        val part2 = (b0 ushr (b1 / 0x3f)) or ((MAGIC shl (b3 / 0x0b)) xor (b2 ushr 1))
        val part3 = (b0 ushr (b1 / 0x2e)) or ((MAGIC shl (b0 / 0x22)) xor b3)
        val part4 = (b1 shl (b3 / 0x23)) or ((MAGIC shl (b0 / 0x31)) and 0x98f)

        // Combine bytes in correct order
        val result = ((part4 and 0xFF) shl 24) or
                     ((part3 and 0xFF) shl 16) or
                     ((part2 and 0xFF) shl 8) or
                     (part1 and 0xFF)

        // Format as 8-digit uppercase hex (truncate to 32 bits)
        return String.format("%08X", result)
    }

    /**
     * Try to extract the crypt challenge from an AT+VERSION response.
     *
     * @param response Raw response bytes from AT+VERSION command.
     * @return The 8-character hex challenge string, or null if not found.
     */
    fun extractCryptChallenge(response: ByteArray): String? {
        val text = String(response, Charsets.US_ASCII)
        val marker = "crypt:"
        val idx = text.indexOf(marker)
        if (idx < 0) return null

        val start = idx + marker.length
        val end = text.indexOfAny(charArrayOf('\r', '\n', ' '), start)
        val raw = if (end > start) text.substring(start, end) else text.substring(start)
        val hex = raw.trim()

        // Validate: must be exactly 8 hex digits
        if (hex.length != 8) return null
        if (!hex.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) return null

        return hex.uppercase()
    }

    /**
     * Check if the AT+VERSION response indicates a Yuming Electronics adapter.
     */
    fun isYumingAdapter(response: ByteArray): Boolean {
        val text = String(response, Charsets.US_ASCII)
        return text.contains("Yuming", ignoreCase = true)
    }
}
