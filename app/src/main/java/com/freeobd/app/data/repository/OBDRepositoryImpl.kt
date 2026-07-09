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

    /** Cached protocol number — used to detect non-CAN modes (1-5) for bitmap parsing. */
    private var currentProtocolNumber: String = "A0"

    override suspend fun getProtocolInfo(): Result<ProtocolInfo> {
        return runCatching {
            val queue = requireQueue()

            // Always send 0100 first to force the ELM327 to lock onto the
            // vehicle's actual protocol before querying ATDP/ATDPN.
            // markFirstCommand grants the 10s timeout needed for auto-detect
            // and K-line 5-baud init. sendObdCommand waits for the '>' prompt,
            // so no extra delay is needed.
            queue.markFirstCommand()
            queue.sendObdCommand("0100")

            val number = parseProtocolNumber(queue.sendRaw("ATDPN").getOrThrow())
            currentProtocolNumber = number
            val description = parseProtocolDescription(queue.sendRaw("ATDP").getOrThrow())

            queue.markFirstCommand()
            ProtocolInfo(description = description, number = number)
        }
    }

    /** True if the current protocol is non-CAN (1-5), requiring bitmap offset correction. */
    /** True if the current protocol is non-CAN (1-5), which adds a count byte in Mode 09. */
    private fun isNonCanProtocol(): Boolean {
        val num = currentProtocolNumber.removePrefix("A").removePrefix("a")
        return num.toIntOrNull()?.let { it in 1..5 } == true
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
            val rawHex = rawBytes.toRawHexString()
            val rawData = extractDataBytes(rawBytes)
            // Bitmap is always 4 bytes (32 bits for 32 PIDs per segment).
            // Extra trailing bytes are padding/checksum from the ECU.
            val data = if (rawData.size > 4) rawData.copyOf(4) else rawData
            LiveDataDiscovery(
                rawHex = rawHex,
                supportedPids = parsePidBitmap(data, segment)
            )
        }
    }

    override suspend fun readLiveDataPID(pidId: Int): Result<String> {
        return runCatching {
            val rawBytes = requireQueue().sendObdCommand(String.format("01%02X", pidId)).getOrThrow()
            val parsed = parsePIDResponse(pidId, rawBytes)
            if (parsed is OBDData.Numeric) {
                val formatted = PidFormatter.format(pidId, parsed.value, parsed.unit)
                if (pidId == 0x02) PidFormatter.enrichDescription(formatted, database.dtcDefinitionDao()) else formatted
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
            parsePIDResponse(pidId, rawBytes, mode = 0x02,
                extraSkip = if (isNonCanProtocol()) 1 else 0) ?: OBDData.Unavailable
        }
    }

    override suspend fun discoverFreezeFramePIDs(segment: Int, frameNumber: Int): Result<FreezeFrameDiscovery> {
        return runCatching {
            val command = String.format("02%02X%02X", segment, frameNumber)
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            // Non-CAN Mode 02 has a frame number echo byte after the PID.
            // CAN Mode 02 handles the frame number at the protocol layer.
            val rawHex = rawBytes.toRawHexString()
            val rawData = extractDataBytes(rawBytes,
                extraSkip = if (isNonCanProtocol()) 1 else 0)
            // Bitmap is always 4 bytes. Trim trailing padding.
            val data = if (rawData.size > 4) rawData.copyOf(4) else rawData
            FreezeFrameDiscovery(
                rawHex = rawHex,
                supportedPids = parsePidBitmap(data, segment)
            )
        }
    }

    override suspend fun readFreezeFramePID(pidId: Int, frameNumber: Int): Result<String> {
        return runCatching {
            val pidHex = String.format("%02X", pidId)
            val frameHex = String.format("%02X", frameNumber)
            val rawBytes = requireQueue().sendObdCommand("02$pidHex$frameHex").getOrThrow()
            val parsed = parsePIDResponse(pidId, rawBytes, mode = 0x02,
                extraSkip = if (isNonCanProtocol()) 1 else 0)
            if (parsed is OBDData.Numeric) {
                val formatted = PidFormatter.format(pidId, parsed.value, parsed.unit)
                if (pidId == 0x02) PidFormatter.enrichDescription(formatted, database.dtcDefinitionDao()) else formatted
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
            val rawHex = rawBytes.toRawHexString()
            // Non-CAN protocols (1-5) insert a message count byte after the
            // InfoType in Mode 09 responses. extraSkip=1 strips it.
            val extraSkip = if (isNonCanProtocol()) 1 else 0
            val rawData = extractDataBytes(rawBytes, extraSkip)
            // Bitmap is always 4 bytes. Trim trailing padding.
            val data = if (rawData.size > 4) rawData.copyOf(4) else rawData
            val supported = parsePidBitmap(data, offset = 0)
            VehicleInfoDiscovery(
                rawHex = rawHex,
                supportedTypes = supported
            )
        }
    }

    override suspend fun readVehicleInfoType(infoType: Int): Result<String> {
        return runCatching {
            val command = String.format("09%02X", infoType)
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()

            when (infoType) {
                // Multi-record types: each frame is an independent record.
                // Multi-frame → each payload is a complete record.
                // Single-frame → SAE J1979 [count][padded records] format.
                0x04, 0x06, 0x0A -> {
                    extractPerFramePayloads(rawBytes)
                        .joinToString("\n") { r -> formatSingleRecord(infoType, r) }
                        .ifBlank { "—" }
                }
                0x01, 0x03, 0x05, 0x09 -> {
                    val data = extractDataBytes(rawBytes)
                    formatInfoTypeResult(infoType, data)
                }
                else -> {
                    val extraSkip = if (isNonCanProtocol()) 1 else 0
                    val data = extractDataBytes(rawBytes, extraSkip)
                    formatInfoTypeResult(infoType, data)
                }
            }
        }
    }

    /** Format a single record payload from a Mode 09 multi-record response. */
    private fun formatSingleRecord(infoType: Int, payload: ByteArray): String = when (infoType) {
        0x04, 0x0A -> // Calibration ID / ECU Name — ASCII
            String(payload, Charsets.US_ASCII).trimEnd(' ', ' ')
        0x06 -> // CVN — hex
            payload.joinToString("") { String.format("%02X", it) }
        else -> payload.joinToString(" ") { "%02X".format(it) }
    }

    /**
     * Format a single InfoType's data bytes into a human-readable string.
     * Type-specific formatting for known types; generic hex dump for others.
     */
    private fun formatInfoTypeResult(infoType: Int, data: ByteArray): String {
        if (data.isEmpty()) return "—"
        return when (infoType) {
            // single byte
            0x01, 0x03, 0x05, 0x09 -> (data[0].toInt() and 0xFF).toString()
            else -> {
                val hex = data.joinToString(" ") { String.format("%02X", it) }
                val ascii = String(data, Charsets.US_ASCII)
                    .replace(" ", "").trim()
                if (ascii.isNotBlank()) "$hex  ($ascii)" else hex
            }
        }
    }

    /** Raw ELM327 ASCII response as a trimmed string for display. */
    private fun ByteArray.toRawHexString(): String =
        String(this, Charsets.US_ASCII).replace(">", "").replace("\r", " ").trim()

    /**
     * Extract per-frame data payloads from a multi-frame Mode 09 response.
     * Each hex line is decoded; mode + InfoType + record index are stripped
     * (extraSkip=1 on top of the standard 2-byte header).
     */
    private fun extractPerFramePayloads(rawBytes: ByteArray): List<ByteArray> {
        val rawString = String(rawBytes, Charsets.US_ASCII)
        val lines = rawString.split("\r", "\n").filter { it.isNotBlank() }
        return lines.mapNotNull { line ->
            val hexOnly = line.replace(">", "").replace(" ", "").trim()
            if (hexOnly.length < 6) return@mapNotNull null
            val decoded = try {
                ByteUtils.fromHexString(hexOnly)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            extractFromDecoded(decoded, extraSkip = 1)
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
     *   Mode 01: "41 XX YY ZZ ..." where 41 = Mode 01 response, XX = PID, YY ZZ = data bytes
     *   Mode 02: "42 XX FF YY ZZ ..." where 42 = Mode 02 response, XX = PID, FF = frame number
     *
     * Returns null if the response is invalid or unsupported.
     *
     * @param pidId The PID identifier.
     * @param rawBytes Raw response bytes from the ELM327.
     * @param mode The OBD mode (0x01 or 0x02). Used for metadata lookup.
     */
    private fun parsePIDResponse(pidId: Int, rawBytes: ByteArray, mode: Int = 0x01, extraSkip: Int = 0): OBDData? {
        val metadata = runBlocking { database.pidMetadataDao().getById(pidId, mode) }

        // Extract data bytes after the mode response header.
        // Mode 02 callers pass extraSkip=1 to strip the frame number echo byte.
        val dataBytes = extractDataBytes(rawBytes, extraSkip)

        if (dataBytes.isEmpty()) return null

        // Trim to expected data length from metadata to strip padding/checksum bytes
        // that some ECUs/protocols append after the real data payload.
        val expectedLen = metadata?.bytesCount ?: dataBytes.size
        val trimmed = if (dataBytes.size > expectedLen) dataBytes.copyOf(expectedLen) else dataBytes

        // Compute numeric value from the data bytes per SAE J1979 formulas
        val value = computePIDValue(pidId, trimmed)
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
    /**
     * @param extraSkip Additional bytes to skip per line after the standard
     *   mode header. Used for protocol-specific bytes (e.g. non-CAN Mode 09
     *   message count byte) or multi-record index bytes.
     */
    private fun extractDataBytes(rawBytes: ByteArray, extraSkip: Int = 0): ByteArray {
        val rawString = String(rawBytes, Charsets.US_ASCII)

        // Process each line separately — ELM327 status messages like "SEARCHING..."
        // or "BUS INIT: OK" appear on their own lines and are not valid hex.
        // Only the actual data line contains the hex-encoded OBD response.
        val lines = rawString.split("\r", "\n").filter { it.isNotBlank() }

        // Collect data from all lines — multi-frame responses (e.g. Mode 09
        // calibration IDs / VIN) span multiple CAN frames, each with its own
        // mode+PID header. We concatenate the payload from each frame.
        val allData = mutableListOf<Byte>()
        for (line in lines) {
            val hexOnly = line.replace(">", "").replace(" ", "").trim()
            if (hexOnly.length < 4) continue
            val decoded = try {
                ByteUtils.fromHexString(hexOnly)
            } catch (_: Exception) {
                continue
            }
            extractFromDecoded(decoded, extraSkip)?.let { allData.addAll(it.toList()) }
        }
        if (allData.isNotEmpty()) return allData.toByteArray()

        // Fallback: try the entire response as a single hex blob.
        val hexOnly = rawString
            .replace(">", "").replace("\r", "").replace("\n", "")
            .replace(" ", "").trim()
        if (hexOnly.length >= 4) {
            val decoded = try {
                ByteUtils.fromHexString(hexOnly)
            } catch (_: Exception) {
                return ByteArray(0)
            }
            extractFromDecoded(decoded, extraSkip)?.let { return it }
        }

        return ByteArray(0)
    }

    /**
     * Find the OBD mode response marker in decoded bytes and return
     * the data payload, or null if no valid response marker is found.
     *
     * Always skips 2 bytes after the mode marker (mode + 1 sub-byte: PID,
     * InfoType, or count). Use [extraSkip] for additional per-frame bytes
     * such as Mode 02 frame number echo or non-CAN Mode 09 message count.
     */
    private fun extractFromDecoded(decoded: ByteArray, extraSkip: Int = 0): ByteArray? {
        for (i in decoded.indices) {
            val b = decoded[i].toInt() and 0xFF
            if (b in 0x41..0x4F) {
                val dataStart = i + 2 + extraSkip
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
            // 1-byte — percentage: A*100/255
            0x04 -> a * 100.0 / 255.0           // Engine load
            0x11 -> a * 100.0 / 255.0           // Throttle position
            0x2F -> a * 100.0 / 255.0           // Fuel level
            0x2C -> a * 100.0 / 255.0           // EGR commanded
            0x2E -> a * 100.0 / 255.0           // EVAP purge
            0x45 -> a * 100.0 / 255.0           // Relative throttle
            0x47 -> a * 100.0 / 255.0           // Throttle B
            0x49 -> a * 100.0 / 255.0           // APP D
            0x4A -> a * 100.0 / 255.0           // APP E
            0x4C -> a * 100.0 / 255.0           // Commanded throttle
            0x5A -> a * 100.0 / 255.0           // Relative APP
            // 1-byte — temperature: A-40
            0x05 -> a - 40.0                     // Coolant temp
            0x0F -> a - 40.0                     // Intake air temp
            0x46 -> a - 40.0                     // Ambient air temp
            0x5C -> a - 40.0                     // Oil temp
            // 1-byte — timing: A/2-64
            0x0E -> a / 2.0 - 64.0              // Timing advance
            // 1-byte — linear
            0x0A -> a * 3.0                      // Fuel pressure kPa
            0x0B -> a.toDouble()                 // Intake pressure kPa
            0x0D -> a.toDouble()                 // Vehicle speed km/h
            0x33 -> a.toDouble()                 // Barometric pressure kPa
            0x30 -> a.toDouble()                 // Warm-ups since clear
            // 1-byte — fuel trim: (A/1.28)-100
            0x06 -> a / 1.28 - 100.0             // STFT B1
            0x07 -> a / 1.28 - 100.0             // LTFT B1
            0x08 -> a / 1.28 - 100.0             // STFT B2
            0x09 -> a / 1.28 - 100.0             // LTFT B2
            // 2-byte — (A*256+B)/4
            0x0C -> ((a * 256) + b) / 4.0        // RPM
            // 2-byte — (A*256+B)/100
            0x10 -> ((a * 256) + b) / 100.0      // MAF g/s
            // 2-byte — (A*256+B) raw
            0x1F -> ((a * 256) + b).toDouble()   // Run time seconds
            0x21 -> ((a * 256) + b).toDouble()   // MIL distance km
            0x31 -> ((a * 256) + b).toDouble()   // Distance since clear km
            0x22 -> ((a * 256) + b) * 0.079      // Fuel rail pressure kPa
            0x23 -> ((a * 256) + b) * 10.0       // Fuel rail (diesel) kPa
            0x42 -> ((a * 256) + b) / 1000.0     // Control module voltage V
            // 2-byte — (A*256+B)/20
            0x5E -> ((a * 256) + b) / 20.0       // Engine fuel rate L/h
            // 4-byte
            0x43 -> ((a * 256) + b) / 100.0      // Absolute load %
            // Default: big-endian unsigned int (raw value, no formula)
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
            val count = DTCParser.extractDtcCount(rawBytes)
            val dataBytes = extractDataBytes(rawBytes)
            DTCParser.parse(dataBytes, count, status).map { enrichDtc(it) }
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

    override suspend fun readDtcWithHex(modeHex: String, status: DTCStatus): Pair<List<DTC>, String> {
        val rawBytes = requireQueue().sendObdCommand(modeHex).getOrThrow()
        val hex = rawBytes.toRawHexString()
        val count = DTCParser.extractDtcCount(rawBytes)
        val dataBytes = extractDataBytes(rawBytes)
        val codes = DTCParser.parse(dataBytes, count, status).map { enrichDtc(it) }
        return codes to hex
    }

    fun release() {
        repositoryScope.cancel()
        commandQueue?.release()
        commandQueue = null
    }
}

