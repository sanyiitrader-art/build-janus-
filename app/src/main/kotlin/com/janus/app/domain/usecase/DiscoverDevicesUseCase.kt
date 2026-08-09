package com.janus.app.domain.usecase

import com.janus.app.data.discovery.NsdDiscoveryService
import com.janus.app.data.discovery.SubnetScanDiscoveryService
import com.janus.app.domain.model.DiscoveredDevice
import com.janus.app.domain.model.DiscoverySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

/**
 * Combines NSD and subnet-scan discovery into a single live, deduplicated
 * result list (spec #9).
 *
 * Both underlying sources are long-running Flows that emit one
 * [DiscoveredDevice] at a time as they're found; this use case merges them
 * and folds results into a running list keyed by IP address, so the
 * Discovery screen can simply collect one Flow<List<DiscoveredDevice>>
 * rather than managing two separate streams and merge/dedupe logic itself.
 *
 * Deduplication is by ipAddress only (spec #9 explicitly calls out
 * "duplicate devices" as a case the discovery subsystem must handle) — if
 * both NSD and subnet-scan find the same IP, the NSD result wins since it
 * carries a real service name and is higher-confidence; a lower-confidence
 * subnet-scan result never overwrites an already-established NSD entry for
 * the same IP, but is shown immediately if it's the first result for that
 * IP rather than waiting for NSD to potentially also find it.
 */
class DiscoverDevicesUseCase(
    private val nsdDiscoveryService: NsdDiscoveryService,
    private val subnetScanDiscoveryService: SubnetScanDiscoveryService
) {
    operator fun invoke(): Flow<List<DiscoveredDevice>> {
        val nsdResults = nsdDiscoveryService.discover()
        val subnetResults = subnetScanDiscoveryService.scan()

        return merge(nsdResults, subnetResults)
            .scan(emptyMap<String, DiscoveredDevice>()) { acc, discovered ->
                val existing = acc[discovered.ipAddress]
                val shouldReplace = existing == null ||
                    existing.source != DiscoverySource.NSD ||
                    discovered.source == DiscoverySource.NSD
                if (shouldReplace) acc + (discovered.ipAddress to discovered) else acc
            }
            .map { it.values.toList() }
    }
}