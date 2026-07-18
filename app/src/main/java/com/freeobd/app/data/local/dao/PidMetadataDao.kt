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

package com.freeobd.app.data.local.dao

import androidx.room.*
import com.freeobd.app.data.local.entity.PidMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PidMetadataDao {

    /** Get all PID definitions for a specific mode. */
    @Query("SELECT * FROM pid_metadata WHERE mode = :mode ORDER BY pid_id ASC")
    suspend fun getByMode(mode: Int): List<PidMetadataEntity>

    /** Get a single PID definition by ID and mode. */
    @Query("SELECT * FROM pid_metadata WHERE pid_id = :pidId AND mode = :mode LIMIT 1")
    suspend fun getById(pidId: Int, mode: Int = 0x01): PidMetadataEntity?

    /** Flow-based observation of a single PID definition. */
    @Query("SELECT * FROM pid_metadata WHERE pid_id = :pidId AND mode = :mode LIMIT 1")
    fun observeById(pidId: Int, mode: Int = 0x01): Flow<PidMetadataEntity?>

    /** Get all PID definitions. */
    @Query("SELECT * FROM pid_metadata ORDER BY pid_id ASC")
    suspend fun getAll(): List<PidMetadataEntity>

    /** Bulk insert or replace PID definitions (used by seeder). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(definitions: List<PidMetadataEntity>)

    /** Count cached PID definitions. */
    @Query("SELECT COUNT(*) FROM pid_metadata")
    suspend fun count(): Int

    /** Delete all PID definitions (used before re-seeding). */
    @Query("DELETE FROM pid_metadata")
    suspend fun deleteAll()
}
