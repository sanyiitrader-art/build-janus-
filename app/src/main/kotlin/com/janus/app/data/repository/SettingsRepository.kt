package com.janus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.janus.app.data.local.PreferenceKeys
import com.janus.app.data.local.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Typed access to app settings backed by DataStore (spec #12, #42).
 *
 * Only the device-expiration setting exists yet — this repository grows one
 * property/function pair per setting as later phases add them (streaming
 * resolution in Phase 6, audio settings in Phase 7, notification duration
 * in Phase 10, etc.), all following the same observe-as-Flow /
 * suspend-fun-to-write pattern established here.
 */
class SettingsRepository(private val context: Context) {

    /**
     * Emits the currently configured device-expiration duration in
     * milliseconds, or null if no automatic expiration is configured
     * (spec #12 — expiration is opt-in, not forced on the user).
     */
    val deviceExpirationMillis: Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[PreferenceKeys.DEVICE_EXPIRATION_MILLIS] }

    suspend fun setDeviceExpirationMillis(durationMillis: Long?) {
        context.dataStore.edit { prefs ->
            if (durationMillis == null) {
                prefs.remove(PreferenceKeys.DEVICE_EXPIRATION_MILLIS)
            } else {
                prefs[PreferenceKeys.DEVICE_EXPIRATION_MILLIS] = durationMillis
            }
        }
    }
}