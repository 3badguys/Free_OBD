package com.freeobd.app.data.repository

import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.data.remote.*
import com.freeobd.app.domain.model.*
import com.freeobd.app.domain.repository.BluetoothRepository
import com.freeobd.app.domain.repository.OBDRepository
import com.freeobd.app.utils.ByteUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Implementation of [OBDRepository] using raw ELM327 AT/Mode commands.
 *
 * Gets the active transport from [BluetoothRepository] — operations will
 * fail with [IllegalStateException] if called while disconnected.
 */
class OBDRepositoryImpl(
    private val bluetoothRepository: BluetoothRepository,
    private val database: AppDatabase
) : OBDRepository {

    /** Get the active transport, throwing if not connected. */
    private val requireTransport: ObdTransport
        get() = bluetoothRepository.transport
            ?: throw IllegalStateException("Not connected to an OBD adapter")

    private var commandQueue: ObdCommandQueue? = null
    private val multiFrameHandler = MultiFrameHandler()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val supportedPids = mutableSetOf<Int>()

    /** Lazy access to the command queue, creating it if needed. */
    private fun requireQueue(): ObdCommandQueue {
        val existing = commandQueue
        if (existing != null) return existing
        val q = ObdCommandQueue(requireTransport)
        commandQueue = q
        return q
    }

    // ── Initialization ─────────────────────────────────────
    override suspend fun initELM327(
        protocol: String,
        ecuAddress: String?,
        cryptoKey: String?
    ): Result<Unit> {
        return runCatching {
            commandQueue = null
            val queue = requireQueue()
            queue.initialize()
            ELM327Initializer(queue).initialize(protocol, ecuAddress, cryptoKey).getOrThrow()
            queue.markFirstCommand()
        }
    }

    override suspend fun readVoltage(): Result<Double> {
        return runCatching {
            val rawBytes = requireQueue().sendRaw("ATRV").getOrThrow()
            val text = String(rawBytes, Charsets.US_ASCII).trim()
            // ATRV returns a voltage string like "12.5V" or "0.0V"
            val cleaned = text.replace(">", "").replace("V", "").replace("v", "").trim()
            cleaned.toDouble()
        }
    }

    override suspend fun readAdapterInfo(): Result<String> {
        return runCatching {
            val rawBytes = requireQueue().sendRaw("ATI").getOrThrow()
            String(rawBytes, Charsets.US_ASCII)
                .replace(">", "")
                .replace("\r", "")
                .replace("\n", " ")
                .trim()
        }
    }

    override suspend fun sendRawCommand(command: String): Result<String> {
        DebugLogger.tx(command)
        return runCatching {
            val rawBytes = requireQueue().sendRaw(command, forceLog = true).getOrThrow()
            val text = String(rawBytes, Charsets.US_ASCII)
                .replace(">", "")
                .replace("\r", "\n")
                .trim()
            DebugLogger.rx(text)
            text
        }.onFailure { e ->
            DebugLogger.error("$command: ${e.message ?: "unknown error"}")
        }
    }

    override suspend fun getProtocolInfo(): Result<ProtocolInfo> {
        return runCatching {
            val queue = requireQueue()

            // If protocol is still auto (not yet negotiated with the vehicle),
            // send a real OBD command to force the ELM327 to detect the protocol.
            var number = parseProtocolNumber(queue.sendRaw("ATDPN").getOrThrow())
            if (number == "A0") {
                // 0100 = Mode 01 PID 00 — query supported PIDs. All OBD-II vehicles
                // must support this, and it forces the adapter to lock onto the
                // vehicle's actual protocol.
                // markFirstCommand grants the 10s timeout needed for auto-detect
                // to try multiple protocols.
                queue.markFirstCommand()
                queue.sendRaw("0100")
                delay(500)
                number = parseProtocolNumber(queue.sendRaw("ATDPN").getOrThrow())
            }

            val description = parseProtocolDescription(queue.sendRaw("ATDP").getOrThrow())

            // Give the first OBD data command a 10s timeout.
            // Critical for K-line (ATSP3/4/5): the ELM327 performs a slow
            // 5-baud init on the first data command, which takes ~5 seconds.
            // Without this, the 3s standard timeout cuts it off mid-init.
            queue.markFirstCommand()

            ProtocolInfo(description = description, number = number)
        }
    }

    /** Parse ATDPN response — returns the protocol letter/number (e.g. "A0"). */
    private fun parseProtocolNumber(raw: ByteArray): String {
        val text = String(raw, Charsets.US_ASCII).trim()
        // ATDPN returns the protocol character optionally followed by a digit.
        // Strip echo and prompt, return the last meaningful line.
        return text.lines().lastOrNull { it.isNotBlank() }
            ?.replace(">", "")
            ?.trim()
            ?: "?"
    }

    /** Parse ATDP response — returns the human-readable description. */
    private fun parseProtocolDescription(raw: ByteArray): String {
        val text = String(raw, Charsets.US_ASCII).trim()
        return text.lines()
            .filter { it.isNotBlank() && !it.startsWith("ATDP") }
            .joinToString(" ") { it.replace(">", "").trim() }
            .ifBlank { "Unknown" }
    }

    // ── Mode 01: Current Data ──────────────────────────────
    override suspend fun readPID(pidId: Int): Result<OBDData> {
        return runCatching {
            val pidHex = String.format("%02X", pidId)
            val rawBytes = requireQueue().sendRaw("01$pidHex").getOrThrow()
            val parsed = parsePIDResponse(pidId, rawBytes)
            parsed ?: OBDData.Unavailable
        }
    }

    override suspend fun readPIDs(pidIds: List<Int>): Map<Int, OBDData> {
        val results = mutableMapOf<Int, OBDData>()
        for (pidId in pidIds) {
            readPID(pidId).onSuccess { data -> results[pidId] = data }
            delay(ObdCommandQueue.DEFAULT_INTER_COMMAND_DELAY_MS)
        }
        return results
    }

    override fun pollPIDs(pidIds: List<Int>, intervalMs: Long): Flow<Map<Int, OBDData>> {
        return flow {
            while (currentCoroutineContext().isActive) {
                val results = readPIDs(pidIds)
                if (results.isNotEmpty()) emit(results)
                delay(intervalMs)
            }
        }.flowOn(Dispatchers.IO)
    }

    // ── PID Discovery ──────────────────────────────────────
    override suspend fun discoverSupportedPIDs(mode: Int): Result<Set<Int>> {
        return runCatching {
            val discovered = mutableSetOf<Int>()
            var offset = 0x00

            while (offset <= PIDBitmapParser.MAX_GROUP_OFFSET) {
                val modeHex = String.format("%02X", mode)
                val offsetHex = String.format("%02X", offset)
                val rawBytes = requireQueue().sendRaw("$modeHex$offsetHex").getOrThrow()

                val dataBytes = extractDataBytes(rawBytes)
                if (PIDBitmapParser.isBitmapEmpty(dataBytes)) break

                discovered.addAll(PIDBitmapParser.parse(offset, dataBytes))
                offset = PIDBitmapParser.nextGroupOffset(offset) ?: break
            }

            supportedPids.clear()
            supportedPids.addAll(discovered)
            discovered
        }
    }

    // ── Mode 03: Stored DTCs ───────────────────────────────
    override suspend fun readStoredDTCs(): Result<List<DTC>> =
        readDTCsFromMode("03", DTCStatus.STORED)

    // ── Mode 04: Clear DTCs ────────────────────────────────
    override suspend fun clearDTCs(): Result<Unit> {
        return runCatching {
            requireQueue().sendRaw("04").getOrThrow()
        }
    }

    // ── Mode 02: Freeze Frame ──────────────────────────────
    override suspend fun readFreezeFrame(pidId: Int): Result<OBDData> {
        return runCatching {
            val pidHex = String.format("%02X", pidId)
            val rawBytes = requireQueue().sendRaw("02$pidHex").getOrThrow()
            parsePIDResponse(pidId, rawBytes) ?: OBDData.Unavailable
        }
    }

    // ── Mode 07: Pending DTCs ──────────────────────────────
    override suspend fun readPendingDTCs(): Result<List<DTC>> =
        readDTCsFromMode("07", DTCStatus.PENDING)

    // ── Mode 09: Vehicle Information ───────────────────────
    override suspend fun readVehicleInfo(): Result<VehicleInfo> {
        return runCatching {
            // VIN (Mode 09 PID 02)
            val vin = readOptional {
                val rawBytes = requireQueue().sendRaw("0902").getOrThrow()
                val data = extractDataBytes(rawBytes)
                // Try multi-frame reassembly first; then fall back to raw data
                // (skipping the PCI/record-number prefix byte that some ECUs prepend).
                val reassembled = multiFrameHandler.processFrame(data)
                val fromMF = reassembled?.let { String(it).trim() }
                    ?.takeIf { it.isNotBlank() && it.length >= 10 }
                fromMF ?: data.copyOfRange(1, data.size)
                    .let { String(it).trim() }
                    .takeIf { it.isNotBlank() && it.length >= 10 }
            }

            // Calibration ID (Mode 09 PID 04)
            val calId = readOptional {
                val rawBytes = requireQueue().sendRaw("0904").getOrThrow()
                val data = extractDataBytes(rawBytes)
                // Skip the PCI/record-number prefix byte
                val calData = if (data.size > 1) data.copyOfRange(1, data.size) else data
                String(calData).trim().takeIf { it.isNotBlank() && it.length > 2 }
            }

            // CVN (Mode 09 PID 06)
            val cvn = readOptional {
                val rawBytes = requireQueue().sendRaw("0906").getOrThrow()
                val data = extractDataBytes(rawBytes)
                // Skip the PCI/record-number prefix byte
                val cvnData = if (data.size > 1) data.copyOfRange(1, data.size) else data
                cvnData.joinToString("") { String.format("%02X", it) }.takeIf { it.isNotBlank() }
            }

            VehicleInfo(
                vin = vin,
                calibrationIds = calId?.let { listOf(CalibrationId("ECM", it)) } ?: emptyList(),
                cvns = cvn?.let { listOf(CalibrationVerificationNumber("ECM", it)) } ?: emptyList()
            )
        }
    }

    // ── Mode 0A: Permanent DTCs ────────────────────────────
    override suspend fun readPermanentDTCs(): Result<List<DTC>> =
        readDTCsFromMode("0A", DTCStatus.PERMANENT)

    // ── Helpers ────────────────────────────────────────────

    /**
     * Parse a Mode 01/02 PID response.
     *
     * Expected response format (CAN, with headers enabled):
     *   "41 XX YY ZZ ..." where 41 = Mode 01 response, XX = PID, YY ZZ = data bytes
     *
     * Returns null if the response is invalid or unsupported.
     */
    private fun parsePIDResponse(pidId: Int, rawBytes: ByteArray): OBDData? {
        val metadata = runBlocking { database.pidMetadataDao().getById(pidId, 0x01) }

        // Extract data bytes after the mode+PID response header
        // Format: "41 XX [data bytes]" or "42 XX [data bytes]"
        val dataBytes = extractDataBytes(rawBytes)

        if (dataBytes.isEmpty()) return null

        // Compute numeric value from the data bytes per SAE J1979 formulas
        val value = computePIDValue(pidId, dataBytes)
        val unit = metadata?.unit ?: ""

        return OBDData.Numeric(value = value, unit = unit, pidId = pidId)
    }

    /**
     * Extract the actual data bytes from a raw ELM327 response.
     *
     * The ELM327 sends responses as ASCII hex text, potentially spread across
     * multiple lines when status messages are emitted:
     *   "SEARCHING...\r41 0C 1B 88 \r\r>"   (status + data on separate lines)
     *
     * With headers enabled (ATH1), the CAN ID and DLC precede the OBD response:
     *   "18 DA F1 10 06 41 0C 1B 88 \r\r>"
     *
     * This method:
     * 1. Splits the response into lines (ELM327 uses \r as line separator)
     * 2. Tries each line as hex, skipping non-hex status messages (e.g. "SEARCHING...")
     * 3. Finds the OBD mode response marker (0x41–0x4F) to skip any CAN headers
     * 4. Skips the mode response byte + 1 subsequent byte (PID, sub-function, etc.)
     * 5. Returns the remaining data payload
     */
    private fun extractDataBytes(rawBytes: ByteArray): ByteArray {
        val rawString = String(rawBytes, Charsets.US_ASCII)

        // Process each line separately — ELM327 status messages like "SEARCHING..."
        // or "BUS INIT: OK" appear on their own lines and are not valid hex.
        // Only the actual data line contains the hex-encoded OBD response.
        val lines = rawString.split("\r", "\n").filter { it.isNotBlank() }

        for (line in lines) {
            val hexOnly = line.replace(">", "").replace(" ", "").trim()
            if (hexOnly.length < 4) continue

            val decoded = try {
                ByteUtils.fromHexString(hexOnly)
            } catch (_: Exception) {
                continue // Not valid hex (e.g. "SEARCHING...") — skip this line
            }

            val result = extractFromDecoded(decoded)
            if (result != null) return result
        }

        // Fallback: try the entire response as a single hex blob.
        // This handles adapters that don't emit line separators.
        val hexOnly = rawString
            .replace(">", "").replace("\r", "").replace("\n", "")
            .replace(" ", "").trim()

        if (hexOnly.length >= 4) {
            val decoded = try {
                ByteUtils.fromHexString(hexOnly)
            } catch (_: Exception) {
                return ByteArray(0)
            }
            val result = extractFromDecoded(decoded)
            if (result != null) return result
        }

        return ByteArray(0)
    }

    /**
     * Find the OBD mode response marker in decoded bytes and return
     * the data payload after mode + 1 subsequent byte, or null if
     * no valid response marker is found.
     */
    private fun extractFromDecoded(decoded: ByteArray): ByteArray? {
        for (i in decoded.indices) {
            val b = decoded[i].toInt() and 0xFF
            if (b in 0x41..0x4F) {
                val dataStart = i + 2
                if (dataStart < decoded.size) {
                    return decoded.copyOfRange(dataStart, decoded.size)
                }
            }
        }
        return null
    }

    /**
     * Compute the numeric value from PID data bytes using the standard SAE J1979 formulas.
     */
    private fun computePIDValue(pidId: Int, data: ByteArray): Double {
        val a = (data.getOrNull(0)?.toInt()?.and(0xFF) ?: 0)
        val b = (data.getOrNull(1)?.toInt()?.and(0xFF) ?: 0)
        val c = (data.getOrNull(2)?.toInt()?.and(0xFF) ?: 0)
        val d = (data.getOrNull(3)?.toInt()?.and(0xFF) ?: 0)

        return when (pidId) {
            // 1-byte formulas
            0x04 -> a * 100.0 / 255.0           // Engine load %
            0x05 -> a - 40.0                     // Coolant temp °C
            0x0A -> a * 3.0                      // Fuel pressure kPa
            0x0B -> a.toDouble()                 // Intake pressure kPa
            0x0D -> a.toDouble()                 // Vehicle speed km/h
            0x0F -> a - 40.0                     // Intake air temp °C
            0x11 -> a * 100.0 / 255.0            // Throttle position %
            0x2F -> a * 100.0 / 255.0            // Fuel level %
            0x33 -> a.toDouble()                 // Barometric pressure kPa
            0x46 -> a - 40.0                     // Ambient air temp °C
            0x5C -> a - 40.0                     // Oil temp °C
            // 2-byte formulas
            0x0C -> ((a * 256) + b) / 4.0        // RPM
            0x10 -> ((a * 256) + b) / 100.0      // MAF g/s
            0x1F -> ((a * 256) + b).toDouble()   // Run time seconds
            0x21 -> ((a * 256) + b).toDouble()   // MIL distance km
            // 4-byte formulas
            0x43 -> ((a * 256) + b) / 100.0      // Absolute load %
            // Default: big-endian unsigned int
            else -> {
                var result = 0L
                for (i in data.indices) {
                    result = (result shl 8) or (data[i].toLong() and 0xFF)
                }
                result.toDouble()
            }
        }
    }

    private suspend fun readDTCsFromMode(modeHex: String, status: DTCStatus): Result<List<DTC>> {
        return runCatching {
            val rawBytes = requireQueue().sendRaw(modeHex).getOrThrow()
            val dataBytes = extractDataBytes(rawBytes)
            DTCParser.parse(dataBytes, status).map { enrichDtc(it) }
        }
    }

    private suspend fun enrichDtc(dtc: DTC): DTC {
        val def = database.dtcDefinitionDao().getByCode(dtc.code) ?: return dtc
        return dtc.copy(
            description = def.description,
            system = def.system,
            severity = when (def.severity?.uppercase()) {
                "LOW" -> DTCSeverity.LOW
                "HIGH" -> DTCSeverity.HIGH
                "CRITICAL" -> DTCSeverity.CRITICAL
                else -> DTCSeverity.MEDIUM
            }
        )
    }

    private suspend fun <T> readOptional(block: suspend () -> T?): T? {
        return try { block() } catch (_: Exception) { null }
    }

    fun release() {
        repositoryScope.cancel()
        commandQueue?.release()
        commandQueue = null
        supportedPids.clear()
    }
}

