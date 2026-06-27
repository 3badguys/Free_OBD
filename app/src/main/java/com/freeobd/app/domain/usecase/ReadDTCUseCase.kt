package com.freeobd.app.domain.usecase

import com.freeobd.app.domain.model.DTC
import com.freeobd.app.domain.repository.OBDRepository

/**
 * Use case for reading and clearing Diagnostic Trouble Codes.
 *
 * Aggregates DTCs from all relevant OBD modes:
 * - Mode 03: Stored (confirmed) DTCs
 * - Mode 07: Pending DTCs
 * - Mode 0A: Permanent DTCs
 */
class ReadDTCUseCase(
    private val obdRepository: OBDRepository
) {
    /**
     * Read all types of DTCs (stored, pending, permanent).
     *
     * @return A map grouping DTCs by their status type. Failed reads produce
     *         empty lists rather than failing the entire operation.
     */
    suspend operator fun invoke(): DTCAggregate {
        val stored = obdRepository.readStoredDTCs().getOrDefault(emptyList())
        val pending = obdRepository.readPendingDTCs().getOrDefault(emptyList())
        val permanent = obdRepository.readPermanentDTCs().getOrDefault(emptyList())
        return DTCAggregate(
            stored = stored,
            pending = pending,
            permanent = permanent
        )
    }

    /** Clear all stored DTCs, freeze frame data, and related diagnostic data. */
    suspend fun clear(): Result<Unit> {
        return obdRepository.clearDTCs()
    }
}

/**
 * Aggregate container for all DTC categories read from the vehicle.
 */
data class DTCAggregate(
    val stored: List<DTC>,
    val pending: List<DTC>,
    val permanent: List<DTC>
) {
    /** Total number of DTCs across all categories. */
    val totalCount: Int get() = stored.size + pending.size + permanent.size

    /** Returns true if no DTCs were found in any category. */
    val isEmpty: Boolean get() = totalCount == 0
}
