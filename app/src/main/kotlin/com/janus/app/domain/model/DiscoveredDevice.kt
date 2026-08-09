package com.janus.app.domain.model

/**
 * A raw result from the discovery subsystem (spec #9) — deliberately a
 * separate model from [Device].
 *
 * [Device] represents a *known* device with a stable, non-IP-based identity
 * (populated once ADB pairing/connection exists in Phase 4) and persisted
 * history. [DiscoveredDevice] represents something discovery just observed
 * on the network *right now* — it has no confirmed stable identity yet
 * (only whatever the network broadcast/scan told us) and is not persisted.
 *
 * Matching a [DiscoveredDevice] to an existing known [Device] (e.g. "this
 * discovered IP belongs to a device I already know about") is done by
 * DiscoverDevicesUseCase / DeviceRepository, not by this model itself.
 *
 * [source] distinguishes which discovery mechanism produced this result,
 * since NSD-discovered entries carry a real advertised service name while
 * subnet-scan results are just "something answered on this IP/port" with
 * far less certainty about what it actually is.
 */
data class DiscoveredDevice(
    val ipAddress: String,
    val port: Int,
    val serviceName: String?,
    val source: DiscoverySource,
    val discoveredAtEpochMillis: Long
)

enum class DiscoverySource {
    /** Found via Android NsdManager (mDNS/DNS-SD) advertising a
     *  Wireless-Debugging-related service type. Higher confidence. */
    NSD,

    /** Found via active subnet probing of common ADB-TLS-connect ports.
     *  Lower confidence — presence of an open port does not confirm the
     *  device is actually Janus/ADB-compatible (spec #9). */
    SUBNET_SCAN
}