package com.janus.app.domain

/**
 * Pure logic for the "automatically forget old devices" feature (spec #12).
 *
 * Deliberately has zero Android imports despite living in the :app module
 * — this keeps it a plain, fast JUnit-testable unit (same philosophy as
 * :coordmapping's CoordinateTransform, just not worth a whole separate
 * Gradle module for one small object). ForgetExpiredDevicesUseCase (the
 * next layer up) is what actually calls DeviceDao and supplies real
 * clock/settings values — this object never touches persistence or time
 * sources directly, which is what makes it deterministic to test.
 */
object ExpirationPolicy {

    /**
     * Returns true if a device last connected at [lastConnectedAtEpochMillis]
     * should be considered expired, given an expiration window of
     * [expirationMillis] and the current time [nowEpochMillis].
     *
     * Returns false (never expires) when:
     * - [expirationMillis] is null — no automatic expiration configured.
     * - [lastConnectedAtEpochMillis] is null — a device that has never
     *   actually connected is never auto-forgotten purely by age (spec #12
     *   intends this for previously-connected devices going stale, not for
     *   punishing devices nobody has gotten around to connecting yet).
     */
    fun isExpired(
        lastConnectedAtEpochMillis: Long?,
        expirationMillis: Long?,
        nowEpochMillis: Long
    ): Boolean {
        if (expirationMillis == null) return false
        if (lastConnectedAtEpochMillis == null) return false
        val age = nowEpochMillis - lastConnectedAtEpochMillis
        return age >= expirationMillis
    }

    /**
     * Computes the cutoff timestamp: any device with
     * lastConnectedAtEpochMillis strictly before this value is expired.
     * Used to build the SQL query bound in DeviceDao.deleteOlderThan.
     *
     * Returns null when [expirationMillis] is null (no cutoff applies).
     */
    fun cutoffEpochMillis(expirationMillis: Long?, nowEpochMillis: Long): Long? {
        if (expirationMillis == null) return null
        return nowEpochMillis - expirationMillis
    }

    // Convenience unit constants for building expirationMillis values from
    // the Days/Weeks/Months picker UI (spec #12, DeviceExpirationScreen —
    // Phase 10). Kept here rather than duplicated in the UI layer.
    const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
    const val MILLIS_PER_WEEK: Long = 7L * MILLIS_PER_DAY

    /**
     * Approximate month length (30 days) used only for the expiration
     * picker's coarse "N months" option — not intended for calendar-exact
     * date arithmetic.
     */
    const val MILLIS_PER_MONTH: Long = 30L * MILLIS_PER_DAY
}