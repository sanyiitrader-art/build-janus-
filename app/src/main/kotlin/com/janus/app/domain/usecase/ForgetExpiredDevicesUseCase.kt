package com.janus.app.domain.usecase

import com.janus.app.data.repository.DeviceRepository
import com.janus.app.data.repository.SettingsRepository
import com.janus.app.domain.ExpirationPolicy
import kotlinx.coroutines.flow.first

/**
 * Applies the device-expiration setting (spec #12) by deleting known
 * devices whose last connection is older than the configured cutoff.
 *
 * This is the runtime glue between the pure [ExpirationPolicy] logic, the
 * persisted setting ([SettingsRepository]), and actual deletion
 * ([DeviceRepository]) — kept as a separate use case (rather than folding
 * this directly into DeviceRepository) so it can be invoked from different
 * triggers later (app startup, a periodic WorkManager job, or a manual
 * "clean up now" action in settings) without duplicating the
 * setting-lookup + cutoff-computation logic at each call site.
 *
 * Intentionally does nothing (no-op, not an error) when no expiration
 * duration is configured — matches spec #12's opt-in framing.
 */
class ForgetExpiredDevicesUseCase(
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(nowEpochMillis: Long = System.currentTimeMillis()) {
        val expirationMillis = settingsRepository.deviceExpirationMillis.first()
        val cutoff = ExpirationPolicy.cutoffEpochMillis(expirationMillis, nowEpochMillis)
            ?: return
        deviceRepository.deleteOlderThan(cutoff)
    }
}