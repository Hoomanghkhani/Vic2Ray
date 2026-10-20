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

    @Test fun `first bounded scan gives each subscription a turn despite a large first source`() {
        val firstSource = (1..900).map { config("First $it") }
        val secondSource = (1..600).map { config("Second $it") }
        val thirdSource = (1..600).map { config("Third $it") }

        val ordered = ServerCatalog.interleaveSources(listOf(firstSource, secondSource, thirdSource))
        val firstPage = ServerCatalog.scanCandidates(ordered)

        assertEquals(300, firstPage.size)
        assertEquals(listOf("First 1", "Second 1", "Third 1", "First 2", "Second 2", "Third 2"),
            firstPage.take(6).map { it.name })
        assertEquals(100, firstPage.count { it.name.startsWith("First ") })
        assertEquals(100, firstPage.count { it.name.startsWith("Second ") })
        assertEquals(100, firstPage.count { it.name.startsWith("Third ") })
    }

    @Test fun `duplicate prefix cannot hide a later source unique links from the first scan`() {
        val shared = (1..400).map { config("Shared $it") }
        val laterUnique = (1..100).map { config("Later $it") }

        val ordered = ServerCatalog.interleaveSources(listOf(shared, shared + laterUnique))
        val firstPage = ServerCatalog.scanCandidates(ordered)

        assertEquals(listOf("Shared 1", "Later 1", "Shared 2", "Later 2", "Shared 3", "Later 3"),
            firstPage.take(6).map { it.name })
        assertEquals(100, firstPage.count { it.name.startsWith("Later ") })
        assertEquals(500, ordered.size)
        assertEquals(ordered.size, ordered.distinctBy { it.rawConfig }.size)
    }

    @Test fun `interleaving skips empty and exhausted groups and keeps first duplicate ownership`() {
        val first = config("First", 20)
        val second = config("Second", 30)
        val third = config("Third", 40)
        val ordered = ServerCatalog.interleaveSources(listOf(emptyList(),
            listOf(first, first.copy(ping = 900), second), emptyList(),
            listOf(first.copy(ping = 800), third), emptyList()))

        assertEquals(listOf(first, third, second), ordered)
        assertTrue(ServerCatalog.interleaveSources(emptyList()).isEmpty())
        assertTrue(ServerCatalog.interleaveSources(listOf(emptyList(), emptyList())).isEmpty())
    }

    @Test fun `successive bounded scans cover the full interleaved subscription queue`() {
        val firstSource = (1..400).map { config("First $it") }
        val secondSource = (1..300).map { config("Second $it") }
        val ordered = ServerCatalog.interleaveSources(listOf(firstSource, secondSource))
        val pages = listOf(0, 300, 600).flatMap { offset ->
            ServerCatalog.scanCandidates(ordered, offset = offset)
        }

        assertEquals(700, ordered.size)
        assertEquals((firstSource + secondSource).toSet(), pages.toSet())
        assertEquals(600, pages.take(600).distinctBy { it.rawConfig }.size)
        assertEquals(ordered.take(200), pages.drop(700))
    }
}
