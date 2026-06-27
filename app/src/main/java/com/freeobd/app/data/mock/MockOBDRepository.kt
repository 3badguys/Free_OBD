package com.freeobd.app.data.mock

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
    override suspend fun initELM327(protocol: String, ecuAddress: String?): Result<Unit> {
        delay(300) // Simulate init sequence
        return Result.success(Unit)
    }

    // ── Mode 01: Current Data ──────────────────────────────
    override suspend fun readPID(pidId: Int): Result<OBDData> {
        delay(30)
        return Result.success(generatePIDValue(pidId))
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

    // ── PID Discovery ──────────────────────────────────────
    override suspend fun discoverSupportedPIDs(mode: Int): Result<Set<Int>> {
        delay(200)
        // Return a realistic set of supported PIDs for a typical vehicle
        return Result.success(
            setOf(
                // PID 0x00 bitmap will indicate support for 0x01-0x20
                0x01, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x13, 0x14, 0x15, 0x1C,
                0x1F, 0x20,
                // PID 0x20 bitmap will indicate support for some 0x21-0x40
                0x21, 0x22, 0x23, 0x24, 0x2F,
                0x30, 0x31, 0x32, 0x33,
                // Extended PIDs
                0x42, 0x43, 0x44, 0x45, 0x46, 0x47,
                0x4C, 0x4D, 0x4E, 0x4F,
                0x51, 0x52, 0x5A, 0x5C
            )
        )
    }

    // ── Mode 03: Stored DTCs ───────────────────────────────
    override suspend fun readStoredDTCs(): Result<List<DTC>> {
        delay(150)
        return Result.success(
            listOf(
                DTC(
                    code = "P0301",
                    description = "Cylinder 1 Misfire Detected",
                    category = DTCCategory.POWERTRAIN,
                    system = "Ignition",
                    severity = DTCSeverity.HIGH,
                    status = DTCStatus.STORED
                ),
                DTC(
                    code = "P0420",
                    description = "Catalyst System Efficiency Below Threshold (Bank 1)",
                    category = DTCCategory.POWERTRAIN,
                    system = "Emissions",
                    severity = DTCSeverity.MEDIUM,
                    status = DTCStatus.STORED
                )
            )
        )
    }

    // ── Mode 04: Clear DTCs ────────────────────────────────
    override suspend fun clearDTCs(): Result<Unit> {
        delay(100)
        return Result.success(Unit)
    }

    // ── Mode 02: Freeze Frame ──────────────────────────────
    override suspend fun readFreezeFrame(pidId: Int): Result<OBDData> {
        delay(80)
        // Return snapshot values (frozen at time of fault)
        return Result.success(generatePIDValue(pidId))
    }

    // ── Mode 07: Pending DTCs ──────────────────────────────
    override suspend fun readPendingDTCs(): Result<List<DTC>> {
        delay(100)
        return Result.success(
            listOf(
                DTC(
                    code = "P0171",
                    description = "System Too Lean (Bank 1)",
                    category = DTCCategory.POWERTRAIN,
                    system = "Fuel/Air",
                    severity = DTCSeverity.MEDIUM,
                    status = DTCStatus.PENDING
                )
            )
        )
    }

    // ── Mode 09: Vehicle Information ───────────────────────
    override suspend fun readVehicleInfo(): Result<VehicleInfo> {
        delay(300) // VIN read takes longer
        return Result.success(
            VehicleInfo(
                vin = "1HGBH41JXMN109186",
                calibrationIds = listOf(
                    CalibrationId("ECM", "CAL-2024-03-A1"),
                    CalibrationId("TCM", "CAL-2024-03-T2")
                ),
                cvns = listOf(
                    CalibrationVerificationNumber("ECM", "A1B2C3D4"),
                    CalibrationVerificationNumber("TCM", "E5F6A7B8")
                )
            )
        )
    }

    // ── Mode 0A: Permanent DTCs ────────────────────────────
    override suspend fun readPermanentDTCs(): Result<List<DTC>> {
        delay(100)
        // Usually empty unless there are serious unresolved issues
        return Result.success(emptyList())
    }

    // ── PID value generation ───────────────────────────────

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
            // Default for unsupported PIDs
            else -> OBDData.Unavailable
        }
    }
}
