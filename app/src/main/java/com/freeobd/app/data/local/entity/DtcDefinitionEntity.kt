package com.freeobd.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity caching SAE J2012 Diagnostic Trouble Code definitions.
 *
 * Pre-seeded from bundled CSV data on first database creation.
 * Provides fast offline lookup of DTC descriptions.
 */
@Entity(tableName = "dtc_definitions")
data class DtcDefinitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "code")
    val code: String,           // e.g. "P0301"

    @ColumnInfo(name = "description")
    val description: String,    // e.g. "Cylinder 1 Misfire Detected"

    @ColumnInfo(name = "category")
    val category: String,       // P, B, C, U — top-level classification

    @ColumnInfo(name = "system")
    val system: String? = null, // e.g. "Ignition", "Fuel/Air"

    @ColumnInfo(name = "severity")
    val severity: String? = null // Low, Medium, High, Critical
)
