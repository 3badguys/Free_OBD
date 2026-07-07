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
            // Route OBD mode commands (01-0A) through sendObdCommand for 7F detection.
            // AT commands (ATZ, ATSP, etc.) go through sendRaw directly.
            val isObdCommand = command.length >= 2 &&
                command[0] == '0' &&
                (command[1] in '1'..'9' || command[1] in 'A'..'F')
            val rawBytes = if (isObdCommand) {
                requireQueue().sendObdCommand(command, forceLog = true).getOrThrow()
            } else {
                requireQueue().sendRaw(command, forceLog = true).getOrThrow()
            }
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
            val rawBytes = requireQueue().sendObdCommand("01$pidHex").getOrThrow()
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

    // ── Mode 03: Stored DTCs ───────────────────────────────
    override suspend fun readStoredDTCs(): Result<List<DTC>> =
        readDTCsFromMode("03", DTCStatus.STORED)

    // ── Mode 04: Clear DTCs ────────────────────────────────
    override suspend fun clearDTCs(): Result<Unit> {
        return runCatching {
            requireQueue().sendObdCommand("04").getOrThrow()
        }
    }

    // ── Mode 01: Per-segment discovery ────────────────────

    override suspend fun discoverLiveDataPIDs(segment: Int): Result<LiveDataDiscovery> {
        return runCatching {
            val command = String.format("01%02X", segment)
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            val data = extractDataBytes(rawBytes)
            val hex = data.joinToString(" ") { String.format("%02X", it) }
            LiveDataDiscovery(
                rawHex = "41 ${String.format("%02X", segment)} $hex",
                supportedPids = parsePidBitmap(data, segment)
            )
        }
    }

    override suspend fun readLiveDataPID(pidId: Int): Result<String> {
        return runCatching {
            val rawBytes = requireQueue().sendObdCommand(String.format("01%02X", pidId)).getOrThrow()
            val parsed = parsePIDResponse(pidId, rawBytes)
            if (parsed is OBDData.Numeric) {
                "${parsed.value} ${parsed.unit}".trim()
            } else {
                "No data"
            }
        }
    }

    // ── Mode 02: Freeze Frame ──────────────────────────────
    override suspend fun readFreezeFrame(pidId: Int): Result<OBDData> {
        return runCatching {
            val pidHex = String.format("%02X", pidId)
            val rawBytes = requireQueue().sendObdCommand("02$pidHex").getOrThrow()
            parsePIDResponse(pidId, rawBytes) ?: OBDData.Unavailable
        }
    }

    override suspend fun discoverFreezeFramePIDs(segment: Int, frameNumber: Int): Result<FreezeFrameDiscovery> {
        return runCatching {
            val command = String.format("02%02X%02X", segment, frameNumber)
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            val data = extractDataBytes(rawBytes)
            val hex = data.joinToString(" ") { String.format("%02X", it) }
            FreezeFrameDiscovery(
                rawHex = "42 ${String.format("%02X", segment)} ${String.format("%02X", frameNumber)} $hex",
                supportedPids = parsePidBitmap(data, segment)
            )
        }
    }

    override suspend fun readFreezeFramePID(pidId: Int, frameNumber: Int): Result<String> {
        return runCatching {
            val pidHex = String.format("%02X", pidId)
            val frameHex = String.format("%02X", frameNumber)
            val rawBytes = requireQueue().sendObdCommand("02$pidHex$frameHex").getOrThrow()
            val parsed = parsePIDResponse(pidId, rawBytes)
            if (parsed is OBDData.Numeric) {
                "${parsed.value} ${parsed.unit}".trim()
            } else {
                "No data"
            }
        }
    }

    // ── Mode 07: Pending DTCs ──────────────────────────────
    override suspend fun readPendingDTCs(): Result<List<DTC>> =
        readDTCsFromMode("07", DTCStatus.PENDING)

    // ── Mode 09: InfoType discovery ──────────────────────

    override suspend fun discoverVehicleInfoTypes(): Result<VehicleInfoDiscovery> {
        return runCatching {
            val rawBytes = requireQueue().sendObdCommand("0900").getOrThrow()
            val data = extractDataBytes(rawBytes)
            val hex = data.joinToString(" ") { String.format("%02X", it) }
            val supported = parseInfoTypeBitmap(data)
            VehicleInfoDiscovery(
                rawHex = "49 00 $hex",
                supportedTypes = supported
            )
        }
    }

    override suspend fun readVehicleInfoType(infoType: Int): Result<String> {
        return runCatching {
            val command = String.format("09%02X", infoType)
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            val data = extractDataBytes(rawBytes)
            formatInfoTypeResult(infoType, data)
        }
    }

    /**
     * Parse the 0900 bitmap response into a set of supported InfoType IDs.
     * Bit 7 of byte 0 = InfoType 0x01, bit 6 = 0x02, etc.
     */
    private fun parseInfoTypeBitmap(data: ByteArray): Set<Int> = parsePidBitmap(data, offset = 0)

    /**
     * Format a single InfoType's data bytes into a human-readable string.
     * Type-specific formatting for known types; generic hex dump for others.
     */
    private fun formatInfoTypeResult(infoType: Int, data: ByteArray): String {
        return when (infoType) {
            0x02 -> { // VIN — ASCII text, skip count prefix byte
                val payload = if (data.size > 1) data.copyOfRange(1, data.size) else data
                String(payload, Charsets.US_ASCII).trim()
                    .replace(" ", "").ifBlank { "—" }
            }
            0x04 -> { // Calibration IDs — multi-record ASCII
                val ids = parseCalibrationIds(data)
                ids.joinToString("\n") { it.calibrationId }.ifBlank { "—" }
            }
            0x06 -> { // CVN — multi-record hex
                val cvns = parseCvns(data)
                cvns.joinToString("\n") { it.cvn }.ifBlank { "—" }
            }
            0x08 -> { // In-use performance tracking
                if (data.size <= 1) "—"
                else data.copyOfRange(1, data.size)
                    .joinToString(" ") { String.format("%02X", it) }
            }
            0x0A -> { // ECU Names — similar to calibration IDs
                val count = (data[0].toInt() and 0xFF).coerceIn(0, 16)
                if (count == 0 || data.size < 2) return "—"
                // Each record is typically 20 bytes ASCII
                val recordSize = (data.size - 1) / count
                (0 until count).map { i ->
                    val start = 1 + i * recordSize
                    val end = minOf(start + recordSize, data.size)
                    String(data.copyOfRange(start, end), Charsets.US_ASCII)
                        .trimEnd(' ', ' ')
                }.joinToString("\n").ifBlank { "—" }
            }
            else -> { // Generic: show as hex + ASCII interpretation
                val hex = data.joinToString(" ") { String.format("%02X", it) }
                val ascii = String(data, Charsets.US_ASCII)
                    .replace(" ", "").trim()
                if (ascii.isNotBlank()) "$hex  ($ascii)" else hex
            }
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
            val rawBytes = requireQueue().sendObdCommand(modeHex).getOrThrow()
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

    // ── Mode 09 response parsers ─────────────────────────

    /**
     * Parse Mode 09 PID 04 (Calibration ID) response.
     *
     * Format: [count] [16 bytes record1] [16 bytes record2] ...
     * Each record is an ASCII string, null/space padded to 16 bytes.
     */
    private fun parseCalibrationIds(data: ByteArray): List<CalibrationId> {
        if (data.isEmpty()) return emptyList()
        val count = (data[0].toInt() and 0xFF).coerceIn(0, 16)
        if (count == 0) return emptyList()

        val recordSize = 16
        val available = (data.size - 1) / recordSize
        val actual = minOf(count, available)
        if (actual == 0) return emptyList()

        val ids = mutableListOf<CalibrationId>()
        for (i in 0 until actual) {
            val start = 1 + i * recordSize
            val end = minOf(start + recordSize, data.size)
            val record = data.copyOfRange(start, end)
            // Trim trailing nulls and whitespace
            val text = String(record, Charsets.US_ASCII)
                .trimEnd(' ', ' ')
            if (text.isNotBlank()) {
                val ecuName = if (ids.isEmpty()) "ECM" else "ECM_$i"
                ids.add(CalibrationId(ecuName, text))
            }
        }
        return ids
    }

    /**
     * Parse Mode 09 PID 06 (CVN) response.
     *
     * Format: [count] [record1] [record2] ...
     * Each record is raw bytes; record size = remaining bytes / count.
     * Typical record size is 4 bytes, but some ECUs use 2.
     */
    private fun parseCvns(data: ByteArray): List<CalibrationVerificationNumber> {
        if (data.isEmpty()) return emptyList()
        val count = (data[0].toInt() and 0xFF).coerceIn(0, 16)
        if (count == 0) return emptyList()

        val remaining = data.size - 1
        if (remaining < count) return emptyList()
        val recordSize = remaining / count

        val cvns = mutableListOf<CalibrationVerificationNumber>()
        for (i in 0 until count) {
            val start = 1 + i * recordSize
            val end = start + recordSize
            val record = data.copyOfRange(start, end)
            val hex = record.joinToString("") { String.format("%02X", it) }
            if (hex.isNotBlank()) {
                val ecuName = if (cvns.isEmpty()) "ECM" else "ECM_$i"
                cvns.add(CalibrationVerificationNumber(ecuName, hex))
            }
        }
        return cvns
    }

    fun release() {
        repositoryScope.cancel()
        commandQueue?.release()
        commandQueue = null
    }
}

