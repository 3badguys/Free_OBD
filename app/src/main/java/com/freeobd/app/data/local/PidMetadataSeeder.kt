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
 *     "pid_id": "0x0C",
 *     "mode": 1,
 *     "description": "Engine RPM",
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

    /** Bump when the JSON is expanded to force re-seed on existing installs. */
    private const val SEED_VERSION = 1

    /**
     * Seed the PID metadata table if empty or stale.
     */
    suspend fun seedIfEmpty(context: Context, database: AppDatabase): Unit =
        withContext(Dispatchers.IO) {
            val dao = database.pidMetadataDao()
            val count = dao.count()
            // Skip if already seeded with current version data (~100+ entries)
            if (count >= 100) {
                return@withContext
            }
            if (count > 0) dao.deleteAll()
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
            android.util.Log.e("PidMetadataSeeder",
                "Could not load PID JSON from assets: ${e.message}")
        }
    }

    /**
     * JSON deserialization helper — mirrors the JSON structure.
     * pid_id is a hex string (e.g. "0x0C") for human readability.
     */
    private data class PidMetadataJson(
        val pid_id: String,
        val mode: Int = 1,
        val description: String = "",
        val unit: String = "",
        val min_value: Double = 0.0,
        val max_value: Double = 0.0,
        val formula: String = "",
        val bytes_count: Int = 2
    ) {
        fun toEntity(): PidMetadataEntity {
            val id = pid_id.removePrefix("0x").removePrefix("0X").toInt(16)
            return PidMetadataEntity(
                pidId = id,
                mode = mode,
                description = description,
                unit = unit,
                minValue = min_value,
                maxValue = max_value,
                formula = formula,
                bytesCount = bytes_count
            )
        }
    }
}
