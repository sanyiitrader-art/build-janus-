package com.janus.app.di

import android.content.Context
import com.janus.app.data.local.JanusDatabase
import com.janus.app.data.repository.DeviceRepository
import com.janus.app.data.repository.SettingsRepository
import com.janus.app.domain.usecase.ForgetExpiredDevicesUseCase

/**
 * Project Janus manual dependency container.
 *
 * Deliberately not a DI framework (no Hilt/Dagger/Koin) — a single
 * constructor-built object graph is enough for this app's size and keeps
 * the dependency list explicit and easy to trace, per the "avoid unnecessary
 * dependencies" requirement.
 *
 * Phase 2 additions: the Room database, DeviceRepository, SettingsRepository,
 * and ForgetExpiredDevicesUseCase are now constructed here. Later phases add
 * further constructed instances following the same pattern — each
 * ViewModel takes the specific dependencies it needs as constructor
 * parameters, sourced from this module.
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

    // Phase 3+: discovery services (NsdDiscoveryService, SubnetScanDiscoveryService)
    // Phase 4+: ADB subsystem (AdbKeystoreManager, AdbConnection factory, pairing/shell clients)
    // Phase 5+: TargetServerLauncher / TargetServerProcessSupervisor
    // Phase 6+: video pipeline (H264Decoder, FrameDropQueue, RemoteRenderer)
    // Phase 7+: audio pipeline (AudioDecoder, AudioSyncClock)
    // Phase 8+: input engine (CoordinateMapper, TouchEventProcessor, MultiPointerTracker)
    // Phase 10+: NotificationCenter, JanusLogger
}