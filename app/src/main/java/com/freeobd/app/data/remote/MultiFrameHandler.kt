package com.freeobd.app.data.remote

/**
 * Handles ISO 15765-2 multi-frame response reassembly for OBD-II data.
 *
 * Under CAN + ATH1, the ELM327 outputs each frame (FF, CF) as a separate
 * ASCII line.  CF lines lack the OBD response header (e.g. "49 02"),
 * causing [extractPerFrameRaw] to skip them.
 *
 * [reassembleMultiFrame] operates entirely on ASCII bytes: it strips
 * the CAN ID and PCI header from each line and re-heads CF lines with
 * the OBD response header + frame sequence number so downstream
 * extraction works unchanged.
 *
 * ISO 15765-2 PCI (Protocol Control Information):
 *   - First Frame  (FF): PCI high nibble = 0x1, low nibble + next byte = total length
 *   - Consecutive Frame (CF): PCI high nibble = 0x2, low nibble = sequence number
 *   - Flow Control  (FC): PCI = 0x30
 */
class MultiFrameHandler {

    /**
     * Reassemble an ISO 15765-2 multi-frame ELM327 ASCII response (ATH1 mode).
     *
     * Example (0904 Calibration ID):
     *   Input:  7E8 10 13 49 04 01 33 33 33 39\r
     *           7E8 21 32 30 2D 36 32 4C 36\r
     *           7E8 22 2A 30 30 30 30 31 00\r
     *   Output: 49 04 01 33 33 33 39\r
     *           49 04 02 32 30 2D 36 32 4C 36\r
     *           49 04 03 2A 30 30 30 30 31 00\r
     *
     * @param rawBytes  The raw ELM327 ASCII response (space-separated hex).
     * @param command   Original OBD command hex (e.g. "0904").
     * @return Reassembled ASCII bytes with per-frame OBD headers, or null if
     *         no First Frame was detected.
     */
    fun reassembleMultiFrame(rawBytes: ByteArray, command: String, concat: Boolean = false): ByteArray? {
        val out = ByteArray(rawBytes.size + 256)
        var outPos = 0
        var foundFF = false
        var frameNum = 0

        // Pre-compute response header: e.g. command "0904" → mode=0x49, tail="04"
        val mode = command.substring(0, 2).toInt(16) + 0x40
        val cmdTail = command.substring(2)

        var pos = 0
        while (pos < rawBytes.size) {
            var lineEnd = pos
            while (lineEnd < rawBytes.size && rawBytes[lineEnd] != CR && rawBytes[lineEnd] != LF) lineEnd++
            if (lineEnd == pos) { pos = lineEnd + 1; continue }

            val pciStart = findPciToken(rawBytes, pos, lineEnd)
            if (pciStart < 0) { pos = lineEnd + 1; continue }

            val isFF = rawBytes[pciStart] == B_1
            if (isFF) foundFF = true

            var dataStart = skipHexToken(rawBytes, pciStart, lineEnd)
            if (isFF) dataStart = skipHexToken(rawBytes, dataStart, lineEnd)
            if (dataStart >= lineEnd) { pos = lineEnd + 1; continue }

            frameNum++

            if (isFF) {
                val len = lineEnd - dataStart
                rawBytes.copyInto(out, outPos, dataStart, lineEnd)
                outPos += len
            } else {
                if (!concat) outPos = writeHeader(out, outPos, mode, cmdTail, frameNum)
                val len = lineEnd - dataStart
                rawBytes.copyInto(out, outPos, dataStart, lineEnd)
                outPos += len
            }
            if (!concat) out[outPos++] = CR
            pos = lineEnd + 1
        }

        if (!foundFF) return null
        if (concat) out[outPos++] = CR
        return out.copyOf(outPos)
    }

    /** Scan a line for the PCI hex token (first token starting with '1' or '2'). */
    private fun findPciToken(data: ByteArray, start: Int, end: Int): Int {
        var i = start
        while (i < end) {
            while (i < end && data[i] == SP) i++
            if (i >= end) break
            val c = data[i]
            if ((c == B_1 || c == B_2) && i + 1 < end && isHex(data[i + 1])) return i
            i++
        }
        return -1
    }

    /** Advance past the current 2-char hex token and trailing spaces. */
    private fun skipHexToken(data: ByteArray, pos: Int, end: Int): Int {
        var i = pos + 2
        while (i < end && data[i] == SP) i++
        return minOf(i, end)
    }

    /** Write "XXYYNN" (mode + cmdTail + frameNum) into the output buffer. */
    private fun writeHeader(out: ByteArray, pos: Int, mode: Int, cmdTail: String, frameNum: Int): Int {
        var p = pos
        writeHexByte(out, p, mode); p += 2
        out[p++] = cmdTail[0].code.toByte()
        out[p++] = cmdTail[1].code.toByte()
        out[p++] = HEX[(frameNum shr 4) and 0x0F]
        out[p++] = HEX[frameNum and 0x0F]
        return p
    }

    private fun writeHexByte(out: ByteArray, pos: Int, b: Int) {
        out[pos] = HEX[(b shr 4) and 0x0F]
        out[pos + 1] = HEX[b and 0x0F]
    }

    private fun isHex(b: Byte): Boolean =
        (b in B_0..B_9) || (b in B_A..B_F) || (b in B_a..B_f)

    companion object {
        private const val CR: Byte = 0x0D
        private const val LF: Byte = 0x0A
        private const val SP: Byte = 0x20
        private const val B_0: Byte = 0x30
        private const val B_9: Byte = 0x39
        private const val B_1: Byte = 0x31
        private const val B_2: Byte = 0x32
        private const val B_A: Byte = 0x41
        private const val B_F: Byte = 0x46
        private const val B_a: Byte = 0x61
        private const val B_f: Byte = 0x66
        private val HEX = "0123456789ABCDEF".toByteArray()
    }
}
