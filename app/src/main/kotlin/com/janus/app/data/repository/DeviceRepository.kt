package com.janus.app.data.repository

import com.janus.app.data.local.DeviceDao
import com.janus.app.data.local.DeviceEntity
import com.janus.app.domain.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bridges Room persistence ([DeviceEntity]) and the domain model ([Device])
 * used throughout the rest of the app (ViewModels, Compose UI).
 *
 * Status handling: [DeviceEntity.lastKnownStatus] is what's persisted, but
 * the [Device.status] this repository hands out is explicitly the
 * *last-known* status, not a live reachability check — that computation
 * (spec #11: comparing against current network state) is the job of the
 * discovery subsystem (Phase 3) and connection state machine (Phase 4),
 * which will update a device's persisted status as they learn about
 * current reachability. This repository only ever reflects what's already
 * been persisted; it does not itself perform any network activity.
 *
 * Live-only fields (batteryPercent, resolutionWidth/Height on [Device])
 * are always null when hydrating from storage — a repository read never
 * has an open ADB session to source live values from. Only the
 * lastKnown* fields are populated. Code that has just read live values
 * from an active connection (Phase 4+) is responsible for merging them
 * into the [Device] it hands to the UI; this repository's job stops at
 * "what's on disk."
 */
class DeviceRepository(private val deviceDao: DeviceDao) {

    fun observeAll(): Flow<List<Device>> =
        deviceDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeById(id: String): Flow<Device?> =
        deviceDao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): Device? =
        deviceDao.getById(id)?.toDomain()

    suspend fun upsert(device: Device) {
        deviceDao.upsert(device.toEntity())
    }

    suspend fun delete(device: Device) {
        deviceDao.deleteById(device.id)
    }

    suspend fun deleteById(id: String) {
        deviceDao.deleteById(id)
    }

    suspend fun deleteOlderThan(cutoffEpochMillis: Long) {
        deviceDao.deleteOlderThan(cutoffEpochMillis)
    }
}

private fun DeviceEntity.toDomain(): Device = Device(
    id = id,
    displayName = displayName,
    manufacturer = manufacturer,
    model = model,
    androidVersion = androidVersion,
    ipAddress = ipAddress,
    status = lastKnownStatus,
    lastConnectedAtEpochMillis = lastConnectedAtEpochMillis,
    batteryPercent = null,
    resolutionWidth = null,
    resolutionHeight = null,
    lastKnownBatteryPercent = lastKnownBatteryPercent,
    lastKnownResolutionWidth = lastKnownResolutionWidth,
    lastKnownResolutionHeight = lastKnownResolutionHeight
)

private fun Device.toEntity(): DeviceEntity = DeviceEntity(
    id = id,
    displayName = displayName,
    manufacturer = manufacturer,
    model = model,
    androidVersion = androidVersion,
    ipAddress = ipAddress,
    lastKnownStatus = status,
    lastConnectedAtEpochMillis = lastConnectedAtEpochMillis,
    addedAtEpochMillis = System.currentTimeMillis(),
    // Prefer freshly-observed live values when present (this device is
    // being upserted right after a live read), falling back to whatever
    // was already cached otherwise -- never let a null live value
    // overwrite a previously known cached value.
    lastKnownBatteryPercent = batteryPercent ?: lastKnownBatteryPercent,
    lastKnownResolutionWidth = resolutionWidth ?: lastKnownResolutionWidth,
    lastKnownResolutionHeight = resolutionHeight ?: lastKnownResolutionHeight
)