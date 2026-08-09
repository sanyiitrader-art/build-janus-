package com.janus.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ExpirationPolicy (spec #12).
 * Runs as a plain JVM test: ./gradlew :app:testDebugUnitTest
 */
class ExpirationPolicyTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `never expires when no expiration configured`() {
        val lastConnected = now - 100 * ExpirationPolicy.MILLIS_PER_DAY
        assertFalse(
            ExpirationPolicy.isExpired(
                lastConnectedAtEpochMillis = lastConnected,
                expirationMillis = null,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun `never expires when device has never connected`() {
        assertFalse(
            ExpirationPolicy.isExpired(
                lastConnectedAtEpochMillis = null,
                expirationMillis = ExpirationPolicy.MILLIS_PER_DAY * 30,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun `expired exactly at the boundary`() {
        val expirationMillis = ExpirationPolicy.MILLIS_PER_DAY * 30
        val lastConnected = now - expirationMillis
        assertTrue(
            ExpirationPolicy.isExpired(
                lastConnectedAtEpochMillis = lastConnected,
                expirationMillis = expirationMillis,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun `not yet expired just under the boundary`() {
        val expirationMillis = ExpirationPolicy.MILLIS_PER_DAY * 30
        val lastConnected = now - expirationMillis + 1
        assertFalse(
            ExpirationPolicy.isExpired(
                lastConnectedAtEpochMillis = lastConnected,
                expirationMillis = expirationMillis,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun `expired well past the boundary`() {
        val expirationMillis = ExpirationPolicy.MILLIS_PER_DAY * 30
        val lastConnected = now - ExpirationPolicy.MILLIS_PER_DAY * 31
        assertTrue(
            ExpirationPolicy.isExpired(
                lastConnectedAtEpochMillis = lastConnected,
                expirationMillis = expirationMillis,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun `cutoff is null when no expiration configured`() {
        assertEquals(null, ExpirationPolicy.cutoffEpochMillis(null, now))
    }

    @Test
    fun `cutoff subtracts expiration window from now`() {
        val expirationMillis = ExpirationPolicy.MILLIS_PER_DAY * 30
        val expectedCutoff = now - expirationMillis
        assertEquals(expectedCutoff, ExpirationPolicy.cutoffEpochMillis(expirationMillis, now))
    }

    @Test
    fun `week and month constants derive correctly from day constant`() {
        assertEquals(ExpirationPolicy.MILLIS_PER_DAY * 7, ExpirationPolicy.MILLIS_PER_WEEK)
        assertEquals(ExpirationPolicy.MILLIS_PER_DAY * 30, ExpirationPolicy.MILLIS_PER_MONTH)
    }
}