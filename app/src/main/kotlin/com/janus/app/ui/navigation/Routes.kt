package com.janus.app.ui.navigation

/**
 * Project Janus navigation destinations.
 *
 * Kept as plain string route constants (no Safe Args / type-safe nav library)
 * to avoid an extra dependency for what is, at this app's size, a small,
 * flat destination graph. Route strings with arguments use "{argName}"
 * placeholders consistent with androidx.navigation.compose's own convention.
 */
object Routes {
    // Root / always-present
    const val REMOTE = "remote"

    // Auth (optional, shown only on first launch unless skipped)
    const val AUTH = "auth"

    // Drawer sections
    const val DEVICES_FOUND = "devices_found"
    const val PREVIOUS_DEVICES = "previous_devices"
    const val DEVICE_DETAIL = "device_detail/{deviceId}"
    const val GUIDE = "guide"
    const val DIAGNOSTICS = "diagnostics"

    // Settings
    const val SETTINGS_ROOT = "settings_root"
    const val SETTINGS_STREAMING = "settings_streaming"
    const val SETTINGS_AUDIO = "settings_audio"
    const val SETTINGS_INPUT = "settings_input"
    const val SETTINGS_CONNECTION = "settings_connection"
    const val SETTINGS_DEVICE_EXPIRATION = "settings_device_expiration"
    const val SETTINGS_NOTIFICATIONS = "settings_notifications"
    const val SETTINGS_APPEARANCE = "settings_appearance"
    const val SETTINGS_DIAGNOSTICS = "settings_diagnostics"

    fun deviceDetail(deviceId: String): String = "device_detail/$deviceId"
}