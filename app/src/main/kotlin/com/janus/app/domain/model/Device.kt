package com.janus.app.domain.model

/**
 * Project Janus device domain model (spec #10 — full info set retained per
 * your instruction, shown live while connected and cached when offline).
 *
 * `id` is a stable device-identity string, NOT the IP address (spec #10: "The
 * device identity must not depend exclusively on the IP address"). The exact
 * identity strategy (ADB device serial / RSA public key fingerprint) is
 * implemented in Phase 4 alongside the ADB subsystem; for now `id` is simply
 * declared as the non-IP-based identity field so nothing downstream (this
 * model, DeviceTabBar, DeviceDao) has to change shape when that lands.
 *
 * Fields that can only be read while an active ADB session is open
 * (batteryPercent, resolution) are nullable — null means "no live session
 * currently open to read this," in which case UI should fall back to
 * lastKnown* cached values rather than showing a blank field.
 */
data class Device(
    val id: String,
    val displayName: String,
    val manufacturer: String?,
    val model: String?,
    val androidVersion: String?,
    val ipAddress: String?,
    val status: DeviceStatus,
    val lastConnectedAtEpochMillis: Long?,

    // Live-only while connected; null otherwise. UI falls back to the
    // lastKnown* cached counterparts below when null.
    val batteryPercent: Int?,
    val resolutionWidth: Int?,
    val resolutionHeight: Int?,

    // Cached last-known values, persisted across disconnects so the device
    // detail screen has something meaningful to show for offline devices.
    val lastKnownBatteryPercent: Int?,
    val lastKnownResolutionWidth: Int?,
    val lastKnownResolutionHeight: Int?
)