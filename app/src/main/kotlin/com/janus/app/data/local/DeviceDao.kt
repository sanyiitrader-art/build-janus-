package com.janus.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room data access object for known devices (spec #10).
 *
 * All read queries return [Flow] so DeviceRepository (and ultimately the UI)
 * observes live changes to the known-device list automatically — e.g. when
 * ForgetExpiredDevicesUseCase (Phase 2, later file) deletes expired
 * entries, or when Phase 3's discovery updates a device's IP, collectors
 * see the change without any manual refresh call.
 *
 * Insert uses REPLACE conflict strategy keyed on the primary key ([id]),
 * matching spec #10/#17: re-inserting a device with the same identity (e.g.
 * after its IP changed) updates the existing row rather than creating a
 * duplicate.
 */
@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY lastConnectedAtEpochMillis DESC")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Update
    suspend fun update(device: DeviceEntity)

    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Used by ForgetExpiredDevicesUseCase (spec #12). Deletes devices whose
     * lastConnectedAtEpochMillis is older than [cutoffEpochMillis] — devices
     * that have never connected (null lastConnectedAtEpochMillis) are
     * intentionally excluded from this query rather than treated as
     * "infinitely old," so a freshly-discovered-but-never-connected device
     * is never accidentally auto-forgotten.
     */
    @Query(
        "DELETE FROM devices WHERE lastConnectedAtEpochMillis IS NOT NULL " +
            "AND lastConnectedAtEpochMillis < :cutoffEpochMillis"
    )
    suspend fun deleteOlderThan(cutoffEpochMillis: Long)
}