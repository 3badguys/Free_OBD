package com.freeobd.app.data.mock

import com.freeobd.app.data.remote.DebugLogger
import com.freeobd.app.domain.model.*
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.random.Random

/**
 * Mock OBD repository that generates simulated vehicle data for demo/testing.
 *
 * Produces realistic, slowly-varying sensor values mimicking an idling engine
 * with occasional throttle input. No actual hardware required.
 */
class MockOBDRepository : OBDRepository {

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
    override suspend fun initELM327(protocol: String, ecuAddress: String?, cryptoKey: String?): Result<Unit> {
        mockResponse("ATZ", "ELM327 v2.1", Unit, 50)
        mockResponse("ATE0", "OK", Unit, 50)
        mockResponse("ATL0", "OK", Unit, 50)
        // Simulate Yuming Electronics adapter with crypto challenge
        val yumingResponse = buildString {
            appendLine("Shenzhen Yuming Electronics Co., Ltd.")
            appendLine("version:V1.0.0")
            appendLine("device type:B02-Z")
            appendLine("device name:OBDII")
            appendLine("device mac:27:5A:C0:29:AD:5D")
            appendLine("interface:v2.1")
            appendLine("cust id:NONE")
            append("crypt:844C10BB")
        }
        DebugLogger.tx("AT+VERSION"); delay(50); DebugLogger.rx(yumingResponse)
        val responseBytes = yumingResponse.toByteArray(Charsets.US_ASCII)
        if (com.freeobd.app.data.remote.YMOBDCrypto.isYumingAdapter(responseBytes)) {
            val challenge = com.freeobd.app.data.remote.YMOBDCrypto.extractCryptChallenge(responseBytes)
            if (challenge != null) {
                val key = com.freeobd.app.data.remote.YMOBDCrypto.generateKey(challenge)
                DebugLogger.tx("AT+SETCRYPT$key"); delay(50); DebugLogger.rx("OK")
            }
        }
        DebugLogger.tx(protocol); delay(50); DebugLogger.rx("OK")
        return Result.success(Unit)
    }

    override suspend fun readVoltage(): Result<Double> =
        mockResponse("ATRV", "13.8V", 13.8, 50)

    override suspend fun readAdapterInfo(): Result<String> =
        mockResponse("ATI", "ELM327 v2.1 (demo)", "ELM327 v2.1 (demo)", 50)

    override suspend fun sendRawCommand(command: String): Result<String> =
        mockResponse(command, "Manual TX not supported in demo mode", "Manual TX not supported in demo mode", 50)

    override suspend fun getProtocolInfo(): Result<ProtocolInfo> {
        mockResponse("ATDPN", "6", Unit, 50)
        mockResponse("ATDP", "ISO 15765-4 CAN (11 bit ID, 500 kbaud)", Unit, 50)
        return Result.success(ProtocolInfo("ISO 15765-4 CAN (11 bit ID, 500 kbaud)", "6"))
    }

