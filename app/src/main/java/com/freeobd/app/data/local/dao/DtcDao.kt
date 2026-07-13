package com.freeobd.app.data.local.dao

import androidx.room.*
import com.freeobd.app.data.local.entity.DtcEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DtcDao {

    @Query("SELECT * FROM dtc WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): DtcEntity?

    @Query("SELECT * FROM dtc WHERE code = :code LIMIT 1")
    fun observeByCode(code: String): Flow<DtcEntity?>

    @Query(
        "SELECT * FROM dtc " +
        "WHERE description LIKE '%' || :query || '%' " +
        "OR code LIKE '%' || :query || '%' " +
        "ORDER BY code ASC"
    )
    suspend fun search(query: String): List<DtcEntity>

    @Query("SELECT * FROM dtc WHERE category = :category ORDER BY code ASC")
    suspend fun getByCategory(category: String): List<DtcEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DtcEntity>)

    @Query("SELECT COUNT(*) FROM dtc")
    suspend fun count(): Int

    @Query("DELETE FROM dtc")
    suspend fun deleteAll()
}
