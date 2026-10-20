package com.vic2ray.models

import org.junit.Assert.*
import org.junit.Test

class ServerCatalogTest {
    private fun config(name: String, ping: Int = 100, protocol: ProtocolType = ProtocolType.VLESS) =
        VpnConfig(rawConfig = "${protocol.name.lowercase()}://$name", protocol = protocol,
            name = name, ping = ping, isWorking = ping > 0)

    @Test fun `search protocol and favorites apply together`() {
        val favorite = config("تهران Gold", 80)
        val otherProtocol = config("Gold Trojan", 60, ProtocolType.TROJAN)
        val other = config("Gold Other", 40)
        val result = ServerCatalog.visible(listOf(favorite, otherProtocol, other),
            query = "  GOLD  ", protocol = ProtocolType.VLESS,
            favorites = setOf(favorite.rawConfig, otherProtocol.rawConfig), favoritesOnly = true)
        assertEquals(listOf(favorite), result)
    }

    @Test fun `untested and failed servers sort after measured servers`() {
        val slow = config("Slow", 500)
        val pending = config("Pending", -1)
        val fast = config("Fast", 25)
        val failed = config("Failed", 0)
        val result = ServerCatalog.visible(listOf(pending, slow, failed, fast))
        assertEquals(listOf(fast, slow), result.take(2))
        assertEquals(setOf(pending, failed), result.drop(2).toSet())
    }

    @Test fun `name sort ignores case and protocol null includes all`() {
        val b = config("beta", 10, ProtocolType.SS)
        val a = config("Alpha", 400)
        assertEquals(listOf(a, b), ServerCatalog.visible(listOf(b, a), sort = ServerSort.NAME))
        assertEquals(2, ServerCatalog.visible(listOf(b, a)).size)
    }

    @Test fun `merge replaces stale latency without losing imported links`() {
        val cached = config("Cached", 400)
        val imported = config("Imported", -1)
        val refreshed = cached.copy(ping = 30)
        val result = ServerCatalog.merge(listOf(cached, imported), listOf(refreshed, refreshed))
        assertEquals(2, result.size)
        assertEquals(30, result.single { it.rawConfig == cached.rawConfig }.ping)
        assertTrue(result.any { it.rawConfig == imported.rawConfig })
        assertEquals(result.map { it.id }.toSet().size, result.size)
    }

    @Test fun `empty refresh preserves cached servers`() {
        val cached = config("Offline")
        assertEquals(listOf(cached), ServerCatalog.merge(listOf(cached), emptyList()))
    }

    @Test fun `source requires HTTPS host and valid port without credentials`() {
        assertTrue(ServerCatalog.isValidSource("https://example.org/sub.txt?token=abc"))
        assertTrue(ServerCatalog.isValidSource("https://example.org:8443/sub"))
        listOf("http://example.org/sub", "file:///etc/passwd", "https://", "https://a b/sub",
            "https://user:password@example.org/sub", "https://example.org:99999/sub",
            "https://example.org/sub#fragment").forEach {
            assertFalse("Must reject $it", ServerCatalog.isValidSource(it))
        }
    }

    @Test fun `identical links have stable ids independent of mutable latency`() {
        val first = config("Stable", 20)
        val second = config("Stable", 400)
        assertEquals(first.id, second.id)
        assertNotEquals(first.id, config("Different").id)
    }

    @Test fun `scan is bounded and prioritizes imported or favorite links`() {
        val configs = (1..10).map { config("Server $it") }
        val selected = ServerCatalog.scanCandidates(configs,
            priorityLinks = setOf(configs.last().rawConfig), offset = 0, limit = 3)
        assertEquals(3, selected.size)
        assertEquals(configs.last(), selected.first())
        assertEquals(3, selected.distinctBy { it.rawConfig }.size)
    }

    @Test fun `successive scan pages reach servers beyond the first batch`() {
        val configs = (1..6).map { config("Server $it") }
        val first = ServerCatalog.scanCandidates(configs, offset = 0, limit = 3)
        val second = ServerCatalog.scanCandidates(configs, offset = 3, limit = 3)
        assertEquals(configs.size, (first + second).toSet().size)
        assertTrue(first.toSet().intersect(second.toSet()).isEmpty())
        assertTrue(ServerCatalog.scanCandidates(emptyList()).isEmpty())
    }

    @Test fun `large imported list does not starve later imports or subscriptions`() {
        val configs = (1..510).map { config("Server $it") }
        val priority = configs.take(500).map { it.rawConfig }.toSet()
        val first = ServerCatalog.scanCandidates(configs, priority, offset = 0, limit = 300)
        val second = ServerCatalog.scanCandidates(configs, priority, offset = 300, limit = 300)
        assertEquals(configs.take(300), first)
        assertEquals(configs.size, (first + second).toSet().size)
    }
}
