package com.freeobd.app.domain.usecase

import com.freeobd.app.domain.repository.OBDRepository

/**
 * Use case that orchestrates the PID discovery process.
 *
 * Walks the SAE J1979 PID bitmap chain (0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0)
 * to discover all PIDs supported by the vehicle for a given mode.
 *
 * Each call to a PID group returns 4 bytes (32 bits) where each bit represents
 * support for the next 32 PIDs starting from (groupOffset + 1).
 */
class DiscoverPIDsUseCase(
    private val obdRepository: OBDRepository
) {
    companion object {
        /** Standard Mode 01 PID group offsets to probe. */
        val PID_GROUP_OFFSETS = listOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0)

        /** Maximum number of consecutive empty groups before stopping the scan. */
        private const val MAX_EMPTY_GROUPS = 2
    }

    /**
     * Discover all supported PIDs for the specified mode.
     *
     * @param mode The OBD mode to query (default 0x01 for current data).
     * @return Set of supported PID IDs, or error if discovery fails entirely.
     */
    suspend operator fun invoke(mode: Int = 0x01): Result<Set<Int>> {
        return obdRepository.discoverSupportedPIDs(mode)
    }
}
