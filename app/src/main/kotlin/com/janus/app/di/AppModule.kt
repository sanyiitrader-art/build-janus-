package com.janus.app.di

import android.content.Context

/**
 * Project Janus manual dependency container.
 *
 * Deliberately not a DI framework (no Hilt/Dagger/Koin) — a single
 * constructor-built object graph is enough for this app's size and keeps
 * the dependency list explicit and easy to trace, per the "avoid unnecessary
 * dependencies" requirement.
 *
 * This is a Phase 1 skeleton: it holds nothing but applicationContext today.
 * Later phases will add constructed instances here as each subsystem lands —
 * e.g. `val deviceRepository = DeviceRepository(...)` in Phase 2,
 * `val adbConnectionManager = AdbConnectionManager(...)` in Phase 4, and so
 * on — each ViewModel will then take the specific dependencies it needs as
 * constructor parameters, sourced from this module.
 */
class AppModule(
    val applicationContext: Context
) {
    // Phase 2+: device persistence (Room database, DeviceRepository, SettingsRepository)
    // Phase 3+: discovery services (NsdDiscoveryService, SubnetScanDiscoveryService)
    // Phase 4+: ADB subsystem (AdbKeystoreManager, AdbConnection factory, pairing/shell clients)
    // Phase 5+: TargetServerLauncher / TargetServerProcessSupervisor
    // Phase 6+: video pipeline (H264Decoder, FrameDropQueue, RemoteRenderer)
    // Phase 7+: audio pipeline (AudioDecoder, AudioSyncClock)
    // Phase 8+: input engine (CoordinateMapper, TouchEventProcessor, MultiPointerTracker)
    // Phase 10+: NotificationCenter, JanusLogger
}