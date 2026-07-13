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
