package com.vic2ray.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRefreshPolicyTest {
    private fun due(
        now: Long = 4_000_000L,
        lastSync: Long = 0L,
        lastAttempt: Long = 0L,
        enabled: Boolean = true,
        busy: Boolean = false,
        vpnActive: Boolean = false,
        hasSources: Boolean = true,
        hasWorkingServers: Boolean = false,
        interactionActive: Boolean = false
    ) = AutoRefreshPolicy.shouldRefresh(now, lastSync, lastAttempt, enabled, busy,
        vpnActive, hasSources, hasWorkingServers, interactionActive)

    @Test fun `first launch discovers servers without a button press`() {
        assertTrue(due(now = 100L))
    }

    @Test fun `working cache is refreshed only after an hour`() {
        assertFalse(due(lastSync = 400_001L, hasWorkingServers = true))
        assertTrue(due(lastSync = 400_000L, hasWorkingServers = true))
    }

    @Test fun `empty catalog can retry before the normal cache interval`() {
        assertTrue(due(lastSync = 3_900_000L, lastAttempt = 3_000_000L))
    }

    @Test fun `failures and stop requests respect a ten minute cooldown`() {
        assertFalse(due(lastAttempt = 3_400_001L))
        assertTrue(due(lastAttempt = 3_400_000L))
    }

    @Test fun `disabled discovery and empty source lists never fetch`() {
        assertFalse(due(enabled = false))
        assertFalse(due(hasSources = false))
    }

    @Test fun `discovery cannot overlap a running scan or active tunnel`() {
        assertFalse(due(busy = true))
        assertFalse(due(vpnActive = true))
    }

    @Test fun `an import in progress pauses automatic discovery`() {
        assertFalse(due(interactionActive = true))
    }

    @Test fun `changed subscription list can refresh despite a working cache`() {
        assertTrue(due(lastSync = 0L, hasWorkingServers = true))
    }

    @Test fun `clock rollback cannot suppress discovery indefinitely`() {
        assertTrue(due(now = 1_000L, lastSync = 5_000L, lastAttempt = 5_000L,
            hasWorkingServers = true))
        assertFalse(due(now = 1_001L, lastAttempt = 1_000L))
    }
}
