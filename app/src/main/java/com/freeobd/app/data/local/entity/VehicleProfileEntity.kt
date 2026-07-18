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
import androidx.room.PrimaryKey

/**
 * Room entity storing per-vehicle profile data for previously connected vehicles.
 *
 * Caches the discovered supported PIDs, protocol, and other vehicle-specific
 * information to avoid repeated PID discovery on subsequent connections.
 */
@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "vin")
    val vin: String,

    @ColumnInfo(name = "last_connected")
    val lastConnected: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "protocol")
    val protocol: String? = null,

    @ColumnInfo(name = "supported_pids")
    val supportedPids: String = "[]", // JSON array of supported PID IDs

    @ColumnInfo(name = "calibration_id")
    val calibrationId: String? = null,

    @ColumnInfo(name = "cvn")
    val cvn: String? = null,

    @ColumnInfo(name = "make")
    val make: String? = null,        // Decoded from VIN (optional)

    @ColumnInfo(name = "model_year")
    val modelYear: Int? = null       // Decoded from VIN (optional)
)
