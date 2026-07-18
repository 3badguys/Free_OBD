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

package com.freeobd.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.freeobd.app.data.local.dao.DtcDao
import com.freeobd.app.data.local.dao.PidMetadataDao
import com.freeobd.app.data.local.dao.VehicleProfileDao
import com.freeobd.app.data.local.entity.DtcEntity
import com.freeobd.app.data.local.entity.PidMetadataEntity
import com.freeobd.app.data.local.entity.VehicleProfileEntity

@Database(
    entities = [
        DtcEntity::class,
        PidMetadataEntity::class,
        VehicleProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dtcDao(): DtcDao
    abstract fun pidMetadataDao(): PidMetadataDao
    abstract fun vehicleProfileDao(): VehicleProfileDao

    companion object {
        private const val DATABASE_NAME = "free_obd.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun resetInstance() {
            INSTANCE = null
        }
    }
}
