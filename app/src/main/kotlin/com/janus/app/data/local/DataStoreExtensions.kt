package com.janus.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Project Janus's single app-wide Preferences DataStore instance, plus the
 * preference keys used to read/write it. SettingsRepository is the only
 * class that should read/write through this — other code should go through
 * SettingsRepository's typed API rather than touching these keys directly,
 * so the storage representation can change without hunting down every call
 * site.
 *
 * Excluded from backup — see backup_rules.xml / data_extraction_rules.xml.
 * (Settings like device-expiration duration aren't sensitive on their own,
 * but the file is excluded as a whole alongside the ADB keystore and device
 * database for simplicity; splitting sensitive vs non-sensitive prefs into
 * separate files can be revisited later if a genuine need arises.)
 */
private const val PREFERENCES_NAME = "janus_secure_prefs"

val Context.dataStore by preferencesDataStore(name = PREFERENCES_NAME)

/**
 * Device expiration cleanup setting (spec #12): stored as a duration in
 * milliseconds rather than "N days" / "N weeks" / "N months" as separate
 * fields, since the UI (DeviceExpirationScreen, Phase 10) offers days,
 * weeks, and months as different pickers for the same underlying single
 * duration value — normalizing to milliseconds at the storage layer avoids
 * needing to know which unit the user picked when actually evaluating
 * expiration later.
 *
 * Null / absent means "no automatic expiration configured" (spec #12 does
 * not require a default cleanup period to be forced on the user).
 */
object PreferenceKeys {
    val DEVICE_EXPIRATION_MILLIS: Preferences.Key<Long> =
        longPreferencesKey("device_expiration_millis")
}