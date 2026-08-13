package com.janus.app.di

import android.content.Context
import com.janus.app.adb.crypto.AdbKeystoreManager
import com.janus.app.data.discovery.NsdDiscoveryService
import com.janus.app.data.discovery.SubnetScanDiscoveryService
import com.janus.app.data.local.JanusDatabase
import com.janus.app.data.repository.DeviceRepository
import com.janus.app.data.repository.SettingsRepository
import com.janus.app.domain.usecase.DiscoverDevicesUseCase
import com.janus.app.domain.usecase.ForgetExpiredDevicesUseCase

/**
 * Project Janus manual dependency container.
 *
 * Phase 2: Room database, DeviceRepository, SettingsRepository,
 * ForgetExpiredDevicesUseCase.
 * Phase 3: NsdDiscoveryService, SubnetScanDiscoveryService,
 * DiscoverDevicesUseCase.
 * Phase 4: AdbKeystoreManager -- the Controller's persistent ADB RSA
 * identity, used by AdbConnection (on reconnect) and AdbPairingClient (on
 * first pairing).
 */
class AppModule(
    val applicationContext: Context
) {
    private val database: JanusDatabase by lazy {
        JanusDatabase.getInstance(applicationContext)
    }

    val deviceRepository: DeviceRepository by lazy {
        DeviceRepository(database.deviceDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    val forgetExpiredDevicesUseCase: ForgetExpiredDevicesUseCase by lazy {
        ForgetExpiredDevicesUseCase(deviceRepository, settingsRepository)
    }

    private val nsdDiscoveryService: NsdDiscoveryService by lazy {
        NsdDiscoveryService(applicationContext)
    }

    private val subnetScanDiscoveryService: SubnetScanDiscoveryService by lazy {
        SubnetScanDiscoveryService(applicationContext)
    }

    val discoverDevicesUseCase: DiscoverDevicesUseCase by lazy {
        DiscoverDevicesUseCase(nsdDiscoveryService, subnetScanDiscoveryService)
    }

    val adbKeystoreManager: AdbKeystoreManager by lazy {
        AdbKeystoreManager(applicationContext)
    }

    // Phase 5+: TargetServerLauncher / TargetServerProcessSupervisor
    // Phase 6+: video pipeline (H264Decoder, FrameDropQueue, RemoteRenderer)
    // Phase 7+: audio pipeline (AudioDecoder, AudioSyncClock)
    // Phase 8+: input engine (CoordinateMapper, TouchEventProcessor, MultiPointerTracker)
    // Phase 10+: NotificationCenter, JanusLogger
}