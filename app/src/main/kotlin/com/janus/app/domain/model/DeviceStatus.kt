package com.janus.app.domain.model

/**
 * Device availability status (spec #11) — a meaningful UI state, not a
 * decorative color choice. DeviceStatusBadge.kt maps each value to its
 * corresponding color token (JanusStatusGreen/Yellow/Red) from Color.kt.
 *
 * GREEN — reachable now, immediate connection is expected to succeed
 *   (same local network, Wireless Debugging available, previously known).
 *
 * YELLOW — the known device appears to have changed IP/address or otherwise
 *   needs attention before a connection attempt will succeed cleanly.
 *
 * RED — currently unavailable (not on the same Wi-Fi, Wireless Debugging
 *   disabled, unreachable, network unavailable).
 *
 * UNKNOWN — status has not been evaluated yet (e.g. a device that was just
 *   added and hasn't been probed by discovery/reachability checks). Not one
 *   of the three spec colors; renders as a neutral/muted badge so it's never
 *   confused with RED.
 */
enum class DeviceStatus {
    GREEN,
    YELLOW,
    RED,
    UNKNOWN
}