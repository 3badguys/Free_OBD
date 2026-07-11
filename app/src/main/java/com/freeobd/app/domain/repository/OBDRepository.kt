package com.freeobd.app.domain.repository

import com.freeobd.app.domain.model.DTC
import com.freeobd.app.domain.model.DTCStatus
import com.freeobd.app.domain.model.OBDData
import com.freeobd.app.domain.model.ProtocolInfo
import com.freeobd.app.domain.model.FreezeFrameDiscovery
import com.freeobd.app.domain.model.LiveDataDiscovery
import com.freeobd.app.domain.model.VehicleInfoDiscovery
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for OBD-II data operations.
 * All methods use [Result] to propagate errors to the caller.
 */
interface OBDRepository {

    // --- Initialization ---

    /**
     * Initialize the ELM327 adapter with the standard init sequence.
     *
     * @param protocol The protocol selection command (e.g. "ATSP0" for auto-detect).
     * @param ecuAddress Optional ECU address (e.g. "7DF" for CAN, "33" for KWP).
     * @param showResponseHeaders Whether to enable ATH1 to show CAN headers in responses.
     */
    suspend fun initELM327(
        protocol: String = "ATSP0",
        ecuAddress: String? = null,
        showResponseHeaders: Boolean = false
    ): Result<Unit>

    /**
     * Query the actual OBD protocol negotiated between the adapter and vehicle.
     * Should be called after [initELM327] has completed.
     *
     * Sends ATDPN (numeric) and ATDP (description) to the adapter.
     */
    suspend fun getProtocolInfo(): Result<ProtocolInfo>

    /**
     * Read the vehicle battery voltage from the ELM327 adapter.
     * Uses the ATRV command. Typical voltage is ~12V when ECU is powered,
     * ~14V when engine is running (alternator charging), ~0V when ignition off.
     */
    suspend fun readVoltage(): Result<Double>

    /**
     * Read the ELM327 adapter firmware identification string.
     * Uses the ATI command. Returns the raw version string
     * (e.g. "ELM327 v1.5" or "OBDII v1.0" on clones).
     */
    suspend fun readAdapterInfo(): Result<String>

    /**
     * Send a raw AT or OBD command string and return the response text.
     * Used by the debug console for manual command input.
     */
    suspend fun sendRawCommand(command: String): Result<String>

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

    // --- Mode 01: Live Data Explorer ---

    /**
     * Discover which PIDs are supported in a single Mode 01 bitmap segment.
     *
     * @param segment The bitmap segment offset (0x00, 0x20, ... 0xE0).
     * @return [LiveDataDiscovery] with raw hex and supported PID IDs.
     */
    suspend fun discoverLiveDataPIDs(segment: Int = 0x00): Result<LiveDataDiscovery>

    /**
     * Read a single Mode 01 PID and return formatted result string.
     *
     * @param pidId The PID identifier (e.g. 0x0C for RPM).
     * @return Human-readable string with value and unit.
     */
    suspend fun readLiveDataPID(pidId: Int): Result<String>

    // --- Mode 03: Stored DTCs ---

    /** Read all stored (confirmed) Diagnostic Trouble Codes. */
    suspend fun readStoredDTCs(): Result<List<DTC>>

    // --- Mode 04: Clear DTCs ---

    /** Clear all stored DTCs, freeze frame data, and related diagnostic data. */
    suspend fun clearDTCs(): Result<Unit>

    // --- Mode 02: Freeze Frame Data ---

    /**
     * Discover which PIDs are available in the freeze frame via 02XX00 bitmap.
     *
     * @param segment The bitmap segment offset (0x00, 0x20, 0x40, ... 0xE0).
     * @return [FreezeFrameDiscovery] with raw hex response and supported PID IDs.
     */
    suspend fun discoverFreezeFramePIDs(segment: Int = 0x00, frameNumber: Int = 0): Result<FreezeFrameDiscovery>

    /**
     * Read a single PID from the freeze frame and return formatted result.
     *
     * @param pidId The PID identifier (e.g. 0x0C for RPM).
     * @param frameNumber The freeze frame index (0 = first frame).
     * @return Human-readable string with value and unit.
     */
    suspend fun readFreezeFramePID(pidId: Int, frameNumber: Int = 0): Result<String>

    // --- Mode 07: Pending DTCs ---

    /** Read pending DTCs (detected in current or last drive cycle, MIL not yet on). */
    suspend fun readPendingDTCs(): Result<List<DTC>>

    // --- Mode 09: Vehicle Information ---

    /**
     * Discover which Mode 09 InfoTypes the ECU supports via 0900 bitmap.
     *
     * @return [VehicleInfoDiscovery] with raw hex response and supported type IDs.
     */
    suspend fun discoverVehicleInfoTypes(): Result<VehicleInfoDiscovery>

    /**
     * Read a single Mode 09 InfoType and return the formatted result string.
     *
     * @param infoType The InfoType ID (e.g. 0x02 for VIN).
     * @return Human-readable string representation of the data.
     */
    suspend fun readVehicleInfoType(infoType: Int): Result<String>

    // --- Mode 0A: Permanent DTCs ---

    /** Read permanent DTCs (cannot be cleared via Mode 04 — require repair). */
    suspend fun readPermanentDTCs(): Result<List<DTC>>

    /** Read DTCs and raw hex in a single command — avoids double-sending the mode query. */
    suspend fun readDtcWithHex(modeHex: String, status: DTCStatus): Pair<List<DTC>, String>
}
