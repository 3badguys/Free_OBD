package com.freeobd.app.data.local

import android.content.Context
import com.freeobd.app.data.local.entity.DtcDefinitionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Seeds the database with SAE J2012 DTC definitions from a bundled CSV file.
 *
 * The CSV format (dtc_definitions.csv in assets):
 *   code,description,category,system,severity
 *
 * Example rows:
 *   P0100,Mass or Volume Air Flow Circuit Malfunction,P,Fuel/Air,Medium
 *   P0301,Cylinder 1 Misfire Detected,P,Ignition,High
 *
 * Run on first database creation and as a refresh when new definitions are available.
 */
object DtcDefinitionSeeder {

    private const val CSV_FILENAME = "dtc_definitions.csv"
    private const val BATCH_SIZE = 200

    /**
     * Seed the database with DTC definitions if the table is empty.
     *
     * @param context Application context for asset access.
     * @param database The AppDatabase instance.
     */
    /** Bump when the CSV is expanded to force re-seed on existing installs. */
    private const val SEED_VERSION = 3

    suspend fun seedIfEmpty(context: Context, database: AppDatabase): Unit =
        withContext(Dispatchers.IO) {
            val dao = database.dtcDefinitionDao()
            val count = dao.count()
            // Skip if already seeded with current version data (~400+ codes)
            if (count >= 400) {
                return@withContext
            }
            if (count > 0) dao.deleteAll()
            seed(context, database)
        }

    /**
     * Force re-seed the DTC definitions table.
     */
    suspend fun reseed(context: Context, database: AppDatabase): Unit =
        withContext(Dispatchers.IO) {
            val dao = database.dtcDefinitionDao()
            dao.deleteAll()
            seed(context, database)
        }

    private suspend fun seed(context: Context, database: AppDatabase) {
        val dao = database.dtcDefinitionDao()

        try {
            val stream = context.assets.open(CSV_FILENAME)
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val batch = mutableListOf<DtcDefinitionEntity>()
            var totalInserted = 0

            reader.use { r ->
                // Skip header line
                r.readLine()

                var line = r.readLine()
                while (line != null) {
                    val entity = parseCsvLine(line)
                    if (entity != null) {
                        batch.add(entity)
                        if (batch.size >= BATCH_SIZE) {
                            dao.insertAll(batch.toList())
                            totalInserted += batch.size
                            batch.clear()
                        }
                    }
                    line = r.readLine()
                }

                // Insert remaining batch
                if (batch.isNotEmpty()) {
                    dao.insertAll(batch.toList())
                    totalInserted += batch.size
                }
            }

            android.util.Log.i("DtcDefinitionSeeder",
                "Seeded $totalInserted DTC definitions")
        } catch (e: Exception) {
            android.util.Log.e("DtcDefinitionSeeder",
                "Could not load DTC CSV from assets: ${e.message}")
        }
    }

    /**
     * Parse a single CSV line into a DtcDefinitionEntity.
     * Format: code,description,category,system,severity
     */
    private fun parseCsvLine(line: String): DtcDefinitionEntity? {
        if (line.isBlank()) return null

        val parts = line.split(",", limit = 5)
        if (parts.size < 2) return null

        return DtcDefinitionEntity(
            code = parts[0].trim(),
            description = parts[1].trim(),
            category = parts.getOrElse(2) { "?" }.trim(),
            system = parts.getOrElse(3) { "" }.trim().ifEmpty { null },
            severity = parts.getOrElse(4) { "" }.trim().ifEmpty { null }
        )
    }

}
