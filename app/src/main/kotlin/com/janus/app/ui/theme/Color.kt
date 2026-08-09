package com.janus.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Project Janus color tokens — Compose-side mirror of res/values/colors.xml.
 * Used directly in Compose code (Theme.kt's ColorScheme, and anywhere a
 * androidx.compose.ui.graphics.Color is needed) instead of colorResource(),
 * which avoids a resource lookup on every recomposition.
 *
 * Keep these values in sync with colors.xml if either is changed.
 */

// Core neutrals
val JanusBackground = Color(0xFF0B0E11)
val JanusSurface = Color(0xFF14181D)
val JanusSurfaceVariant = Color(0xFF1C2126)
val JanusOutline = Color(0xFF2A3138)
val JanusOnBackground = Color(0xFFE6E9EC)
val JanusOnSurface = Color(0xFFC7CDD3)
val JanusOnSurfaceMuted = Color(0xFF8A939C)

// Accent
val JanusPrimary = Color(0xFF3DD9C0)
val JanusPrimaryVariant = Color(0xFF2AB3A0)
val JanusOnPrimary = Color(0xFF04211C)

// Device status colors — semantic (see DeviceStatus.kt), not decorative.
// Referenced by DeviceStatusBadge.kt to render the green/yellow/red states
// required by spec #11.
val JanusStatusGreen = Color(0xFF3ECF6E)
val JanusStatusGreenBg = Color(0x1A3ECF6E)
val JanusStatusYellow = Color(0xFFE3B341)
val JanusStatusYellowBg = Color(0x1AE3B341)
val JanusStatusRed = Color(0xFFE5484D)
val JanusStatusRedBg = Color(0x1AE5484D)

// Notification banners
val JanusNotificationSuccess = Color(0xFF3ECF6E)
val JanusNotificationError = Color(0xFFE5484D)
val JanusNotificationInfo = Color(0xFF3DA9D9)
val JanusNotificationWarning = Color(0xFFE3B341)