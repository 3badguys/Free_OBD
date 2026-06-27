package com.freeobd.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Room entity caching SAE J1979 PID metadata definitions.
 *
 * Pre-seeded from bundled JSON data on first database creation.
 * Provides metadata (name, unit, formula) for PID value interpretation.
 */
@Entity(
    tableName = "pid_metadata",
    primaryKeys = ["pid_id", "mode"]
)
data class PidMetadataEntity(
    @ColumnInfo(name = "pid_id")
    val pidId: Int,             // Hex PID identifier, e.g. 0x0C = 12

    @ColumnInfo(name = "mode")
    val mode: Int,              // OBD mode: 0x01 (live data), 0x02 (freeze frame), 0x09 (vehicle info)

    @ColumnInfo(name = "name")
    val name: String,           // Human-readable name: "Engine RPM"

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "unit")
    val unit: String = "",      // "rpm", "°C", "kPa", "%", "km/h"

    @ColumnInfo(name = "min_value")
    val minValue: Double = 0.0,

    @ColumnInfo(name = "max_value")
    val maxValue: Double = 0.0,

    @ColumnInfo(name = "formula")
    val formula: String = "",   // e.g. "((A*256)+B)/4"

    @ColumnInfo(name = "bytes_count")
    val bytesCount: Int = 2     // 1, 2, or 4 response bytes
)
