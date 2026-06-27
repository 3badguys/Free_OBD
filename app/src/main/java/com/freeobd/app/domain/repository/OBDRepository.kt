package com.freeobd.app.domain.repository

import com.freeobd.app.domain.model.DTC
import com.freeobd.app.domain.model.OBDData
import com.freeobd.app.domain.model.VehicleInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for OBD-II data operations.
 * All methods use [Result] to propagate errors to the caller.
 */
interface OBDRepository {

    // --- Initialization ---

    /**
     * Initialize the ELM327 adapter with the standard init sequence:
     * ATZ → ATE0 → ATL0 → ATSPx → ATH1
     *
     * @param protocol The protocol selection command (e.g. "ATSP0" for auto-detect).
     * @param ecuAddress Optional ECU CAN address (e.g. "7DF").
     */
    suspend fun initELM327(protocol: String = "ATSP0", ecuAddress: String? = null): Result<Unit>

    // --- Mode 01: Current Data ---

    /**
     * Read a single PID value from Mode 01 (current data).
     *
     * @param pidId The PID identifier (e.g. 0x0C for RPM).
     * @return Parsed OBD data value, or error if PID is unsupported or timed out.
     */
    suspend fun readPID(pidId: Int): Result<OBDData>

    /**
     * Read multiple PIDs in sequence and return their values as a map.
     * PIDs that fail are omitted from the result map rather than failing the entire batch.
     */
    suspend fun readPIDs(pidIds: List<Int>): Map<Int, OBDData>

    /**
     * Start continuous polling of the specified PIDs at an adaptive rate.
     * Emits a map of PID → latest value on each poll cycle.
     *
     * @param pidIds The PIDs to poll.
     * @param intervalMs Target interval between complete poll cycles in milliseconds.
     */
    fun pollPIDs(pidIds: List<Int>, intervalMs: Long = 250): Flow<Map<Int, OBDData>>

    // --- Mode 01: PID Discovery ---

    /**
     * Discover all PIDs supported by the vehicle by walking the bitmap chain.
     * Sends 0100, 0120, 0140... until a zero-bitmap is returned.
     *
     * @param mode The OBD mode to discover PIDs for (default 0x01).
     * @return Set of supported PID IDs.
     */
    suspend fun discoverSupportedPIDs(mode: Int = 0x01): Result<Set<Int>>

    // --- Mode 03: Stored DTCs ---

    /** Read all stored (confirmed) Diagnostic Trouble Codes. */
    suspend fun readStoredDTCs(): Result<List<DTC>>

    // --- Mode 04: Clear DTCs ---

    /** Clear all stored DTCs, freeze frame data, and related diagnostic data. */
    suspend fun clearDTCs(): Result<Unit>

    // --- Mode 02: Freeze Frame Data ---

    /**
     * Read freeze frame data for a specific PID.
     * Freeze frame captures sensor data at the moment a fault occurred.
     */
    suspend fun readFreezeFrame(pidId: Int): Result<OBDData>

    // --- Mode 07: Pending DTCs ---

    /** Read pending DTCs (detected in current or last drive cycle, MIL not yet on). */
    suspend fun readPendingDTCs(): Result<List<DTC>>

    // --- Mode 09: Vehicle Information ---

    /** Read vehicle information including VIN, calibration IDs, and CVN. */
    suspend fun readVehicleInfo(): Result<VehicleInfo>

    // --- Mode 0A: Permanent DTCs ---

    /** Read permanent DTCs (cannot be cleared via Mode 04 — require repair). */
    suspend fun readPermanentDTCs(): Result<List<DTC>>
}
