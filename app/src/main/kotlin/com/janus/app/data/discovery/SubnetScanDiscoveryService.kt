package com.janus.app.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import com.janus.app.domain.model.DiscoveredDevice
import com.janus.app.domain.model.DiscoverySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Fallback device discovery: actively probes every host address on the
 * Controller's current /24 subnet for an open ADB-TCP port (spec #9).
 *
 * Used when NSD (mDNS) finds nothing — common on networks that isolate
 * clients or block multicast. This is inherently lower-confidence than NSD
 * (spec #9, #10): an open port on 5555 does not confirm the device is
 * Janus-compatible or even an Android device at all, only that *something*
 * is listening there. Port 5555 is scanned because it's the conventional
 * ADB-over-TCP port used both by `adb tcpip` and by some OEMs' persistent
 * Wireless Debugging configurations; Wireless Debugging's ad-hoc pairing/
 * connect ports are randomly assigned per-session and are NOT guessed by
 * this scanner — those require either NSD discovery or the user manually
 * entering IP/port from the Target's Developer Options screen (spec #15).
 *
 * Bounded concurrency (16 simultaneous connection attempts) keeps a full
 * /24 scan (254 addresses) fast without flooding the network or the OS
 * with hundreds of simultaneous sockets.
 */
class SubnetScanDiscoveryService(private val context: Context) {

    fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        val subnetHosts = currentSubnetHostAddresses()
        if (subnetHosts.isEmpty()) {
            close()
            return@callbackFlow
        }

        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        val jobs = subnetHosts.map { host ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    if (isPortOpen(host, ADB_TCP_PORT, PROBE_TIMEOUT_MILLIS)) {
                        trySend(
                            DiscoveredDevice(
                                ipAddress = host,
                                port = ADB_TCP_PORT,
                                serviceName = null,
                                source = DiscoverySource.SUBNET_SCAN,
                                discoveredAtEpochMillis = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        awaitClose {
            jobs.forEach { it.cancel() }
        }
    }

    private fun isPortOpen(host: String, port: Int, timeoutMillis: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
                true
            }
        }.getOrDefault(false)
    }

    /**
     * Computes every host address on the Controller's current Wi-Fi /24
     * subnet, excluding the network and broadcast addresses. Returns an
     * empty list if there is no active network or the prefix length isn't
     * available (e.g. no Wi-Fi connection).
     */
    private fun currentSubnetHostAddresses(): List<String> {
        val connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptyList()

        val network: Network = connectivityManager.activeNetwork ?: return emptyList()
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return emptyList()

        val ipv4Address: LinkAddress = linkProperties.linkAddresses
            .firstOrNull { it.address is java.net.Inet4Address }
            ?: return emptyList()

        val prefixLength = ipv4Address.prefixLength
        if (prefixLength < 24) {
            // Scanning anything larger than a /24 (more than 254 hosts) is
            // both slow and impolite on shared networks; skip rather than
            // attempt an enormous scan.
            return emptyList()
        }

        val baseAddressBytes = ipv4Address.address.address
        val hostBitsCount = 32 - prefixLength
        val hostCount = (1 shl hostBitsCount) - 2 // exclude network + broadcast
        if (hostCount <= 0) return emptyList()

        val baseAsInt = bytesToInt(baseAddressBytes)
        val networkBase = baseAsInt and (-1 shl hostBitsCount)

        return (1..hostCount).mapNotNull { hostOffset ->
            runCatching {
                InetAddress.getByAddress(intToBytes(networkBase + hostOffset)).hostAddress
            }.getOrNull()
        }
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private companion object {
        const val ADB_TCP_PORT = 5555
        const val PROBE_TIMEOUT_MILLIS = 300
        const val MAX_CONCURRENT_PROBES = 16
    }
}