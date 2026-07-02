package com.freeobd.app.data.repository

import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.data.remote.*
import com.freeobd.app.domain.model.*
import com.freeobd.app.domain.repository.BluetoothRepository
import com.freeobd.app.domain.repository.OBDRepository
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
    override suspend fun initELM327(protocol: String, ecuAddress: String?): Result<Unit> {
        return runCatching {
            // Reset cached command queue so we always use the current transport.
            // If we don't do this, a reconnection after disconnect would reuse
            // the old queue referencing a stale (disconnected) transport, causing
            // "Not connected—no input stream available".
            commandQueue = null
            val queue = requireQueue()
            queue.initialize()
            ELM327Initializer(queue).initialize(protocol, ecuAddress).getOrThrow()
            queue.markFirstCommand()
        }
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
                // Try multi-frame reassembly first
                val reassembled = multiFrameHandler.processFrame(data)
                reassembled?.let { String(it).trim().takeIf { it.isNotBlank() } }
                    ?: data.let { String(it).trim().takeIf { str -> str.isNotBlank() && str.length >= 10 } }
            }

            // Calibration ID (Mode 09 PID 04)
            val calId = readOptional {
                val rawBytes = requireQueue().sendRaw("0904").getOrThrow()
                val data = extractDataBytes(rawBytes)
                String(data).trim().takeIf { it.isNotBlank() && it.length > 2 }
            }

            // CVN (Mode 09 PID 06)
            val cvn = readOptional {
                val rawBytes = requireQueue().sendRaw("0906").getOrThrow()
                val data = extractDataBytes(rawBytes)
                data.joinToString("") { String.format("%02X", it) }.takeIf { it.isNotBlank() }
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
        val hex = rawBytesToHex(rawBytes)

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
     * ELM327 responses look like:
     *   "41 0C 1B 88 \r\r>"  (with spaces)
     *   or "41 0C 1B 88" (without spaces, after echo stripping)
     *
     * We need bytes after the mode byte (41) and PID byte (0C).
     */
    private fun extractDataBytes(rawBytes: ByteArray): ByteArray {
        // Convert to clean hex string, skipping non-hex chars
        val hexStr = rawBytesToHex(rawBytes).replace(" ", "")

        // Response should start with mode response byte + PID
        // Mode response = request mode + 0x40 (so request 01 → response 41)
        // Minimum: 2 hex chars for mode, 2 hex chars for PID
        if (hexStr.length < 4) return ByteArray(0)

        // Skip the response mode byte and PID byte (first 4 hex chars = 2 bytes)
        val dataHex = if (hexStr.length > 4) hexStr.substring(4) else ""
        if (dataHex.isEmpty()) return ByteArray(0)

        // Convert remaining hex to bytes
        return try {
            ByteArray(dataHex.length / 2) { i ->
                dataHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
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

private fun rawBytesToHex(bytes: ByteArray): String =
    bytes.joinToString(" ") { String.format("%02X", it) }
