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

    /** Paginated: get a page of all DTC codes. */
    @Query("SELECT * FROM dtc ORDER BY code ASC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<DtcEntity>

    /** Count total DTC codes in the database. */
    @Query("SELECT COUNT(*) FROM dtc")
    suspend fun getTotalCount(): Int

    /** Paginated search by code or description. */
    @Query(
        "SELECT * FROM dtc " +
        "WHERE description LIKE '%' || :query || '%' " +
        "OR code LIKE '%' || :query || '%' " +
        "ORDER BY code ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun searchPage(query: String, limit: Int, offset: Int): List<DtcEntity>

    /** Count matching search results. */
    @Query(
        "SELECT COUNT(*) FROM dtc " +
        "WHERE description LIKE '%' || :query || '%' " +
        "OR code LIKE '%' || :query || '%'"
    )
    suspend fun searchCount(query: String): Int

    @Query("DELETE FROM dtc")
    suspend fun deleteAll()
}
