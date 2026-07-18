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

package com.freeobd.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Room entity caching SAE J1979 PID metadata definitions.
 *
 * Pre-seeded from bundled JSON data on first database creation.
 * Provides metadata (description, unit, formula) for PID value interpretation.
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

    @ColumnInfo(name = "description")
    val description: String,    // Human-readable description: "Engine RPM"

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
