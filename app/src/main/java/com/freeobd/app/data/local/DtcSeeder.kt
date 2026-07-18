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
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Seeds the database with SAE J2012 DTC definitions from a bundled pre-built
 * SQLite database (dtc_codes.db in assets).
 *
 * The bundled db has a single table:
 *   dtc (code TEXT PRIMARY KEY, description TEXT NOT NULL, category TEXT NOT NULL)
 */
object DtcSeeder {

    private const val BUNDLED_DB = "dtc_codes.db"

    suspend fun seedIfEmpty(context: Context, database: AppDatabase) =
        withContext(Dispatchers.IO) {
            if (database.dtcDao().count() > 0) return@withContext
            seed(context, database)
        }

    suspend fun reseed(context: Context, database: AppDatabase) =
        withContext(Dispatchers.IO) {
            database.dtcDao().deleteAll()
            seed(context, database)
        }

    private suspend fun seed(context: Context, database: AppDatabase) {
        val dao = database.dtcDao()
        val destFile = File(context.getDatabasePath("_dtc_seed.db").path)

        try {
            // Copy bundled db to temp file
            destFile.parentFile?.mkdirs()
            context.assets.open(BUNDLED_DB).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            // Read from bundled db and insert into Room database
            val sourceDb = SQLiteDatabase.openDatabase(destFile.path, null, SQLiteDatabase.OPEN_READONLY)
            val sourceCount = sourceDb.compileStatement("SELECT COUNT(*) FROM dtc").use { it.simpleQueryForLong() }
            var total = 0

            sourceDb.use { sdb ->
                val cursor = sdb.rawQuery("SELECT code, description, category FROM dtc ORDER BY code", null)
                val batch = mutableListOf<com.freeobd.app.data.local.entity.DtcEntity>()
                while (cursor.moveToNext()) {
                    batch.add(
                        com.freeobd.app.data.local.entity.DtcEntity(
                            code = cursor.getString(0),
                            description = cursor.getString(1),
                            category = cursor.getString(2)
                        )
                    )
                    if (batch.size >= 500) {
                        dao.insertAll(batch.toList())
                        total += batch.size
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) {
                    dao.insertAll(batch.toList())
                    total += batch.size
                }
                cursor.close()
            }

            // Verify final count matches source
            val finalCount = dao.count()
            android.util.Log.i("DtcSeeder", "Seeded $total/$sourceCount DTC definitions, final table count: $finalCount")

            // Flush WAL into the main db file. Non-critical — data is already
            // correct via Room; this just lets external tools read the .db directly.
            try {
                val dbPath = context.getDatabasePath("free_obd.db").path
                SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                }
            } catch (_: Exception) { /* data is fine regardless */ }

            if (finalCount != sourceCount.toInt()) {
                android.util.Log.w("DtcSeeder",
                    "Count mismatch! Source: $sourceCount, final: $finalCount. " +
                    "Try clearing app data and re-launching.")
            }
        } finally {
            destFile.delete()
        }
    }
}