    // ── Mode 01: Current Data ──────────────────────────────
    override suspend fun readPID(pidId: Int): Result<OBDData> {
        val pidHex = String.format("%02X", pidId)
        DebugLogger.tx("01$pidHex")
        delay(30)
        val data = generatePIDValue(pidId)
        DebugLogger.rx("41 $pidHex [${mockDataHex(data)}]")
        return Result.success(data)
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
        mockResponse("03", "43 02 01 02 03 04", listOf(
            mockDtc("P0301", "Cylinder 1 Misfire Detected", DTCCategory.POWERTRAIN, "Ignition", DTCSeverity.HIGH, DTCStatus.STORED),
            mockDtc("P0420", "Catalyst System Efficiency Below Threshold (Bank 1)", DTCCategory.POWERTRAIN, "Emissions", DTCSeverity.MEDIUM, DTCStatus.STORED)
        ), 150)

    // ── Mode 04: Clear DTCs ────────────────────────────────
    override suspend fun clearDTCs(): Result<Unit> =
        mockResponse("04", "44", Unit, 150)

    // ── Mode 01: Per-segment discovery ────────────────────

    override suspend fun discoverLiveDataPIDs(segment: Int): Result<LiveDataDiscovery> {
        val cmd = String.format("01%02X", segment)
        DebugLogger.tx(cmd); delay(100)
        return when (segment) {
            0x00 -> {
                DebugLogger.rx("41 00 BE 1F A8 13")
                Result.success(LiveDataDiscovery("41 00 BE 1F A8 13",
                    setOf(0x01,0x03,0x04,0x05,0x06,0x07,0x0B,0x0C,0x0D,0x0E,0x0F,
                          0x11,0x13,0x14,0x15,0x1C,0x1F,0x20)))
            }
            0x20 -> {
                DebugLogger.rx("41 20 80 00 00 01")
                Result.success(LiveDataDiscovery("41 20 80 00 00 01", setOf(0x21, 0x3F)))
            }
            else -> {
                val sh = String.format("%02X", segment)
                DebugLogger.rx("41 $sh 00 00 00 00 00")
                Result.success(LiveDataDiscovery("41 $sh 00 00 00 00 00", emptySet()))
            }
        }
    }

    override suspend fun readLiveDataPID(pidId: Int): Result<String> =
        mockPidResponse(0x01, pidId)

    // ── Mode 02: Freeze Frame ──────────────────────────────
    override suspend fun readFreezeFrame(pidId: Int): Result<OBDData> {
        delay(80)
        return Result.success(generatePIDValue(pidId))
    }

    override suspend fun discoverFreezeFramePIDs(segment: Int, frameNumber: Int): Result<FreezeFrameDiscovery> {
        val cmd = String.format("02%02X%02X", segment, frameNumber)
        DebugLogger.tx(cmd); delay(100)
        return when {
            segment == 0x00 && frameNumber == 0 -> {
                DebugLogger.rx("42 00 00 58 18 02")
                Result.success(FreezeFrameDiscovery("42 00 00 58 18 02", setOf(0x02, 0x04, 0x05, 0x0C, 0x0D, 0x11)))
            }
            segment == 0x00 && frameNumber == 1 -> {
                DebugLogger.rx("42 00 01 08 18 00")
                Result.success(FreezeFrameDiscovery("42 00 01 08 18 00", setOf(0x05, 0x0C, 0x0D)))
            }
            frameNumber >= 2 -> {
                DebugLogger.rx("7F 02 11")
                Result.failure(Exception("No more freeze frames"))
            }
            else -> {
                val sh = String.format("%02X", segment)
                val fh = String.format("%02X", frameNumber)
                DebugLogger.rx("42 $sh $fh 00 00 00 00")
                Result.success(FreezeFrameDiscovery("42 $sh $fh 00 00 00 00", emptySet()))
            }
        }
    }

    override suspend fun readFreezeFramePID(pidId: Int, frameNumber: Int): Result<String> =
        mockPidResponse(0x02, pidId, frameNumber)

    /**
     * Shared mock helper for Mode 01/02 PID responses.
     * Sends TX, generates a value, formats RX, and returns display string.
     */
    private suspend fun mockPidResponse(mode: Int, pidId: Int, frameNumber: Int = 0): Result<String> {
        val cmd = if (mode == 0x02) String.format("02%02X%02X", pidId, frameNumber)
        else String.format("%02X%02X", mode, pidId)
        DebugLogger.tx(cmd); delay(if (mode == 0x02) 80 else 60)
        val data = generatePIDValue(pidId)
        return when (data) {
            is OBDData.Numeric -> {
                val v = data.value.toInt()
                val modeResp = (mode + 0x40).toString(16).uppercase()
                DebugLogger.rx(String.format("$modeResp %02X %02X %02X", pidId, (v shr 8) and 0xFF, v and 0xFF))
                val display = if (pidId == 0x02) formatDtcCode(v) else "${data.value} ${data.unit}".trim()
                Result.success(display)
            }
            else -> {
                DebugLogger.rx("7F ${String.format("%02X", mode)} 11")
                Result.failure(Exception("serviceNotSupported"))
            }
        }
    }

    private fun formatDtcCode(value: Int): String {
        val cat = when ((value shr 14) and 0x03) { 0 -> "P"; 1 -> "C"; 2 -> "B"; 3 -> "U"; else -> "?" }
        return "$cat${(value shr 12) and 0x03}${(value shr 8) and 0x0F}${(value shr 4) and 0x0F}${value and 0x0F}"
    }

    // ── Mode 07: Pending DTCs ──────────────────────────────
    override suspend fun readPendingDTCs(): Result<List<DTC>> =
        mockResponse("07", "47 01 01 71", listOf(
            mockDtc("P0171", "System Too Lean (Bank 1)", DTCCategory.POWERTRAIN, "Fuel/Air", DTCSeverity.MEDIUM, DTCStatus.PENDING)
        ))

    // ── Mode 09: Vehicle Information ───────────────────────

    override suspend fun discoverVehicleInfoTypes(): Result<VehicleInfoDiscovery> =
        mockResponse("0900", "49 00 54 02",
            VehicleInfoDiscovery("49 00 54 02", setOf(0x02, 0x04, 0x06, 0x0A)))

    override suspend fun readVehicleInfoType(infoType: Int): Result<String> {
        val cmd = String.format("09%02X", infoType)
        return when (infoType) {
            0x02 -> mockResponse(cmd, "49 02 01 31 48 47 42 48 34 31 4A 58 4D 4E 31 30 39 31 38 36", "1HGBH41JXMN109186")
            0x04 -> mockResponse(cmd, "49 04 02 32 33 39 32 00 00 00 00 00 00 00 00 00 00 00 00 30 2D 31 30 00 00 00 00 00 00 00 00 00 00 00 00", "3292\n0-10")
            0x06 -> mockResponse(cmd, "49 06 02 A1 B2 C3 D4 E5 F6 A7 B8", "A1B2C3D4\nE5F6A7B8")
            0x0A -> mockResponse(cmd, "49 0A 01 45 43 4D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", "ECM")
            0x01, 0x03, 0x05, 0x07 -> mockResponse(cmd, String.format("49 %02X 01", infoType), "01")
            0x08, 0x0B -> mockResponse(cmd, String.format("49 %02X 01 A1 B2 C3 D4", infoType), "A1 B2 C3 D4")
            else -> mockResponse(cmd, String.format("49 %02X 00", infoType), "—")
        }
    }
    // ── Helpers ────────────────────────────────────────────

    /** Log TX → delay → log RX → return success. Default delay 100ms. */
    private suspend fun <T> mockResponse(cmd: String, rx: String, result: T, delayMs: Long = 100): Result<T> {
        DebugLogger.tx(cmd); delay(delayMs); DebugLogger.rx(rx)
        return Result.success(result)
    }

    private fun mockDtc(code: String, desc: String, cat: DTCCategory, sys: String, sev: DTCSeverity, status: DTCStatus) =
        DTC(code, desc, cat, sys, sev, status)

    // ── Mode 0A: Permanent DTCs ────────────────────────────
    override suspend fun readPermanentDTCs(): Result<List<DTC>> =
        mockResponse("0A", "4A 00", emptyList<DTC>())

    // ── PID value generation ───────────────────────────────

    private fun mockDataHex(data: OBDData): String = when (data) {
        is OBDData.Numeric -> String.format("%02X %02X",
            data.value.toInt() shr 8 and 0xFF,
            data.value.toInt() and 0xFF)
        else -> "??"
    }

    private fun generatePIDValue(pidId: Int): OBDData {
        return when (pidId) {
            // 1-byte PIDs
            0x04 -> OBDData.Numeric(engineLoad, "%", pidId)
            0x05 -> OBDData.Numeric(baseCoolantTemp, "°C", pidId)
            0x0A -> OBDData.Numeric(fuelPressure, "kPa", pidId)
            0x0B -> OBDData.Numeric(intakePressure, "kPa", pidId)
            0x0D -> OBDData.Numeric(baseSpeed, "km/h", pidId)
            0x0F -> OBDData.Numeric(intakeTemp, "°C", pidId)
            0x11 -> OBDData.Numeric(throttlePos, "%", pidId)
            0x2F -> OBDData.Numeric(fuelLevel, "%", pidId)
            0x33 -> OBDData.Numeric(baroPressure, "kPa", pidId)
            0x46 -> OBDData.Numeric(intakeTemp + 2, "°C", pidId) // Ambient ≈ intake + 2
            0x5C -> OBDData.Numeric(oilTemp, "°C", pidId)
            // 2-byte PIDs
            0x0C -> OBDData.Numeric(baseRpm, "rpm", pidId)
            0x10 -> OBDData.Numeric(mafRate, "g/s", pidId)
            0x1F -> OBDData.Numeric(runTime, "s", pidId)
            0x21 -> OBDData.Numeric(152.3 + random.nextDouble(-1.0, 1.0), "km", pidId)
            // Generic fallback — generate plausible numeric values for any PID
            0x02 -> OBDData.Numeric(0x0170.toDouble(), "", pidId) // Freeze Frame DTC = P0170
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
