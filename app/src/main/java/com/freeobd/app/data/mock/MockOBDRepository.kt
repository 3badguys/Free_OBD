package com.freeobd.app.data.mock

import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.data.remote.DebugLogger
import com.freeobd.app.data.remote.PidFormatter
import com.freeobd.app.domain.model.*
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.random.Random

/**
 * Mock OBD repository that generates simulated vehicle data for demo/testing.
 */
class MockOBDRepository(
    private val database: AppDatabase
) : OBDRepository {

    // Internal state for realistic simulation
    private var baseRpm = 800.0
    private var baseSpeed = 0.0
    private var baseCoolantTemp = 25.0
    private var throttlePos = 15.0
    private var engineLoad = 22.0
    private var fuelLevel = 65.0
    private var intakeTemp = 28.0
    private var oilTemp = 30.0
    private var mafRate = 4.5
    private var fuelPressure = 350.0
    private var intakePressure = 35.0
    private var baroPressure = 101.0
    private var runTime = 0.0
    private val random = Random(42)
    // ── Initialization ─────────────────────────────────────
    override suspend fun initELM327(
        protocol: String, ecuAddress: String?, showResponseHeaders: Boolean
    ): Result<Unit> {
        mockResponse("ATZ", "ELM327 v2.1", 50) { Unit }
        mockResponse("ATE0", "OK", 50) { Unit }
        mockResponse("ATL0", "OK", 50) { Unit }
        // Simulate Yuming Electronics adapter with crypto challenge
        val yumingLines = listOf(
            "Shenzhen Yuming Electronics Co., Ltd.", "version:V1.0.0",
            "device type:B02-Z", "device name:OBDII", "device mac:27:5A:C0:29:AD:5D",
            "interface:v2.1", "cust id:NONE", "crypt:844C10BB"
        )
        mockResponse("AT+VERSION", yumingLines.joinToString("\n"), 50) { Unit }
        val responseBytes = yumingLines.joinToString("\n").toByteArray(Charsets.US_ASCII)
        if (com.freeobd.app.data.remote.YMOBDCrypto.isYumingAdapter(responseBytes)) {
            val challenge = com.freeobd.app.data.remote.YMOBDCrypto.extractCryptChallenge(responseBytes)
            if (challenge != null) {
                val key = com.freeobd.app.data.remote.YMOBDCrypto.generateKey(challenge)
                mockResponse("AT+SETCRYPT$key", "OK", 50) { Unit }
            }
        }
        mockResponse("ATSP0", "OK", 50) { Unit }
        mockResponse(if (showResponseHeaders) "ATH1" else "ATH0", "OK", 50) { Unit }
        return Result.success(Unit)
    }

    override suspend fun readVoltage(): Result<Double> =
        mockResponse("ATRV", "13.8V", 50) { 13.8 }

    override suspend fun readAdapterInfo(): Result<String> =
        mockResponse("ATI", "ELM327 v2.1 (demo)", 50) { it }

    override suspend fun sendRawCommand(command: String): Result<String> =
        mockResponse(command, "Manual TX not supported in demo mode", 50) { it }

    override suspend fun getProtocolInfo(): Result<ProtocolInfo> {
        mockResponse("0100", "SEARCHING...\n41 00 FE 1F A8 13", 100) { Unit }
        mockResponse("ATDPN", "6", 50) { Unit }
        mockResponse("ATDP", "ISO 15765-4 CAN (11 bit ID, 500 kbaud)", 50) { Unit }
        return Result.success(ProtocolInfo("ISO 15765-4 CAN (11 bit ID, 500 kbaud)", "6"))
    }

    // ── Mode 01: Current Data ──────────────────────────────
    override suspend fun readPID(pidId: Int): Result<OBDData> {
        val pidHex = String.format("%02X", pidId)
        val data = generatePIDValue(pidId)
        return mockResponse("01$pidHex", "41 $pidHex [${mockDataHex(data)}]", 30) { data }
    }

    override suspend fun readPIDs(pidIds: List<Int>): Map<Int, OBDData> {
        delay(50)
        return pidIds.associateWith { generatePIDValue(it) }
    }

    override fun pollPIDs(pidIds: List<Int>, intervalMs: Long): Flow<Map<Int, OBDData>> {
        return flow {
            while (true) {
                // Simulate engine warming up
                if (runTime < 300) {
                    baseCoolantTemp = (25.0 + (runTime / 300.0) * 65.0).coerceAtMost(92.0)
                    oilTemp = (25.0 + (runTime / 300.0) * 65.0).coerceAtMost(90.0)
                    intakeTemp = (28.0 - (runTime / 300.0) * 8.0).coerceAtLeast(20.0)
                }

                // Idle variation — subtle fluctuations
                baseRpm = (800 + random.nextDouble(-30.0, 30.0)).coerceIn(650.0, 900.0)
                engineLoad = (22 + random.nextDouble(-3.0, 5.0)).coerceIn(15.0, 35.0)
                throttlePos = (15 + random.nextDouble(-1.0, 2.0)).coerceIn(12.0, 18.0)
                mafRate = (4.5 + random.nextDouble(-0.3, 0.5)).coerceIn(3.5, 6.0)
                fuelPressure = (350 + random.nextDouble(-10.0, 15.0)).coerceIn(300.0, 400.0)
                intakePressure = (35 + random.nextDouble(-2.0, 3.0)).coerceIn(28.0, 42.0)
                fuelLevel = (65 - runTime * 0.001).coerceAtLeast(63.5)
                // Simulate occasional driving: speed ramps up after brief warmup
                baseSpeed = if (runTime > 3) {
                    (40 + random.nextDouble(-5.0, 8.0) + 20 * kotlin.math.sin(runTime / 10.0))
                        .coerceIn(0.0, 80.0)
                } else 0.0

                runTime += intervalMs / 1000.0

                val values = pidIds.associateWith { generatePIDValue(it) }
                emit(values)
                delay(intervalMs)
            }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    }

    // ── Mode 03: Stored DTCs ───────────────────────────────
    override suspend fun readStoredDTCs(): Result<List<DTC>> =
        Result.success(readDtcWithHex("03", DTCStatus.STORED).first)

    // ── Mode 04: Clear DTCs ────────────────────────────────
    override suspend fun clearDTCs(): Result<Unit> =
        mockResponse("04", "44", 150) { Unit }

    // ── Mode 01: Per-segment discovery ────────────────────

    override suspend fun discoverLiveDataPIDs(segment: Int): Result<LiveDataDiscovery> {
        val cmd = String.format("01%02X", segment)
        return when (segment) {
            0x00 -> mockResponse(cmd, "41 00 FE 1F A8 13") {
                LiveDataDiscovery(it, setOf(0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x0B,0x0C,0x0D,0x0E,0x0F,0x11,0x13,0x14,0x15,0x1C,0x1F,0x20))
            }
            0x20 -> mockResponse(cmd, "41 20 80 00 00 01") {
                LiveDataDiscovery(it, setOf(0x21, 0x3F))
            }
            else -> mockResponse(cmd, String.format("41 %02X 00 00 00 00 00", segment)) {
                LiveDataDiscovery(it, emptySet())
            }
        }
    }

    override suspend fun readLiveDataPID(pidId: Int): Result<String> =
        mockPidResponse(0x01, pidId)

    // ── Mode 02: Freeze Frame ──────────────────────────────

    override suspend fun discoverFreezeFramePIDs(segment: Int, frameNumber: Int): Result<FreezeFrameDiscovery> {
        val cmd = String.format("02%02X%02X", segment, frameNumber)
        return when {
            segment == 0x00 && frameNumber == 0 ->
                mockResponse(cmd, "42 00 00 58 18 02") {
                    FreezeFrameDiscovery(it, setOf(0x02, 0x04, 0x05, 0x0C, 0x0D, 0x11))
                }
            segment == 0x00 && frameNumber == 1 ->
                // Frame #1: triggered by P0420 — different PIDs from frame 0 (no 0x04)
                mockResponse(cmd, "42 00 01 58 18 00") {
                    FreezeFrameDiscovery(it, setOf(0x02, 0x05, 0x0C, 0x0D))
                }
            frameNumber >= 2 -> {
                DebugLogger.tx(cmd); delay(100); DebugLogger.rx(withHeader(cmd, "7F 02 11"))
                Result.failure(Exception("No more freeze frames"))
            }
            else -> {
                val sh = String.format("%02X", segment); val fh = String.format("%02X", frameNumber)
                mockResponse(cmd, "42 $sh $fh 00 00 00 00") { FreezeFrameDiscovery(it, emptySet()) }
            }
        }
    }

    override suspend fun readFreezeFramePID(pidId: Int, frameNumber: Int): Result<String> =
        mockPidResponse(0x02, pidId, frameNumber)

    /** DTC codes per freeze frame (frame 0 = P0301, frame 1 = P0420). */
    private fun dtcForFrame(frame: Int) = when (frame) { 0 -> 0x0301; 1 -> 0x0420; else -> 0x0301 }

    /** Shared mock helper for Mode 01/02 PID responses. */
    private suspend fun mockPidResponse(mode: Int, pidId: Int, frameNumber: Int = 0): Result<String> {
        val cmd = if (mode == 0x02) String.format("02%02X%02X", pidId, frameNumber)
        else String.format("%02X%02X", mode, pidId)
        DebugLogger.tx(cmd); delay(if (mode == 0x02) 80 else 60)
        val data = generatePIDValue(pidId, frameNumber)
        return when (data) {
            is OBDData.Numeric -> {
                val v = data.value.toInt()
                val mr = (mode + 0x40).toString(16).uppercase()
                DebugLogger.rx(withHeader(cmd, String.format("$mr %02X %02X %02X", pidId, (v shr 8) and 0xFF, v and 0xFF)))
                val formatted = PidFormatter.format(data)
                Result.success(formatted)
            }
            is OBDData.RawBytes -> {
                val mr = (mode + 0x40).toString(16).uppercase()
                val hex = data.bytes.joinToString(" ") { String.format("%02X", it) }
                DebugLogger.rx(withHeader(cmd, String.format("$mr %02X %s", pidId, hex)))
                val formatted = PidFormatter.format(data, database.dtcDao())
                Result.success(formatted)
            }
            else -> {
                DebugLogger.rx(withHeader(cmd, "7F ${String.format("%02X", mode)} 11"))
                Result.failure(Exception("serviceNotSupported"))
            }
        }
    }

    // ── Mode 07: Pending DTCs ──────────────────────────────
    override suspend fun readPendingDTCs(): Result<List<DTC>> =
        Result.success(readDtcWithHex("07", DTCStatus.PENDING).first)

    // ── Mode 09: Vehicle Information ───────────────────────

    override suspend fun discoverVehicleInfoTypes(): Result<VehicleInfoDiscovery> =
        mockResponse("0900", "49 00 54 02") {
            VehicleInfoDiscovery(it, setOf(0x02, 0x04, 0x06, 0x0A))
        }

    override suspend fun readVehicleInfoType(infoType: Int): Result<String> {
        val cmd = String.format("09%02X", infoType)
        return when (infoType) {
            0x02 -> mockResponse(cmd, "49 02 01 31 48 47 42 48 34 31 4A 58 4D 4E 31 30 39 31 38 36") { "1HGBH41JXMN109186" }
            0x04 -> mockResponse(cmd, "49 04 02 32 33 39 32 00 00 00 00 00 00 00 00 00 00 00 00 30 2D 31 30 00 00 00 00 00 00 00 00 00 00 00 00") { "3292\n0-10" }
            0x06 -> mockResponse(cmd, "49 06 02 A1 B2 C3 D4 E5 F6 A7 B8") { "A1B2C3D4\nE5F6A7B8" }
            0x0A -> mockResponse(cmd, "49 0A 01 45 43 4D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00") { "ECM" }
            0x01, 0x03, 0x05, 0x07 -> mockResponse(cmd, String.format("49 %02X 01", infoType)) { "01" }
            0x08, 0x0B -> mockResponse(cmd, String.format("49 %02X 01 A1 B2 C3 D4", infoType)) { "A1 B2 C3 D4" }
            else -> mockResponse(cmd, String.format("49 %02X 00", infoType)) { "—" }
        }
    }
    // ── Helpers ────────────────────────────────────────────

    private fun isATCommand(cmd: String): Boolean =
        cmd.length >= 2 && cmd[0] in "aA" && cmd[1] in "tT"

    /**
     * Apply CAN header prefix to a response string when showHeaders is on
     * and the command is an OBD command. Handles multi-line responses
     * (e.g. "SEARCHING...\n41 00 ...") by adding headers to each hex line.
     */
    private fun withHeader(cmd: String, rx: String): String {
        if (!DemoModeState.showResponseHeaders || isATCommand(cmd)) return rx
        val hdr = "18 DA F1 10 06 "
        return rx.split("\n").joinToString("\n") { line ->
            if (line.isNotEmpty() && line[0] in '0'..'9') hdr + line else line
        }
    }

    /** Log TX → delay → log RX → build result with header-aware rx. */
    private suspend fun <T> mockResponse(
        cmd: String, rx: String, delayMs: Long = 100, resultBuilder: (String) -> T
    ): Result<T> {
        val displayRx = withHeader(cmd, rx)
        DebugLogger.tx(cmd); delay(delayMs); DebugLogger.rx(displayRx)
        return Result.success(resultBuilder(displayRx))
    }

    private fun mockDtc(code: String, desc: String, cat: DTCCategory, status: DTCStatus) =
        DTC(code, desc, cat, status)

    // ── Mode 0A: Permanent DTCs ────────────────────────────
    override suspend fun readPermanentDTCs(): Result<List<DTC>> =
        Result.success(readDtcWithHex("0A", DTCStatus.PERMANENT).first)

    override suspend fun readDtcWithHex(modeHex: String, status: DTCStatus): Pair<List<DTC>, String> {
        val (rx, codes) = when (modeHex) {
            "03" -> "43 02 03 01 04 20" to listOf(
                mockDtc("P0301", "Cylinder 1 Misfire Detected", DTCCategory.POWERTRAIN, DTCStatus.STORED),
                mockDtc("P0420", "Catalyst System Efficiency Below Threshold (Bank 1)", DTCCategory.POWERTRAIN, DTCStatus.STORED))
            "07" -> "47 01 01 71" to listOf(
                mockDtc("P0171", "System Too Lean (Bank 1)", DTCCategory.POWERTRAIN, DTCStatus.PENDING))
            "0A" -> "4A 00" to emptyList()
            else -> "7F $modeHex 11" to emptyList()
        }
        var displayHex = rx
        mockResponse(modeHex, rx, 150) { displayHex = it; Unit }
        return codes to displayHex
    }

    // ── PID value generation ───────────────────────────────

    private fun mockDataHex(data: OBDData): String = when (data) {
        is OBDData.Numeric -> String.format("%02X %02X",
            data.value.toInt() shr 8 and 0xFF,
            data.value.toInt() and 0xFF)
        else -> "??"
    }

    private fun generatePIDValue(pidId: Int, frameNumber: Int = 0): OBDData {
        return when (pidId) {
            0x01 -> OBDData.RawBytes(bytes = byteArrayOf(0x01.toByte()), pidId = pidId)
            0x02 -> {
                val dtcCode = dtcForFrame(frameNumber)
                OBDData.RawBytes(bytes = byteArrayOf(
                    ((dtcCode shr 8) and 0xFF).toByte(),
                    (dtcCode and 0xFF).toByte()
                ), pidId = pidId)
            }
            0x03 -> OBDData.RawBytes(bytes = byteArrayOf(0x02.toByte(), 0x00.toByte()), pidId = pidId)
            0x13 -> OBDData.RawBytes(bytes = byteArrayOf(0x33.toByte()), pidId = pidId) // B1S1 B1S2 B2S1 B2S2
            0x14 -> OBDData.RawBytes(bytes = byteArrayOf(0x99.toByte(), 0x80.toByte()), pidId = pidId) // ~0.765V STFT:0%
            0x15 -> OBDData.RawBytes(bytes = byteArrayOf(0x80.toByte(), 0xFF.toByte()), pidId = pidId) // ~0.640V no STFT
            0x1C -> OBDData.Numeric(0x05.toDouble(), "", pidId)  // OBD-II compliant
            // 1-byte PIDs — percentage type
            0x04 -> OBDData.Numeric(engineLoad, "%", pidId)
            0x11 -> OBDData.Numeric(throttlePos, "%", pidId)
            0x2F -> OBDData.Numeric(fuelLevel, "%", pidId)
            0x43 -> OBDData.Numeric((engineLoad * 2.55).coerceAtMost(100.0), "%", pidId)
            // 1-byte PIDs — temperature type (A-40)
            0x05 -> OBDData.Numeric(baseCoolantTemp, "°C", pidId)
            0x0F -> OBDData.Numeric(intakeTemp, "°C", pidId)
            0x46 -> OBDData.Numeric(intakeTemp + 2, "°C", pidId)
            0x5C -> OBDData.Numeric(oilTemp, "°C", pidId)
            // 1-byte PIDs — linear scaling
            0x0A -> OBDData.Numeric(fuelPressure, "kPa", pidId)        // A*3
            0x0B -> OBDData.Numeric(intakePressure, "kPa", pidId)       // A
            0x0D -> OBDData.Numeric(baseSpeed, "km/h", pidId)           // A
            0x33 -> OBDData.Numeric(baroPressure, "kPa", pidId)         // A
            // 1-byte PIDs — offset scaling
            0x0E -> OBDData.Numeric(10.0 + random.nextDouble(-2.0, 2.0), "°", pidId) // Timing advance
            // 1-byte PIDs — fuel trim type ((A/1.28)-100)
            0x06 -> OBDData.Numeric(-3.1, "%", pidId)  // STFT B1
            0x07 -> OBDData.Numeric(2.3, "%", pidId)   // LTFT B1
            0x08 -> OBDData.Numeric(-1.6, "%", pidId)  // STFT B2
            0x09 -> OBDData.Numeric(1.9, "%", pidId)   // LTFT B2
            // 2-byte PIDs
            0x0C -> OBDData.Numeric(baseRpm, "rpm", pidId)             // (A*256+B)/4
            0x10 -> OBDData.Numeric(mafRate, "g/s", pidId)             // (A*256+B)/100
            0x1F -> OBDData.Numeric(runTime, "s", pidId)               // (A*256+B)
            0x21 -> OBDData.Numeric(152.3 + random.nextDouble(-1.0, 1.0), "km", pidId)
            0x22 -> OBDData.Numeric(3500.0 + random.nextDouble(-100.0, 100.0), "kPa", pidId) // Fuel rail pressure
            0x42 -> OBDData.Numeric(13.8, "V", pidId)                  // Control module voltage
            // Generic fallback
            else -> {
                // Determine byte count by PID range (rough heuristic)
                val bytes = when {
                    pidId in setOf(0x0C, 0x10, 0x1F, 0x21, 0x22, 0x23, 0x31, 0x3C, 0x3D, 0x3E, 0x3F, 0x42, 0x43, 0x44, 0x4D, 0x4E, 0x5D, 0x5E, 0x63, 0x6B, 0x73, 0x78, 0x79, 0x7A, 0x7B) -> 2
                    pidId >= 0x7F -> 4
                    else -> 1
                }
                val value = when (bytes) {
                    2 -> 500.0 + random.nextDouble(-50.0, 50.0)
                    4 -> 100000.0 + random.nextDouble(0.0, 50000.0)
                    else -> 50.0 + random.nextDouble(-10.0, 10.0)
                }
                OBDData.Numeric(value, "", pidId)
            }
        }
    }
}
