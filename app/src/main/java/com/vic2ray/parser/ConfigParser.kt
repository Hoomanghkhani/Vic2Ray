package com.vic2ray.parser

import com.vic2ray.models.ProtocolType
import com.vic2ray.models.VpnConfig
import java.util.Locale

class ConfigParser {
    fun parseConfigs(rawLines: List<String>): List<VpnConfig> {
        return rawLines.asSequence().flatMap { document ->
            val trimmed = document.trim().removePrefix("\uFEFF")
            val text = if (trimmed.contains("://")) trimmed else {
                runCatching { ProxyLinkParser.decodeBase64(trimmed) }.getOrDefault("")
            }
            text.lineSequence()
        }.map { it.trim() }
            .filter { ProtocolType.fromString(it) != ProtocolType.UNKNOWN }
            .map { it.substringBefore("://").lowercase(Locale.ROOT) + "://" + it.substringAfter("://") }
            .distinct()
            .mapNotNull { raw ->
                runCatching {
                    val link = ProxyLinkParser.parse(raw)
                    VpnConfig(rawConfig = raw, protocol = link.protocol, name = link.name)
                }.getOrNull()
            }.toList()
    }
}
