package com.janus.app.data.local

import androidx.room.TypeConverter
import com.janus.app.domain.model.DeviceStatus

/**
 * Room TypeConverters for [DeviceEntity].
 *
 * Room can only persist primitive-ish column types directly; [DeviceStatus]
 * is a Kotlin enum, so it needs an explicit String <-> enum mapping. Stored
 * as the enum's name() rather than its ordinal — ordinal-based storage
 * silently breaks if enum entries are ever reordered, whereas name-based
 * storage only breaks if an entry is renamed (a much rarer, more visible
 * change).
 */
class Converters {

    @TypeConverter
    fun fromDeviceStatus(status: DeviceStatus): String = status.name

    @TypeConverter
    fun toDeviceStatus(value: String): DeviceStatus =
        runCatching { DeviceStatus.valueOf(value) }.getOrDefault(DeviceStatus.UNKNOWN)
}