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
    private var initProtocol: String = "ATSP0"
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
        showResponseHeaders: Boolean
    ): Result<Unit> {
        return runCatching {
            initProtocol = protocol
            commandQueue = null
            val queue = requireQueue()
            queue.initialize()
            ELM327Initializer(queue).initialize(protocol, ecuAddress, showResponseHeaders = showResponseHeaders).getOrThrow()
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

            // For auto-detect (ATSP0), send 0100 to force the ELM327 to lock
            // onto the vehicle's actual protocol before querying ATDP/ATDPN.
            // For explicit protocols the adapter is already locked — skip the
            // probe to save time.
            if (initProtocol == "ATSP0") {
                queue.markFirstCommand()
                queue.sendObdCommand("0100")
            }

            val number = parseProtocolNumber(queue.sendRaw("ATDPN").getOrThrow())
            currentProtocolNumber = number
            val description = parseProtocolDescription(queue.sendRaw("ATDP").getOrThrow())

            if (initProtocol == "ATSP0") {
                queue.markFirstCommand()
            }
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
            val command = "01$pidHex"
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            parsePIDResponse(pidId, rawBytes, command = command)
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
            val rawData = extractDataBytes(rawBytes, command = command)
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
            val command = String.format("01%02X", pidId)
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            val parsed = parsePIDResponse(pidId, rawBytes, command = command)
            if (parsed is OBDData.Unavailable) return@runCatching "No data"
            PidFormatter.format(parsed, database.dtcDao())
        }
    }

    // ── Mode 02: Freeze Frame ──────────────────────────────

    override suspend fun discoverFreezeFramePIDs(segment: Int, frameNumber: Int): Result<FreezeFrameDiscovery> {
        return runCatching {
            val command = String.format("02%02X%02X", segment, frameNumber)
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            // Mode 02 response echoes the frame number after the PID byte.
            // extraSkip=1 strips that extra byte for both CAN and non-CAN.
            val rawHex = rawBytes.toRawHexString()
            val rawData = extractDataBytes(rawBytes, extraSkip = 1, command = command)
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
            val command = "02$pidHex$frameHex"
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            val parsed = parsePIDResponse(pidId, rawBytes, mode = 0x02, extraSkip = 1,
                command = command)
            if (parsed is OBDData.Unavailable) return@runCatching "No data"
            PidFormatter.format(parsed, database.dtcDao())
        }
    }

    // ── Mode 07: Pending DTCs ──────────────────────────────
    override suspend fun readPendingDTCs(): Result<List<DTC>> =
        readDTCsFromMode("07", DTCStatus.PENDING)

    // ── Mode 09: InfoType discovery ──────────────────────

    override suspend fun discoverVehicleInfoTypes(): Result<VehicleInfoDiscovery> {
        return runCatching {
            val command = "0900"
            val rawBytes = requireQueue().sendObdCommand(command).getOrThrow()
            val rawHex = rawBytes.toRawHexString()
            // Non-CAN protocols (1-5) insert a message count byte after the
            // InfoType in Mode 09 responses. extraSkip=1 strips it.
            val extraSkip = if (isNonCanProtocol()) 1 else 0
            val rawData = extractDataBytes(rawBytes, extraSkip, command = command)
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
                    extractPerFramePayloads(rawBytes, command)
                        .joinToString("\n") { r -> formatSingleRecord(infoType, r) }
                        .ifBlank { "—" }
                }
                0x01, 0x03, 0x05, 0x09 -> {
                    val data = extractDataBytes(rawBytes, command = command)
                    formatInfoTypeResult(infoType, data)
                }
                else -> {
                    val extraSkip = if (isNonCanProtocol()) 1 else 0
                    val data = extractDataBytes(rawBytes, extraSkip, command)
                    formatInfoTypeResult(infoType, data)
                }
            }
        }
    }

    /** Format a single record payload from a Mode 09 multi-record response. */
    private fun formatSingleRecord(infoType: Int, payload: ByteArray): String {
        // Strip trailing padding/filler bytes (e.g. A6, 95, AD, 99) that some
        // ECUs append after the data payload in each CAN frame.
        val trimmed = payload.trimTrailingNonPrintable()
        return when (infoType) {
            0x04, 0x0A -> // Calibration ID / ECU Name — ASCII
                String(trimmed, Charsets.US_ASCII).trimEnd(' ', ' ')
            0x06 -> // CVN — hex
                trimmed.joinToString("") { String.format("%02X", it) }
            else -> trimmed.joinToString(" ") { "%02X".format(it) }
        }
    }

    /** Remove trailing bytes that fall outside the printable ASCII range (0x20–0x7E). */
    private fun ByteArray.trimTrailingNonPrintable(): ByteArray {
        var end = size
        while (end > 0) {
            val b = this[end - 1].toInt() and 0xFF
            if (b in 0x20..0x7E) break
            end--
        }
        return if (end == size) this else copyOf(end)
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
     * Each line is a separate CAN frame; mode + InfoType + record index (3 bytes)
     * are stripped from each.
     */
    private fun extractPerFramePayloads(rawBytes: ByteArray, command: String? = null): List<ByteArray> =
        extractPerFrameRaw(rawBytes, headerBytes = 3, command = command)

    // ── Mode 0A: Permanent DTCs ────────────────────────────
    override suspend fun readPermanentDTCs(): Result<List<DTC>> =
        readDTCsFromMode("0A", DTCStatus.PERMANENT)

    /**
     * Compute the expected ASCII hex response header from an OBD command,
     * or a broad fallback pattern when the command is unknown.
     *
     * With command: "010C" → response mode 0x41 → bytes "410C"
     *                "03"   → response mode 0x43 → bytes "43"
     *                "0900" → response mode 0x49 → bytes "4900"
     * Without command: returns [0x34] ('4') to match any "4x" hex pair.
     */
    private fun expectedResponseHeader(command: String?): ByteArray {
        if (command == null) return byteArrayOf(B4)  // broad "4x" fallback
        val mode = command.substring(0, 2).toInt(16)
        val responseMode = mode + 0x40
        val headerHex = String.format("%02X", responseMode) + command.substring(2)
        return headerHex.toByteArray(Charsets.US_ASCII)
    }

    /**
     * Core: scan raw bytes line by line for the OBD response header, truncate,
     * then clean and hex-decode.
     *
     * When [command] is provided (e.g. "010C"), the exact expected response
     * header is computed (e.g. "410C") and matched space-insensitively.
     * When null, falls back to broad matching of any "4x" hex pair (0x41–0x4F).
     *
     * @param headerBytes Number of leading bytes to discard from each decoded
     *   frame after the response header (mode byte + sub-byte + optional extras).
     * @param command The OBD command that was sent, or null for broad matching.
     * @return Per-frame data payloads; empty list if no valid frames found.
     */
    private fun extractPerFrameRaw(
        rawBytes: ByteArray,
        headerBytes: Int,
        command: String? = null
    ): List<ByteArray> {
        val expectedHeader = expectedResponseHeader(command)
        val result = mutableListOf<ByteArray>()

        var pos = 0
        while (pos < rawBytes.size) {
            // Skip leading CR/LF
            while (pos < rawBytes.size && (rawBytes[pos] == CR || rawBytes[pos] == LF)) pos++
            if (pos >= rawBytes.size) break

            // Find end of this line
            val lineEnd = (pos until rawBytes.size).firstOrNull {
                rawBytes[it] == CR || rawBytes[it] == LF
            } ?: rawBytes.size
            if (lineEnd <= pos) { pos = lineEnd; continue }

            // Find response header
            val markerIdx = indexOfHeader(rawBytes, pos, lineEnd, expectedHeader)

            if (markerIdx >= 0) {
                // Only now convert to String: from the marker onward
                val hexOnly = String(rawBytes, markerIdx, lineEnd - markerIdx, Charsets.US_ASCII)
                    .replace(">", "")
                    .replace(" ", "")
                if (hexOnly.length >= headerBytes * 2) {
                    val decoded = try {
                        ByteUtils.fromHexString(hexOnly)
                    } catch (_: Exception) {
                        null
                    }
                    if (decoded != null && decoded.size > headerBytes) {
                        result.add(decoded.copyOfRange(headerBytes, decoded.size))
                    }
                }
            }
            pos = lineEnd
        }

        return result
    }

    /**
     * Extract the actual data bytes from a raw ELM327 response.
     *
     * Multi-frame responses (e.g. Mode 09 VIN) span multiple CAN frames — each
     * frame's data payload is concatenated into a single ByteArray.
     *
     * @param extraSkip Additional bytes to skip per frame after the standard
     *   mode header (mode + PID/InfoType). Used for protocol-specific bytes
     *   (e.g. Mode 02 frame number echo, non-CAN Mode 09 message count).
     * @param command The OBD command that was sent, for exact response-header
     *   matching, or null for broad matching.
     */
    private fun extractDataBytes(
        rawBytes: ByteArray,
        extraSkip: Int = 0,
        command: String? = null
    ): ByteArray {
        val frames = extractPerFrameRaw(rawBytes, headerBytes = 2 + extraSkip, command = command)
        val allData = mutableListOf<Byte>()
        for (frame in frames) allData.addAll(frame.toList())
        return allData.toByteArray()
    }

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
    private fun parsePIDResponse(
        pidId: Int,
        rawBytes: ByteArray,
        mode: Int = 0x01,
        extraSkip: Int = 0,
        command: String? = null
    ): OBDData {
        var metadata = runBlocking { database.pidMetadataDao().getById(pidId, mode) }
        // Mode 02 freeze frame uses the same PID definitions as Mode 01.
        // Fall back so that bytesCount trim still works for PIDs not
        // explicitly listed under mode 2 in pid_definitions.json (e.g. 0x03).
        if (metadata == null && mode == 0x02) {
            metadata = runBlocking { database.pidMetadataDao().getById(pidId, 0x01) }
        }

        // Extract data bytes after the mode response header.
        // Mode 02 callers pass extraSkip=1 to strip the frame number echo byte.
        val dataBytes = extractDataBytes(rawBytes, extraSkip, command)

        if (dataBytes.isEmpty()) return OBDData.Unavailable

        // Trim to expected data length from metadata to strip padding/checksum bytes
        // that some ECUs/protocols append after the real data payload.
        val expectedLen = metadata?.bytesCount ?: dataBytes.size
        val trimmed = if (dataBytes.size > expectedLen) dataBytes.copyOf(expectedLen) else dataBytes

        return computePIDValue(pidId, trimmed, metadata?.unit ?: "")
    }

    /**
     * Find the exact expected response header in raw bytes, skipping spaces
     * between hex characters. E.g. pattern "410C" matches "4 1 0 C" or "41 0C".
     * Returns the byte index of the first match, or -1 if none found.
     */
    private fun indexOfHeader(bytes: ByteArray, start: Int, end: Int, pattern: ByteArray): Int {
        for (i in start until end) {
            var pi = 0
            var bi = i
            while (pi < pattern.size && bi < end) {
                if (bytes[bi] == SP) { bi++; continue }
                if (bytes[bi] == pattern[pi]) { pi++; bi++ }
                else break
            }
            if (pi == pattern.size) return i
        }
        return -1
    }

    companion object {
        private const val CR: Byte = 0x0D
        private const val LF: Byte = 0x0A
        private const val SP: Byte = 0x20
        private const val B4: Byte = 0x34  // '4'
    }

    /**
     * Compute the numeric value from PID data bytes using the standard SAE J1979 formulas.
     *
     * For non-numeric PIDs (bit-fields, multi-field enums) this returns
     * [OBDData.RawBytes] so the formatter layer can parse individual fields
     * without lossy Double round-tripping.
     */
    private fun computePIDValue(pidId: Int, data: ByteArray, unit: String): OBDData {
        val a = (data.getOrNull(0)?.toInt()?.and(0xFF) ?: 0)
        val b = (data.getOrNull(1)?.toInt()?.and(0xFF) ?: 0)
        val c = (data.getOrNull(2)?.toInt()?.and(0xFF) ?: 0)
        val d = (data.getOrNull(3)?.toInt()?.and(0xFF) ?: 0)

        return when (pidId) {
            // Non-numeric PIDs — keep raw bytes for structured formatting
            0x01 -> OBDData.RawBytes(bytes = data, pidId = pidId)             // Monitor status (MIL + DTC count)
            0x02 -> OBDData.RawBytes(bytes = data, pidId = pidId)             // Freeze Frame DTC code
            0x03 -> OBDData.RawBytes(bytes = data, pidId = pidId)             // Fuel system status (bank 1 + bank 2)

            // 1-byte — percentage: A*100/255
            0x04 -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // Engine load
            0x11 -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // Throttle position
            0x2F -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // Fuel level
            0x2C -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // EGR commanded
            0x2E -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // EVAP purge
            0x45 -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // Relative throttle
            0x47 -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // Throttle B
            0x49 -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // APP D
            0x4A -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // APP E
            0x4C -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // Commanded throttle
            0x5A -> OBDData.Numeric(a * 100.0 / 255.0, unit, pidId)           // Relative APP
            // 1-byte — temperature: A-40
            0x05 -> OBDData.Numeric(a - 40.0, unit, pidId)                     // Coolant temp
            0x0F -> OBDData.Numeric(a - 40.0, unit, pidId)                     // Intake air temp
            0x46 -> OBDData.Numeric(a - 40.0, unit, pidId)                     // Ambient air temp
            0x5C -> OBDData.Numeric(a - 40.0, unit, pidId)                     // Oil temp
            // 1-byte — timing: A/2-64
            0x0E -> OBDData.Numeric(a / 2.0 - 64.0, unit, pidId)              // Timing advance
            // 1-byte — linear
            0x0A -> OBDData.Numeric(a * 3.0, unit, pidId)                      // Fuel pressure kPa
            0x0B -> OBDData.Numeric(a.toDouble(), unit, pidId)                 // Intake pressure kPa
            0x0D -> OBDData.Numeric(a.toDouble(), unit, pidId)                 // Vehicle speed km/h
            0x33 -> OBDData.Numeric(a.toDouble(), unit, pidId)                 // Barometric pressure kPa
            0x30 -> OBDData.Numeric(a.toDouble(), unit, pidId)                 // Warm-ups since clear
            // 1-byte — fuel trim: (A/1.28)-100
            0x06 -> OBDData.Numeric(a / 1.28 - 100.0, unit, pidId)             // STFT B1
            0x07 -> OBDData.Numeric(a / 1.28 - 100.0, unit, pidId)             // LTFT B1
            0x08 -> OBDData.Numeric(a / 1.28 - 100.0, unit, pidId)             // STFT B2
            0x09 -> OBDData.Numeric(a / 1.28 - 100.0, unit, pidId)             // LTFT B2
            // 2-byte — (A*256+B)/4
            0x0C -> OBDData.Numeric(((a * 256) + b) / 4.0, unit, pidId)        // RPM
            // 2-byte — (A*256+B)/100
            0x10 -> OBDData.Numeric(((a * 256) + b) / 100.0, unit, pidId)      // MAF g/s
            // 2-byte — (A*256+B) raw
            0x1F -> OBDData.Numeric(((a * 256) + b).toDouble(), unit, pidId)   // Run time seconds
            0x21 -> OBDData.Numeric(((a * 256) + b).toDouble(), unit, pidId)   // MIL distance km
            0x31 -> OBDData.Numeric(((a * 256) + b).toDouble(), unit, pidId)   // Distance since clear km
            0x22 -> OBDData.Numeric(((a * 256) + b) * 0.079, unit, pidId)      // Fuel rail pressure kPa
            0x23 -> OBDData.Numeric(((a * 256) + b) * 10.0, unit, pidId)       // Fuel rail (diesel) kPa
            0x42 -> OBDData.Numeric(((a * 256) + b) / 1000.0, unit, pidId)     // Control module voltage V
            // 2-byte — (A*256+B)/20
            0x5E -> OBDData.Numeric(((a * 256) + b) / 20.0, unit, pidId)       // Engine fuel rate L/h
            // 4-byte
            0x43 -> OBDData.Numeric(((a * 256) + b) / 100.0, unit, pidId)      // Absolute load %
            // Default: big-endian unsigned int (raw value, no formula)
            else -> {
                var result = 0L
                for (i in data.indices) {
                    result = (result shl 8) or (data[i].toLong() and 0xFF)
                }
                OBDData.Numeric(result.toDouble(), unit, pidId)
            }
        }
    }

    private suspend fun readDTCsFromMode(modeHex: String, status: DTCStatus): Result<List<DTC>> {
        return runCatching {
            val rawBytes = requireQueue().sendObdCommand(modeHex).getOrThrow()
            val count = DTCParser.extractDtcCount(rawBytes)
            val dataBytes = extractDataBytes(rawBytes, command = modeHex)
            DTCParser.parse(dataBytes, count, status).map { enrichDtc(it) }
        }
    }

    private suspend fun enrichDtc(dtc: DTC): DTC {
        val def = database.dtcDao().getByCode(dtc.code) ?: return dtc
        return dtc.copy(
            description = def.description,
            category = when (def.category) {
                "P" -> DTCCategory.POWERTRAIN
                "B" -> DTCCategory.BODY
                "C" -> DTCCategory.CHASSIS
                "U" -> DTCCategory.NETWORK
                else -> DTCCategory.UNKNOWN
            }
        )
    }

    private suspend fun <T> readOptional(block: suspend () -> T?): T? {
        return try { block() } catch (_: Exception) { null }
    }

    override suspend fun readDtcWithHex(modeHex: String, status: DTCStatus): Pair<List<DTC>, String> {
        return try {
            val rawBytes = requireQueue().sendObdCommand(modeHex).getOrThrow()
            val hex = rawBytes.toRawHexString()
            val count = DTCParser.extractDtcCount(rawBytes)
            val dataBytes = extractDataBytes(rawBytes, command = modeHex)
            val codes = DTCParser.parse(dataBytes, count, status).map { enrichDtc(it) }
            codes to hex
        } catch (e: Exception) {
            // Negative response (e.g. 7F 07 11) or I/O error — return empty list
            // with the error hex so the UI can show which mode is unsupported.
            val errorHex = (e as? NegativeResponseException)?.toHexString() ?: "ERR"
            emptyList<DTC>() to errorHex
        }
    }

    fun release() {
        repositoryScope.cancel()
        commandQueue?.release()
        commandQueue = null
    }
}
