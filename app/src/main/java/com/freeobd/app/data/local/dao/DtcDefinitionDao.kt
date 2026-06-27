package com.freeobd.app.data.local.dao

import androidx.room.*
import com.freeobd.app.data.local.entity.DtcDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DtcDefinitionDao {

    /** Lookup a single DTC definition by code. */
    @Query("SELECT * FROM dtc_definitions WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): DtcDefinitionEntity?

    /** Flow-based lookup for reactive UI updates. */
    @Query("SELECT * FROM dtc_definitions WHERE code = :code LIMIT 1")
    fun observeByCode(code: String): Flow<DtcDefinitionEntity?>

    /** Search DTC descriptions by keyword. */
    @Query(
        "SELECT * FROM dtc_definitions " +
        "WHERE description LIKE '%' || :query || '%' " +
        "OR code LIKE '%' || :query || '%' " +
        "ORDER BY code ASC"
    )
    suspend fun search(query: String): List<DtcDefinitionEntity>

    /** Get all DTCs in a specific category (P, B, C, U). */
    @Query("SELECT * FROM dtc_definitions WHERE category = :category ORDER BY code ASC")
    suspend fun getByCategory(category: String): List<DtcDefinitionEntity>

    /** Bulk insert or replace DTC definitions (used by seeder). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(definitions: List<DtcDefinitionEntity>)

    /** Count total cached definitions. */
    @Query("SELECT COUNT(*) FROM dtc_definitions")
    suspend fun count(): Int

    /** Delete all definitions (used before re-seeding). */
    @Query("DELETE FROM dtc_definitions")
    suspend fun deleteAll()
}
