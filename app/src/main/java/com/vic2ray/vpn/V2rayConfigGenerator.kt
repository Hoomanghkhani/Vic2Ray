package com.vic2ray.vpn

import com.vic2ray.models.ProtocolType
import com.vic2ray.parser.ProxyLink
import com.vic2ray.parser.ProxyLinkParser
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object V2rayConfigGenerator {
    
    fun generateJsonConfig(
        rawConfig: String,
        protocol: ProtocolType,
        forTest: Boolean = false,
        socksPort: Int = 10808
    ): String {
        require(socksPort in 1..65535) { "The local SOCKS port is invalid." }
        val link = ProxyLinkParser.parse(rawConfig)
        require(link.protocol == protocol) { "The proxy protocol does not match the link." }
        val template = getBaseTemplate(forTest, socksPort)
        val server = JSONObject().put("address", link.address).put("port", link.port)
        val settings = JSONObject()
        when (protocol) {
            ProtocolType.VMESS, ProtocolType.VLESS -> {
                val user = JSONObject().put("id", link.credential)
                if (protocol == ProtocolType.VMESS) {
                    user.put("alterId", link.parameters["aid"]?.toIntOrNull() ?: 0)
                    user.put("security", link.parameters["scy"]?.ifBlank { "auto" } ?: "auto")
                } else {
                    user.put("encryption", link.parameters["encryption"]?.ifBlank { "none" } ?: "none")
                    link.parameters["flow"]?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
                }
                server.put("users", JSONArray().put(user))
                settings.put("vnext", JSONArray().put(server))
            }
            ProtocolType.TROJAN -> {
                server.put("password", link.credential)
                settings.put("servers", JSONArray().put(server))
            }
            ProtocolType.SS -> {
                server.put("method", link.cipher).put("password", link.credential)
                settings.put("servers", JSONArray().put(server))
            }
            else -> error("Unsupported proxy protocol.")
        }
        val outbound = JSONObject()
            .put("protocol", if (protocol == ProtocolType.SS) "shadowsocks" else protocol.name.lowercase(Locale.ROOT))
            .put("settings", settings)
        if (protocol != ProtocolType.SS) outbound.put("streamSettings", buildStreamSettings(link))
        addOutbounds(template, outbound)
        return template.toString()
    }

    private fun getBaseTemplate(forTest: Boolean, socksPort: Int): JSONObject {
        val template = JSONObject()

        // Match v2rayNG loglevel
        template.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        // Policy: connection timeouts critical for messaging apps (long-lived TCP connections)
        template.put("policy", JSONObject().apply {
            put("levels", JSONObject().apply {
                put("8", JSONObject().apply {
                    put("handshake", 4)
                    put("connIdle", 300)
                    put("uplinkOnly", 1)
                    put("downlinkOnly", 1)
                })
            })
            put("system", JSONObject().apply {
                put("statsOutboundUplink", false)
                put("statsOutboundDownlink", false)
            })
        })

        val inbounds = JSONArray()
        val socksInbound = JSONObject().apply {
            put("tag", "socks")
            put("port", socksPort)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
                put("userLevel", 8)
            })
            // Simple sniffing without fakedns - matches v2rayNG default
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                put("routeOnly", false)
            })
        }
        inbounds.put(socksInbound)

        if (!forTest) {
            val httpInbound = JSONObject().apply {
                put("tag", "http")
                put("port", 10809)
                put("listen", "127.0.0.1")
                put("protocol", "http")
                put("settings", JSONObject().apply {
                    put("userLevel", 8)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                    put("routeOnly", false)
                })
            }
            val tunInbound = JSONObject().apply {
                put("tag", "tun")
                put("protocol", "tun")
                put("settings", JSONObject().apply {
                    put("name", "tun0")
                    put("mtu", 1280)
                    put("address", JSONArray().put("10.0.0.1/24"))
                    put("autoRoute", false)
                    put("strictRoute", false)
                    put("stack", "gvisor")
                    put("fd", -1)
                    put("androidTunFd", -1)
                    put("tunFd", -1)
                    put("tun-fd", -1)
                    put("userLevel", 8)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                    put("routeOnly", false)
                })
            }
            inbounds.put(httpInbound)
            inbounds.put(tunInbound)
        }

        template.put("inbounds", inbounds)

        // Simple, proven DNS config - NO FakeDNS (causes messaging app failures)
        template.put("dns", JSONObject().apply {
            put("hosts", JSONObject().apply {
                // Force Google domains to known IPs to avoid DNS issues
                put("dns.google", "8.8.8.8")
            })
            val servers = JSONArray()
            // Primary: Google DNS via proxy
            servers.put("8.8.8.8")
            // Secondary: Cloudflare
            servers.put("1.1.1.1")
            // Fallback
            servers.put("8.8.4.4")
            put("servers", servers)
            // Force IPv4 to avoid IPv6 issues with free proxies
            put("queryStrategy", "UseIPv4")
        })

        // Clean routing - matches v2rayNG proven pattern
        template.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            val rules = JSONArray()

            // 1. Route DNS (port 53) through proxy outbound (critical for messaging apps)
            rules.put(JSONObject().apply {
                put("type", "field")
                put("port", "53")
                put("outboundTag", "dns-out")
            })

            // 2. Private/Local IPs go direct
            rules.put(JSONObject().apply {
                put("type", "field")
                put("ip", JSONArray().apply {
                    put("127.0.0.0/8")
                    put("10.0.0.0/8")
                    put("172.16.0.0/12")
                    put("192.168.0.0/16")
                    put("fc00::/7")
                    put("fe80::/10")
                })
                put("outboundTag", "direct")
            })

            // 3. .ir domains go direct
            rules.put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray().apply {
                    put("domain:ir")
                    put("keyword:cafebazaar")
                    put("keyword:snapp")
                    put("keyword:tapsi")
                    put("keyword:digikala")
                })
                put("outboundTag", "direct")
            })

            // 4. Block QUIC (UDP/443) - forces apps to use TCP through proxy
            // This fixes Instagram/Twitter chat which tries QUIC first
            rules.put(JSONObject().apply {
                put("type", "field")
                put("network", "udp")
                put("port", "443")
                put("outboundTag", "block")
            })

            put("rules", rules)
        })

        return template
    }

    private fun addOutbounds(template: JSONObject, proxyOutbound: JSONObject) {
        val outbounds = JSONArray()
        
        // Ensure proxy outbound has a tag
        if (!proxyOutbound.has("tag")) {
            proxyOutbound.put("tag", "proxy")
        }
        
        outbounds.put(proxyOutbound)
        outbounds.put(JSONObject().apply {
            put("protocol", "dns")
            put("tag", "dns-out")
        })
        outbounds.put(JSONObject().apply {
            put("protocol", "freedom")
            put("tag", "direct")
            put("settings", JSONObject().apply {
                put("domainStrategy", "UseIPv4")
            })
        })
        outbounds.put(JSONObject().apply {
            put("protocol", "blackhole")
            put("tag", "block")
        })
        template.put("outbounds", outbounds)
    }

    private fun buildStreamSettings(link: ProxyLink): JSONObject {
        val parameters = link.parameters
        val network = (parameters[if (link.protocol == ProtocolType.VMESS) "net" else "type"] ?: "tcp")
            .ifBlank { "tcp" }.lowercase(Locale.ROOT).let { if (it == "raw") "tcp" else it }
        val security = (parameters[if (link.protocol == ProtocolType.VMESS) "tls" else "security"]
            ?: if (link.protocol == ProtocolType.TROJAN) "tls" else "none").lowercase(Locale.ROOT)
        val host = parameters["host"].orEmpty()
        val path = parameters["path"].orEmpty()
        val stream = JSONObject().put("network", network)
        if (security == "tls" || security == "reality") {
            stream.put("security", security)
            val tls = JSONObject()
            val serverName = parameters["sni"]?.ifBlank { null } ?: host
            if (serverName.isNotBlank()) tls.put("serverName", serverName)
            val fingerprint = parameters["fp"]?.ifBlank { null } ?: if (security == "reality") "chrome" else null
            fingerprint?.let { tls.put("fingerprint", it) }
            parameters["alpn"]?.takeIf { it.isNotBlank() }?.let { value ->
                tls.put("alpn", JSONArray(value.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
            }
            if (security == "reality") {
                val publicKey = parameters["pbk"]?.ifBlank { null } ?: parameters["publicKey"]
                require(!publicKey.isNullOrBlank()) { "The REALITY public key is missing." }
                tls.put("publicKey", publicKey)
                parameters["sid"]?.let { tls.put("shortId", it) }
                parameters["spx"]?.let { tls.put("spiderX", it) }
            } else {
                val insecure = parameters["allowInsecure"] ?: parameters["insecure"] ?: "false"
                tls.put("allowInsecure", insecure.equals("true", true) || insecure == "1")
            }
            stream.put(if (security == "reality") "realitySettings" else "tlsSettings", tls)
        }
        when (network) {
            "ws" -> stream.put("wsSettings", JSONObject().apply {
                put("path", path.ifBlank { "/" })
                if (host.isNotBlank()) put("headers", JSONObject().put("Host", host))
            })
            "grpc" -> stream.put("grpcSettings", JSONObject().apply {
                put("serviceName", parameters["serviceName"] ?: path)
                put("multiMode", parameters["mode"] == "multi" || parameters["multiMode"] == "true")
                parameters["authority"]?.let { put("authority", it) }
            })
            "httpupgrade" -> stream.put("httpupgradeSettings", JSONObject().apply {
                put("path", path.ifBlank { "/" })
                if (host.isNotBlank()) put("host", host)
            })
            "xhttp", "splithttp" -> stream.put("xhttpSettings", JSONObject().apply {
                put("path", path.ifBlank { "/" })
                if (host.isNotBlank()) put("host", host)
                put("mode", parameters["mode"]?.ifBlank { "auto" } ?: "auto")
            })
            "tcp" -> {
                val headerType = parameters["headerType"] ?: if (link.protocol == ProtocolType.VMESS) parameters["type"] else null
                require(headerType.isNullOrBlank() || headerType == "none" || headerType == "http") { "This TCP header type is not supported." }
                if (headerType == "http") stream.put("tcpSettings", JSONObject().put("header", JSONObject().apply {
                    put("type", "http")
                    put("request", JSONObject().apply {
                        put("path", JSONArray().put(path.ifBlank { "/" }))
                        if (host.isNotBlank()) put("headers", JSONObject().put("Host", JSONArray(host.split(','))))
                    })
                }))
            }
        }
        return stream
    }
}
