package com.freeobd.app.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Handles ISO 15765-2 multi-frame response reassembly for OBD-II data.
 *
 * Used primarily for long responses like VIN (17+ characters), which may be split
 * across multiple CAN frames due to the 8-byte CAN frame payload limit.
 *
 * ISO 15765-2 Multi-Frame Flow:
 *
 * 1. Sender sends a First Frame (FF):
 *    - PCI byte high nibble = 0x1 (First Frame indicator)
 *    - PCI byte low nibble + next byte = total data length (12 bits)
 *    - Remaining bytes in the frame = first chunk of data
 *
 * 2. Receiver sends a Flow Control Frame (FC):
 *    - PCI byte = 0x30 (Continue To Send)
 *    - Block size (how many Consecutive Frames before next FC)
 *    - Separation time (minimum delay between CFs in ms)
 *
 * 3. Sender sends Consecutive Frames (CF):
 *    - PCI byte high nibble = 0x2 (Consecutive Frame indicator)
 *    - PCI byte low nibble = sequence number (0-15, wrapping)
 *    - Remaining bytes = next chunk of data
 *
 * This handler manages the reassembly buffer and flow control handshake.
 */
class MultiFrameHandler {

    /** Maximum number of bytes to buffer for a single multi-frame response. */
    private val maxBufferSize = 4096

    /** Buffer for accumulating frame data. */
    private val reassemblyBuffer = mutableListOf<Byte>()

    /** Expected total data length from the First Frame. */
    private var expectedLength: Int = 0

    /** Current frame sequence number for validation. */
    private var expectedSequenceNumber: Int = 0

    /** Whether a multi-frame transfer is currently in progress. */
    private var transferInProgress: Boolean = false

    /**
     * Process a frame that may be part of a multi-frame response.
     *
     * @param frameData The raw frame payload bytes (including PCI byte).
     * @return The reassembled complete data if this frame completes the transfer,
     *         or null if more frames are expected.
     */
    fun processFrame(frameData: ByteArray): ByteArray? {
        if (frameData.isEmpty()) return null

        val pci = frameData[0].toInt() and 0xFF
        val pciType = (pci shr 4) and 0x0F

        return when (pciType) {
            PCI_TYPE_SINGLE_FRAME -> {
                // Single frame — no reassembly needed
                // PCI byte low nibble = data length
                val dataLength = pci and 0x0F
                if (dataLength <= frameData.size - 1) {
                    frameData.copyOfRange(1, 1 + dataLength)
                } else {
                    frameData.copyOfRange(1, frameData.size)
                }
            }

            PCI_TYPE_FIRST_FRAME -> {
                // First Frame — start new reassembly
                val lengthHigh = pci and 0x0F
                val lengthLow = frameData[1].toInt() and 0xFF
                expectedLength = (lengthHigh shl 8) or lengthLow
                expectedSequenceNumber = 1 // CFs start at sequence 1
                transferInProgress = true
                reassemblyBuffer.clear()

                // Append data from the FF (bytes after the 2-byte header)
                reassemblyBuffer.addAll(frameData.drop(2).toList())

                // Return null — awaiting Consecutive Frames
                null
            }

            PCI_TYPE_CONSECUTIVE_FRAME -> {
                // Consecutive Frame — append data
                if (!transferInProgress) return null

                val sequenceNumber = pci and 0x0F
                if (sequenceNumber != expectedSequenceNumber) {
                    // Sequence mismatch — transfer corrupted, abort
                    android.util.Log.w(TAG,
                        "CF sequence mismatch: expected $expectedSequenceNumber, got $sequenceNumber")
                    abort()
                    return null
                }

                expectedSequenceNumber = (expectedSequenceNumber + 1) % 16
                reassemblyBuffer.addAll(frameData.drop(1).toList())

                // Check if transfer is complete
                if (reassemblyBuffer.size >= expectedLength) {
                    val complete = reassemblyBuffer.take(expectedLength).toByteArray()
                    abort() // Reset state
                    return complete
                }

                null // More frames expected
            }

            PCI_TYPE_FLOW_CONTROL -> {
                // We sent a flow control frame — this is the sender's response to it
                // Not directly used in data processing
                null
            }

            else -> {
                android.util.Log.w(TAG, "Unknown PCI type: $pciType")
                null
            }
        }
    }

    /**
     * Create a Flow Control frame to request the sender to continue sending.
     *
     * @param blockSize Number of Consecutive Frames to accept before the next FC frame.
     * @param separationTimeMs Minimum separation time between CFs in milliseconds.
     * @return The FC frame bytes ready to be sent.
     */
    fun createFlowControlFrame(
        blockSize: Int = 0, // 0 = send all remaining frames
        separationTimeMs: Int = 10
    ): ByteArray {
        return byteArrayOf(
            (PCI_TYPE_FLOW_CONTROL shl 4).toByte(), // PCI byte: FC type + 0
            blockSize.toByte(),
            separationTimeMs.toByte()
        )
    }

    /** Reset the reassembly state and clear the buffer. */
    private fun abort() {
        reassemblyBuffer.clear()
        expectedLength = 0
        expectedSequenceNumber = 0
        transferInProgress = false
    }

    /** Check if a multi-frame transfer is currently active. */
    val isTransferActive: Boolean get() = transferInProgress

    companion object {
        private const val TAG = "MultiFrameHandler"

        // PCI (Protocol Control Information) type identifiers
        private const val PCI_TYPE_SINGLE_FRAME = 0x0
        private const val PCI_TYPE_FIRST_FRAME = 0x1
        private const val PCI_TYPE_CONSECUTIVE_FRAME = 0x2
        private const val PCI_TYPE_FLOW_CONTROL = 0x3
    }
}
