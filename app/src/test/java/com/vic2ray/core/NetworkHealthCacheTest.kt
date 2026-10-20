package com.vic2ray.core

import com.vic2ray.models.NetworkHealthCache
import com.vic2ray.models.ProtocolType
import com.vic2ray.models.TestFailure
import com.vic2ray.models.VpnConfig
import org.junit.Assert.*
import org.junit.Test

class NetworkHealthCacheTest {
    private val working = VpnConfig("vless://saved", ProtocolType.VLESS, "Saved", ping = 42, isWorking = true)
    private val failed = VpnConfig("vless://failed", ProtocolType.VLESS, "Failed", lastTestFailure = TestFailure.DNS)

    @Test fun firstNetworkNeverTrustsPersistedHealth() {
        val restored = NetworkHealthCache().switchTo(1L, listOf(working, failed), 100L)
        assertEquals(listOf("vless://saved", "vless://failed"), restored.map { it.rawConfig })
        assertTrue(restored.all { !it.isWorking && it.ping == -1 && it.lastTestFailure == null })
    }

    @Test fun changingNetworksDoesNotLeakSuccessOrFailure() {
        val cache = NetworkHealthCache()
        cache.switchTo(1L, listOf(working, failed), 100L)
        cache.record(working, 200L)
        cache.record(failed, 200L)
        val mobile = cache.switchTo(2L, listOf(working, failed), 300L)
        assertTrue(mobile.all { !it.isWorking && it.ping == -1 && it.lastTestFailure == null })
    }

    @Test fun returningRestoresOnlyHealthForMatchingLinks() {
        val cache = NetworkHealthCache()
        val httpFailure = failed.copy(lastTestFailure = TestFailure.HTTP_STATUS, lastHttpStatus = 403)
        cache.switchTo(1L, listOf(working, httpFailure), 100L)
        cache.record(working, 200L)
        cache.record(httpFailure, 200L)
        val otherNetwork = cache.switchTo(2L, listOf(working, httpFailure), 300L)
        val renamed = otherNetwork[0].copy(name = "Renamed", id = "preserved-id")
        val imported = VpnConfig("trojan://new-import", ProtocolType.TROJAN, "Imported", ping = 7, isWorking = true)
        val restored = cache.switchTo(1L, listOf(renamed, otherNetwork[1], imported), 400L)
        assertEquals(listOf("Renamed", "Failed", "Imported"), restored.map { it.name })
        assertEquals("preserved-id", restored[0].id)
        assertEquals(42, restored[0].ping)
        assertTrue(restored[0].isWorking)
        assertEquals(TestFailure.HTTP_STATUS, restored[1].lastTestFailure)
        assertEquals(403, restored[1].lastHttpStatus)
        assertFalse(restored[2].isWorking)
        assertEquals(-1, restored[2].ping)
    }

    @Test fun oneHourOldSnapshotsAreUnverified() {
        val cache = NetworkHealthCache()
        cache.switchTo(1L, listOf(working), 100L)
        cache.record(working, 200L)
        val mobile = cache.switchTo(2L, listOf(working), 300L)
        val restored = cache.switchTo(1L, mobile, 3_600_200L)
        assertFalse(restored.single().isWorking)
        assertEquals(-1, restored.single().ping)
    }

    @Test fun testingAnotherLinkDoesNotRenewAnOlderMeasurement() {
        val cache = NetworkHealthCache()
        val second = VpnConfig("trojan://second", ProtocolType.TROJAN, "Second", ping = 15, isWorking = true)
        cache.switchTo(1L, listOf(working, second), 0L)
        cache.record(working, 0L)
        cache.record(second, 50 * 60_000L)
        val mobile = cache.switchTo(2L, listOf(working, second), 51 * 60_000L)
        val restored = cache.switchTo(1L, mobile, 70 * 60_000L)
        assertFalse("Testing Second must not keep Saved's 70-minute-old result fresh", restored[0].isWorking)
        assertEquals(-1, restored[0].ping)
        assertTrue(restored[1].isWorking)
        assertEquals(15, restored[1].ping)
    }

    @Test fun visitingMoreThanThreeNetworksEvictsOldestHealth() {
        val cache = NetworkHealthCache()
        cache.switchTo(1L, listOf(working), 100L)
        cache.record(working, 200L)
        var configs = cache.switchTo(2L, listOf(working), 300L)
        configs = cache.switchTo(3L, configs, 400L)
        configs = cache.switchTo(4L, configs, 500L)
        assertFalse(cache.switchTo(1L, configs, 600L).single().isWorking)
    }

    @Test fun aFullProfileEvictsTheOldestMeasurementAfterAnEarlierLinkIsRetested() {
        val cache = NetworkHealthCache()
        fun server(index: Int) = VpnConfig("vless://candidate-$index", ProtocolType.VLESS,
            "Candidate $index", ping = 5, isWorking = true)
        cache.switchTo(1L, emptyList(), 0L)
        repeat(2_000) { index -> cache.record(server(index), index.toLong()) }
        cache.record(server(0), 3_000L)
        cache.record(server(2_000), 3_001L)
        val configs = listOf(server(0), server(1), server(2_000))
        val mobile = cache.switchTo(2L, configs, 3_002L)
        val restored = cache.switchTo(1L, mobile, 3_003L)
        assertTrue("A recently retested link must survive eviction", restored[0].isWorking)
        assertFalse("The oldest measurement must make room when the profile is full", restored[1].isWorking)
        assertEquals(-1, restored[1].ping)
        assertTrue(restored[2].isWorking)
    }

    @Test fun offlineClearsHealthAndDoesNotOverwritePhysicalNetworkSnapshot() {
        val cache = NetworkHealthCache()
        cache.switchTo(1L, listOf(working), 100L)
        cache.record(working, 200L)
        val offline = cache.switchTo(null, listOf(working), 300L)
        assertFalse(offline.single().isWorking)
        assertEquals(-1, offline.single().ping)
        assertEquals(42, cache.switchTo(1L, offline, 400L).single().ping)
    }

    @Test fun repeatedCallbacksForTheSameNetworkKeepCurrentResults() {
        val cache = NetworkHealthCache()
        cache.switchTo(1L, listOf(working), 100L)
        assertEquals(listOf(working), cache.switchTo(1L, listOf(working), 200L))
    }
}
