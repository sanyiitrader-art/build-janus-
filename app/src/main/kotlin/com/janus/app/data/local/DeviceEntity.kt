package com.janus.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.janus.app.domain.model.DeviceStatus

/**
 * Room persistence model for a known device (spec #10).
 *
 * [id] is the stable, non-IP-based device identity (spec #10: "device
 * identity must not depend exclusively on the IP address") — the Phase 4
 * ADB subsystem will populate this with the device's ADB serial or RSA key
 * fingerprint once pairing/connection exists. For now it is simply declared
 * as a String primary key so nothing downstream needs to change shape once
 * that identity source is wired in.
 *
 * [lastKnownStatus] is stored as its own column (via a Room TypeConverter,
 * see Converters.kt) but is treated as a STARTING POINT only — the real
 * green/yellow/red evaluation (spec #11) is a runtime computation based on
 * current reachability, not a static persisted fact, and is done in
 * DeviceRepository, not here. The persisted value here should be read as
 * "last known status," not "current status."
 *
 * ipAddress is intentionally mutable/updatable independent of [id] — this
 * is what lets Janus recognize a device whose IP changed as the same known
 * device (spec #10, #17) rather than creating a duplicate record.
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val manufacturer: String?,
    val model: String?,
    val androidVersion: String?,
    val ipAddress: String?,
    val lastKnownStatus: DeviceStatus,
    val lastConnectedAtEpochMillis: Long?,
    val addedAtEpochMillis: Long,

    // Cached last-known values for fields only readable during an active
    // ADB session — see Device.kt's equivalent nullable live fields for
    // why these exist as a separate cached pair rather than one field.
    val lastKnownBatteryPercent: Int?,
    val lastKnownResolutionWidth: Int?,
    val lastKnownResolutionHeight: Int?
)