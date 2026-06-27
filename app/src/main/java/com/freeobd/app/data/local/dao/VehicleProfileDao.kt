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
