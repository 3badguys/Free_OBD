package com.freeobd.app.data.local

import android.content.Context
import com.freeobd.app.data.local.entity.PidMetadataEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seeds the database with SAE J1979 PID metadata from a bundled JSON file.
 *
 * JSON format (pid_definitions.json in assets):
 * [
 *   {
 *     "pid_id": 12,
 *     "mode": 1,
 *     "name": "Engine RPM",
 *     "description": "Engine speed in revolutions per minute",
 *     "unit": "rpm",
 *     "min_value": 0.0,
 *     "max_value": 16383.75,
 *     "formula": "((A*256)+B)/4",
 *     "bytes_count": 2
 *   },
 *   ...
 * ]
 */
object PidMetadataSeeder {

    private const val JSON_FILENAME = "pid_definitions.json"

    /**
     * Seed the PID metadata table if empty.
     */
    suspend fun seedIfEmpty(context: Context, database: AppDatabase): Unit =
        withContext(Dispatchers.IO) {
            val dao = database.pidMetadataDao()
            if (dao.count() > 0) {
                return@withContext // Already seeded
            }
            seed(context, database)
        }

    /**
     * Force re-seed the PID metadata table.
     */
    suspend fun reseed(context: Context, database: AppDatabase): Unit =
        withContext(Dispatchers.IO) {
            val dao = database.pidMetadataDao()
            dao.deleteAll()
            seed(context, database)
        }

    private suspend fun seed(context: Context, database: AppDatabase) {
        val dao = database.pidMetadataDao()

        try {
            val json = context.assets.open(JSON_FILENAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val type = object : TypeToken<List<PidMetadataJson>>() {}.type
            val parsedList: List<PidMetadataJson> = Gson().fromJson(json, type)

            val entities = parsedList.map { it.toEntity() }
            dao.insertAll(entities)

            android.util.Log.i("PidMetadataSeeder",
                "Seeded ${entities.size} PID definitions")
        } catch (e: Exception) {
            android.util.Log.w("PidMetadataSeeder",
                "Could not load PID JSON from assets, seeding minimal set: ${e.message}")
            seedMinimalSet(database)
        }
    }

    /**
     * Fallback: seed a minimal set of commonly used Mode 01 PIDs.
     */
    private suspend fun seedMinimalSet(database: AppDatabase) {
        val dao = database.pidMetadataDao()
        val commonPids = listOf(
            PidMetadataEntity(0x04, 0x01, "Calculated Engine Load", "Engine load percentage", "%", 0.0, 100.0, "A*100/255", 1),
            PidMetadataEntity(0x05, 0x01, "Engine Coolant Temperature", "Coolant temperature in degrees Celsius", "°C", -40.0, 215.0, "A-40", 1),
            PidMetadataEntity(0x0A, 0x01, "Fuel Pressure", "Fuel rail pressure", "kPa", 0.0, 765.0, "A*3", 1),
            PidMetadataEntity(0x0B, 0x01, "Intake Manifold Absolute Pressure", "Intake manifold pressure", "kPa", 0.0, 255.0, "A", 1),
            PidMetadataEntity(0x0C, 0x01, "Engine RPM", "Engine speed in revolutions per minute", "rpm", 0.0, 16383.75, "((A*256)+B)/4", 2),
            PidMetadataEntity(0x0D, 0x01, "Vehicle Speed", "Vehicle speed in km/h", "km/h", 0.0, 255.0, "A", 1),
            PidMetadataEntity(0x0F, 0x01, "Intake Air Temperature", "Intake air temperature", "°C", -40.0, 215.0, "A-40", 1),
            PidMetadataEntity(0x10, 0x01, "Mass Air Flow Rate", "Mass air flow sensor reading", "g/s", 0.0, 655.35, "((A*256)+B)/100", 2),
            PidMetadataEntity(0x11, 0x01, "Throttle Position", "Absolute throttle position", "%", 0.0, 100.0, "A*100/255", 1),
            PidMetadataEntity(0x1F, 0x01, "Run Time Since Engine Start", "Engine run time", "s", 0.0, 65535.0, "(A*256)+B", 2),
            PidMetadataEntity(0x21, 0x01, "Distance Traveled with MIL On", "Distance driven with check engine light on", "km", 0.0, 65535.0, "(A*256)+B", 2),
            PidMetadataEntity(0x2F, 0x01, "Fuel Level Input", "Fuel tank level percentage", "%", 0.0, 100.0, "A*100/255", 1),
            PidMetadataEntity(0x33, 0x01, "Absolute Barometric Pressure", "Atmospheric pressure", "kPa", 0.0, 255.0, "A", 1),
            PidMetadataEntity(0x46, 0x01, "Ambient Air Temperature", "Outside air temperature", "°C", -40.0, 215.0, "A-40", 1),
            PidMetadataEntity(0x5C, 0x01, "Engine Oil Temperature", "Oil temperature", "°C", -40.0, 210.0, "A-40", 1)
        )
        dao.insertAll(commonPids)
        android.util.Log.i("PidMetadataSeeder",
            "Seeded ${commonPids.size} minimal PID definitions")
    }

    /**
     * JSON deserialization helper — mirrors the JSON structure.
     */
    private data class PidMetadataJson(
        val pid_id: Int,
        val mode: Int = 1,
        val name: String,
        val description: String = "",
        val unit: String = "",
        val min_value: Double = 0.0,
        val max_value: Double = 0.0,
        val formula: String = "",
        val bytes_count: Int = 2
    ) {
        fun toEntity() = PidMetadataEntity(
            pidId = pid_id,
            mode = mode,
            name = name,
            description = description,
            unit = unit,
            minValue = min_value,
            maxValue = max_value,
            formula = formula,
            bytesCount = bytes_count
        )
    }
}
