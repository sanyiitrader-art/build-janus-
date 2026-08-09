package com.janus.app.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.janus.app.domain.model.DiscoveredDevice
import com.janus.app.domain.model.DiscoverySource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Discovers Wireless-Debugging-enabled Android devices on the local network
 * via Android's Network Service Discovery (NSD / mDNS-DNS-SD) API (spec #9).
 *
 * Android's ADB daemon, when Wireless Debugging is enabled, advertises
 * itself over mDNS under the service type "_adb-tls-connect._tcp" (the
 * connect-ready service; a separate "_adb-tls-pairing._tcp" type is
 * advertised only during the pairing-code screen). This service queries
 * for the connect service type, since that's what indicates a device ready
 * to accept an ADB-over-TCP connection attempt (spec #17's "remembered
 * device reconnection" flow) or a first-time pairing target.
 *
 * NSD is best-effort: many routers isolate wireless clients or block
 * multicast traffic entirely, in which case this legitimately finds
 * nothing even though the Target is reachable by direct IP (spec #9 — "Do
 * not claim that every Android device can always be discovered
 * automatically"). SubnetScanDiscoveryService exists as a fallback for
 * exactly this case.
 */
class NsdDiscoveryService(context: Context) {

    private val nsdManager: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * Emits an updated [DiscoveredDevice] each time NSD resolves a newly
     * found service. The Flow stays open until cancelled by the collector
     * (e.g. when the discovery screen leaves composition) — NSD discovery
     * is inherently a long-running listener, not a one-shot query.
     */
    fun discover(): Flow<DiscoveredDevice> = callbackFlow {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Resolution failures are common/benign (e.g. the service
                // disappeared between discovery and resolve) — do not
                // close the flow or surface this as an error.
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val address = serviceInfo.host?.hostAddress ?: return
                trySend(
                    DiscoveredDevice(
                        ipAddress = address,
                        port = serviceInfo.port,
                        serviceName = serviceInfo.serviceName,
                        source = DiscoverySource.NSD,
                        discoveredAtEpochMillis = System.currentTimeMillis()
                    )
                )
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // NsdManager.resolveService must be called with a fresh
                // ResolveListener instance per call in some Android
                // versions' implementations; reusing resolveListener
                // across calls is the documented-safe pattern for a single
                // outstanding resolve at a time, which is sufficient here
                // since discovered services on a home LAN are infrequent.
                runCatching {
                    nsdManager.resolveService(serviceInfo, resolveListener)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // Intentionally not surfaced as a removal event in Phase 3
                // — DiscoverDevicesUseCase treats discovery results as a
                // best-effort snapshot list, refreshed by re-running
                // discovery, rather than maintaining precise add/remove
                // state per service.
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("NSD discovery start failed, error code $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Best-effort cleanup failure; nothing actionable for the
                // collector at this point since discovery was already
                // ending.
            }
        }

        runCatching {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        }.onFailure { close(it) }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_adb-tls-connect._tcp."
    }
}