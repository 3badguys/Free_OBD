package com.freeobd.app.domain.usecase

import com.freeobd.app.domain.model.VehicleInfo
import com.freeobd.app.domain.repository.OBDRepository

/**
 * Use case for reading vehicle information via Mode 09.
 *
 * Retrieves VIN (may require multi-frame reassembly per ISO 15765-2),
 * calibration IDs, and Calibration Verification Numbers (CVNs).
 */
class ReadVehicleInfoUseCase(
    private val obdRepository: OBDRepository
) {
    /**
     * Read all available vehicle information.
     *
     * @return VehicleInfo containing VIN, calibration IDs, and CVNs.
     *         Individual fields may be null if the ECU does not support them.
     */
    suspend operator fun invoke(): Result<VehicleInfo> {
        return obdRepository.readVehicleInfo()
    }
}
