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
