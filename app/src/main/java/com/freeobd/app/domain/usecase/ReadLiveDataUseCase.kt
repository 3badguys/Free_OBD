/*
 * Copyright 2026 3badguys <chuiC456@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.freeobd.app.domain.usecase

import com.freeobd.app.domain.model.OBDData
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for reading live sensor data from the vehicle via Mode 01.
 *
 * Wraps the repository's polling mechanism and provides a clean API
 * for the presentation layer to consume real-time OBD data.
 */
class ReadLiveDataUseCase(
    private val obdRepository: OBDRepository
) {
    /**
     * Start continuous polling of the specified PIDs.
     *
     * @param pidIds The PIDs to monitor (e.g. [0x0C, 0x0D, 0x05]).
     * @param intervalMs Target interval between complete poll cycles in milliseconds.
     *                   Default 250ms balances responsiveness with adapter reliability.
     * @return A Flow that emits a Map<pidId, OBDData> on each successful poll cycle.
     */
    operator fun invoke(
        pidIds: List<Int>,
        intervalMs: Long = 250
    ): Flow<Map<Int, OBDData>> {
        return obdRepository.pollPIDs(pidIds, intervalMs)
    }

    /**
     * Read a single PID value once (no continuous polling).
     * Useful for snapshot reads or freeze frame display.
     */
    suspend fun readOnce(pidId: Int): Result<OBDData> {
        return obdRepository.readPID(pidId)
    }

    /**
     * Read multiple PIDs in a single batch.
     * Returns a map of only the successfully-read PIDs.
     */
    suspend fun readBatch(pidIds: List<Int>): Map<Int, OBDData> {
        return obdRepository.readPIDs(pidIds)
    }
}
