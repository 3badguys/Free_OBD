package com.freeobd.app

import android.app.Application
import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.data.local.DtcDefinitionSeeder
import com.freeobd.app.data.local.PidMetadataSeeder
import com.freeobd.app.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application entry point for Free OBD.
 *
 * Initializes:
 * - Koin dependency injection
 * - Room database
 * - DTC and PID metadata seeding (async, non-blocking)
 */
class FreeObdApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initKoin()
        seedDatabase()
    }

    private fun initKoin() {
        startKoin {
            androidContext(this@FreeObdApplication)
            modules(appModule)
        }
    }

    /**
     * Seed the Room database with bundled DTC definitions and PID metadata
     * on first launch. Runs asynchronously so app startup is not blocked.
     */
    private fun seedDatabase() {
        applicationScope.launch {
            try {
                val database = AppDatabase.getInstance(this@FreeObdApplication)

                // Seed DTC definitions (from CSV or minimal fallback set)
                DtcDefinitionSeeder.seedIfEmpty(this@FreeObdApplication, database)

                // Seed PID metadata (from JSON or minimal fallback set)
                PidMetadataSeeder.seedIfEmpty(this@FreeObdApplication, database)

                android.util.Log.i("FreeObdApp", "Database seeding complete")
            } catch (e: Exception) {
                android.util.Log.e("FreeObdApp", "Database seeding failed", e)
            }
        }
    }
}
