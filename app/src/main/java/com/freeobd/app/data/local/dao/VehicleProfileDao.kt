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
import com.freeobd.app.data.local.entity.VehicleProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleProfileDao {

    /** Get a vehicle profile by VIN. */
    @Query("SELECT * FROM vehicle_profiles WHERE vin = :vin LIMIT 1")
    suspend fun getByVin(vin: String): VehicleProfileEntity?

    /** Observe a vehicle profile by VIN (reactive). */
    @Query("SELECT * FROM vehicle_profiles WHERE vin = :vin LIMIT 1")
    fun observeByVin(vin: String): Flow<VehicleProfileEntity?>

    /** Get all known vehicle profiles, most recently connected first. */
    @Query("SELECT * FROM vehicle_profiles ORDER BY last_connected DESC")
    suspend fun getAll(): List<VehicleProfileEntity>

    /** Insert or update a vehicle profile. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: VehicleProfileEntity)

    /** Delete a vehicle profile by VIN. */
    @Query("DELETE FROM vehicle_profiles WHERE vin = :vin")
    suspend fun deleteByVin(vin: String)

    /** Count stored vehicle profiles. */
    @Query("SELECT COUNT(*) FROM vehicle_profiles")
    suspend fun count(): Int
}
