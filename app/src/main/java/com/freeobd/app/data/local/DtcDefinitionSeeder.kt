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
    suspend fun seedIfEmpty(context: Context, database: AppDatabase): Unit =
        withContext(Dispatchers.IO) {
            val dao = database.dtcDefinitionDao()
            if (dao.count() > 0) {
                return@withContext // Already seeded
            }
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
            // If the CSV file is not bundled, seed with a minimal set of common codes
            android.util.Log.w("DtcDefinitionSeeder",
                "Could not load DTC CSV from assets, seeding minimal set: ${e.message}")
            seedMinimalSet(database)
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

    /**
     * Fallback: seed a minimal set of common DTC definitions.
     * Ensures the app has basic DTC lookup even without the bundled CSV.
     */
    private suspend fun seedMinimalSet(database: AppDatabase) {
        val dao = database.dtcDefinitionDao()
        val commonCodes = listOf(
            DtcDefinitionEntity("P0100", "Mass or Volume Air Flow Circuit Malfunction", "P", "Fuel/Air", "Medium"),
            DtcDefinitionEntity("P0101", "Mass or Volume Air Flow Circuit Range/Performance", "P", "Fuel/Air", "Medium"),
            DtcDefinitionEntity("P0110", "Intake Air Temperature Circuit Malfunction", "P", "Fuel/Air", "Low"),
            DtcDefinitionEntity("P0115", "Engine Coolant Temperature Circuit Malfunction", "P", "Cooling", "Medium"),
            DtcDefinitionEntity("P0120", "Throttle Pedal Position Sensor Circuit Malfunction", "P", "Fuel/Air", "High"),
            DtcDefinitionEntity("P0130", "O2 Sensor Circuit Malfunction (Bank 1 Sensor 1)", "P", "Emissions", "High"),
            DtcDefinitionEntity("P0170", "Fuel Trim Malfunction (Bank 1)", "P", "Fuel/Air", "Medium"),
            DtcDefinitionEntity("P0300", "Random/Multiple Cylinder Misfire Detected", "P", "Ignition", "High"),
            DtcDefinitionEntity("P0301", "Cylinder 1 Misfire Detected", "P", "Ignition", "High"),
            DtcDefinitionEntity("P0302", "Cylinder 2 Misfire Detected", "P", "Ignition", "High"),
            DtcDefinitionEntity("P0303", "Cylinder 3 Misfire Detected", "P", "Ignition", "High"),
            DtcDefinitionEntity("P0304", "Cylinder 4 Misfire Detected", "P", "Ignition", "High"),
            DtcDefinitionEntity("P0400", "Exhaust Gas Recirculation Flow Malfunction", "P", "Emissions", "Low"),
            DtcDefinitionEntity("P0420", "Catalyst System Efficiency Below Threshold (Bank 1)", "P", "Emissions", "Medium"),
            DtcDefinitionEntity("P0500", "Vehicle Speed Sensor Malfunction", "P", "Transmission", "High"),
            DtcDefinitionEntity("P0600", "Serial Communication Link Malfunction", "P", "ECU", "High"),
            DtcDefinitionEntity("P0700", "Transmission Control System Malfunction", "P", "Transmission", "High"),
            DtcDefinitionEntity("C0035", "Left Front Wheel Speed Sensor Circuit Malfunction", "C", "ABS", "High"),
            DtcDefinitionEntity("U0100", "Lost Communication With ECM/PCM", "U", "Network", "Critical"),
            DtcDefinitionEntity("B1200", "Climate Control Pushbutton Circuit Failure", "B", "Body", "Low")
        )
        dao.insertAll(commonCodes)
        android.util.Log.i("DtcDefinitionSeeder",
            "Seeded ${commonCodes.size} minimal DTC definitions")
    }
}
