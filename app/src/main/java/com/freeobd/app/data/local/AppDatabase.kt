package com.freeobd.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.freeobd.app.data.local.dao.DtcDefinitionDao
import com.freeobd.app.data.local.dao.PidMetadataDao
import com.freeobd.app.data.local.dao.VehicleProfileDao
import com.freeobd.app.data.local.entity.DtcDefinitionEntity
import com.freeobd.app.data.local.entity.PidMetadataEntity
import com.freeobd.app.data.local.entity.VehicleProfileEntity

/**
 * Room database for Free OBD local data caching.
 *
 * Contains:
 * - DTC definitions (from SAE J2012)
 * - PID metadata (from SAE J1979)
 * - Vehicle profiles (per-connected-vehicle data)
 *
 * Uses fallbackToDestructiveMigration() initially since all data
 * is pre-seeded from bundled assets and can be recreated.
 */
@Database(
    entities = [
        DtcDefinitionEntity::class,
        PidMetadataEntity::class,
        VehicleProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dtcDefinitionDao(): DtcDefinitionDao
    abstract fun pidMetadataDao(): PidMetadataDao
    abstract fun vehicleProfileDao(): VehicleProfileDao

    companion object {
        private const val DATABASE_NAME = "free_obd.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance, creating it if necessary.
         *
         * Uses fallbackToDestructiveMigration() — all data is seedable from bundled assets.
         * For v2, add proper Migration objects for schema changes.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** For testing: reset the singleton instance. */
        fun resetInstance() {
            INSTANCE = null
        }
    }
}
