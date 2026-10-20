package com.vic2ray.models

import java.net.URI
import java.util.Locale

enum class ServerSort { LATENCY, NAME }

object ServerCatalog {
    fun visible(configs: List<VpnConfig>, query: String = "", protocol: ProtocolType? = null,
        favorites: Set<String> = emptySet(), favoritesOnly: Boolean = false,
        sort: ServerSort = ServerSort.LATENCY): List<VpnConfig> {
        val search = query.trim()
        val filtered = configs.filter { config ->
            (protocol == null || config.protocol == protocol) &&
                (!favoritesOnly || config.rawConfig in favorites) &&
                (search.isEmpty() || config.name.contains(search, ignoreCase = true) ||
                    config.protocol.name.contains(search, ignoreCase = true))
        }
        val byName = compareBy<VpnConfig> { it.name.lowercase(Locale.ROOT) }.thenBy { it.rawConfig }
        return when (sort) {
            ServerSort.NAME -> filtered.sortedWith(byName)
            ServerSort.LATENCY -> filtered.sortedWith(compareBy<VpnConfig> {
                if (it.isWorking && it.ping > 0) it.ping else Int.MAX_VALUE
            }.then(byName))
        }
    }

    /** Incoming results update matching links without discarding the offline catalog. */
    fun merge(existing: List<VpnConfig>, incoming: List<VpnConfig>): List<VpnConfig> {
        val merged = LinkedHashMap<String, VpnConfig>()
        (existing + incoming).forEach { config -> merged[config.rawConfig] = config }
        return merged.values.toList()
    }

    fun isValidSource(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65535)
    } catch (_: Exception) {
        false
    }

    /** Each source owns its first-seen links so duplicate prefixes cannot consume another source's turn. */
    fun interleaveSources(configsBySource: List<List<VpnConfig>>): List<VpnConfig> {
        val seen = HashSet<String>()
        val sources = java.util.ArrayDeque<Iterator<VpnConfig>>()
        configsBySource.forEach { configs ->
            val unique = configs.filter { seen.add(it.rawConfig) }
            if (unique.isNotEmpty()) sources.addLast(unique.iterator())
        }
        val result = ArrayList<VpnConfig>(seen.size)
        while (sources.isNotEmpty()) {
            val source = sources.removeFirst()
            result.add(source.next())
            if (source.hasNext()) sources.addLast(source)
        }
        return result
    }

    fun scanCandidates(configs: List<VpnConfig>, priorityLinks: Set<String> = emptySet(),
        offset: Int = 0, limit: Int = 300): List<VpnConfig> {
        if (limit <= 0 || configs.isEmpty()) return emptyList()
        val unique = configs.distinctBy { it.rawConfig }
        val priority = unique.filter { it.rawConfig in priorityLinks }
        val remaining = unique.filterNot { it.rawConfig in priorityLinks }
        // Priority links lead a complete pass; rotate the full queue so large imports cannot starve it.
        val queue = priority + remaining
        val start = offset.coerceAtLeast(0) % queue.size
        return (queue.drop(start) + queue.take(start)).take(limit)
    }
}
