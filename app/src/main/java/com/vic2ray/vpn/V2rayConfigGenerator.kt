package com.vic2ray.vpn

import android.net.Uri
import android.util.Base64
import com.vic2ray.models.ProtocolType
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

object V2rayConfigGenerator {
    
    fun generateJsonConfig(rawConfig: String, protocol: ProtocolType, forTest: Boolean = false): String {
        return when (protocol) {
            ProtocolType.VMESS -> buildVmessJson(rawConfig, forTest)
            ProtocolType.VLESS -> buildVlessJson(rawConfig, forTest)
            ProtocolType.TROJAN -> buildTrojanJson(rawConfig, forTest)
            ProtocolType.SS -> buildShadowsocksJson(rawConfig, forTest)
            else -> throw IllegalArgumentException("پروتکل ${protocol.name} در حال حاضر پشتیبانی نمی‌شود.")
        }
    }

    private fun getBaseTemplate(forTest: Boolean): JSONObject {
        val template = JSONObject()

        // Match v2rayNG loglevel
        template.put("log", JSONObject().apply {
            put("loglevel", if (forTest) "debug" else "warning")
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
            put("port", if (forTest) 10808 else 10808)
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
                    put("mtu", 1500)
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

    private fun buildStreamSettings(
        net: String, tls: String, sni: String, host: String, path: String, flow: String = "",
        pbk: String = "", fp: String = "", sid: String = "", spx: String = "", alpn: String = ""
    ): JSONObject {
        val streamSettings = JSONObject()
        val actualNet = if (net.isEmpty()) "tcp" else net
        streamSettings.put("network", actualNet)
        
        if (tls == "tls" || tls == "xtls" || tls == "reality") {
            streamSettings.put("security", tls)
            val tlsSettings = JSONObject().apply {
                if (sni.isNotEmpty()) put("serverName", sni)
                else if (host.isNotEmpty()) put("serverName", host)
                put("allowInsecure", true)
                
                if (alpn.isNotEmpty()) {
                    val alpnArray = JSONArray()
                    alpn.split(",").forEach { alpnArray.put(it.trim()) }
                    put("alpn", alpnArray)
                }

                if (tls == "reality") {
                    if (pbk.isNotEmpty()) put("publicKey", pbk)
                    if (fp.isNotEmpty()) put("fingerprint", fp)
                    if (sid.isNotEmpty()) put("shortId", sid)
                    if (spx.isNotEmpty()) put("spiderX", spx)
                } else {
                    if (fp.isNotEmpty()) put("fingerprint", fp)
                }
            }
            if (tls == "tls") streamSettings.put("tlsSettings", tlsSettings)
            if (tls == "xtls") streamSettings.put("xtlsSettings", tlsSettings)
            if (tls == "reality") streamSettings.put("realitySettings", tlsSettings)
        }

        if (actualNet == "ws") {
            streamSettings.put("wsSettings", JSONObject().apply {
                put("path", if (path.isEmpty()) "/" else path)
                if (host.isNotEmpty()) {
                    put("headers", JSONObject().apply {
                        put("Host", host)
                    })
                }
            })
        } else if (actualNet == "grpc") {
            streamSettings.put("grpcSettings", JSONObject().apply {
                put("serviceName", path)
                put("multiMode", false)
            })
        }
        return streamSettings
    }

    private fun buildVmessJson(vmessUrl: String, forTest: Boolean): String {
        var base64Part = vmessUrl.removePrefix("vmess://")
        
        // Remove everything after # if exists (remarks in URI)
        val hashIndex = base64Part.indexOf("#")
        if (hashIndex != -1) {
            base64Part = base64Part.substring(0, hashIndex)
        }

        val jsonString = try {
            String(Base64.decode(base64Part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
        } catch (e: Exception) {
            // Fallback to default decode if URL_SAFE fails
            String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
        }
        
        val vmessParams = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            // If it's still not a valid JSON, try to fix common truncation issues in remarks
            if (jsonString.contains("\"ps\":\"")) {
                val fixedJson = jsonString.substringBefore(",\"ps\":") + "}"
                JSONObject(fixedJson)
            } else {
                throw e
            }
        }

        val add = vmessParams.optString("add")
        val port = vmessParams.optString("port").toIntOrNull() ?: 443
        val id = vmessParams.optString("id")
        val aid = vmessParams.optString("aid", "0").toIntOrNull() ?: 0
        val net = vmessParams.optString("net", "tcp")
        val host = vmessParams.optString("host", "")
        val path = vmessParams.optString("path", "")
        val tls = vmessParams.optString("tls", "")
        val sni = vmessParams.optString("sni", host)

        val alpn = vmessParams.optString("alpn", "")
        val fp = vmessParams.optString("fp", "")
        val pbk = vmessParams.optString("pbk", "")
        val sid = vmessParams.optString("sid", "")
        val spx = vmessParams.optString("spx", "")

        val template = getBaseTemplate(forTest)
        
        val proxyOutbound = JSONObject().apply {
            put("protocol", "vmess")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", add)
                    put("port", port)
                    put("users", JSONArray().put(JSONObject().apply {
                        put("id", id)
                        put("alterId", aid)
                        put("security", "auto")
                    }))
                }))
            })
            put("streamSettings", buildStreamSettings(net, tls, sni, host, path, "", pbk, fp, sid, spx, alpn))
        }
        
        addOutbounds(template, proxyOutbound)
        return template.toString()
    }

    private fun buildVlessJson(vlessUrl: String, forTest: Boolean): String {
        val withoutScheme = vlessUrl.removePrefix("vless://")
        val atIndex = withoutScheme.indexOf("@")
        
        var id = ""
        var hostPortPath = withoutScheme
        
        if (atIndex != -1) {
            id = withoutScheme.substring(0, atIndex)
            id = URLDecoder.decode(id, "UTF-8")
            hostPortPath = withoutScheme.substring(atIndex + 1)
        }
        
        // Use http scheme to ensure Android Uri parses the host, port, and query properly
        val uri = Uri.parse("http://$hostPortPath")
        val add = uri.host ?: ""
        val port = uri.port.takeIf { it > 0 } ?: 443
        
        val encryption = uri.getQueryParameter("encryption") ?: "none"
        val security = uri.getQueryParameter("security") ?: ""
        val net = uri.getQueryParameter("type") ?: "tcp"
        val host = uri.getQueryParameter("host") ?: ""
        val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName") ?: ""
        val sni = uri.getQueryParameter("sni") ?: ""
        val flow = uri.getQueryParameter("flow") ?: ""
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val fp = uri.getQueryParameter("fp") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""
        val spx = uri.getQueryParameter("spx") ?: ""
        val alpn = uri.getQueryParameter("alpn") ?: ""

        val template = getBaseTemplate(forTest)

        val proxyOutbound = JSONObject().apply {
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", add)
                    put("port", port)
                    put("users", JSONArray().put(JSONObject().apply {
                        put("id", id)
                        put("encryption", encryption)
                        if (flow.isNotEmpty()) put("flow", flow)
                    }))
                }))
            })
            put("streamSettings", buildStreamSettings(net, security, sni, host, path, flow, pbk, fp, sid, spx, alpn))
        }

        addOutbounds(template, proxyOutbound)
        return template.toString()
    }

    private fun buildTrojanJson(trojanUrl: String, forTest: Boolean): String {
        val withoutScheme = trojanUrl.removePrefix("trojan://")
        val atIndex = withoutScheme.indexOf("@")
        
        var password = ""
        var hostPortPath = withoutScheme
        
        if (atIndex != -1) {
            password = withoutScheme.substring(0, atIndex)
            password = URLDecoder.decode(password, "UTF-8")
            hostPortPath = withoutScheme.substring(atIndex + 1)
        }
        
        // Use http scheme to ensure Android Uri parses the host, port, and query properly
        val uri = Uri.parse("http://$hostPortPath")
        val add = uri.host ?: ""
        val port = uri.port.takeIf { it > 0 } ?: 443
        
        val security = uri.getQueryParameter("security") ?: "tls"
        val net = uri.getQueryParameter("type") ?: "tcp"
        val host = uri.getQueryParameter("host") ?: ""
        val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName") ?: ""
        val sni = uri.getQueryParameter("sni") ?: ""
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val fp = uri.getQueryParameter("fp") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""
        val spx = uri.getQueryParameter("spx") ?: ""
        val alpn = uri.getQueryParameter("alpn") ?: ""

        val template = getBaseTemplate(forTest)

        val proxyOutbound = JSONObject().apply {
            put("protocol", "trojan")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().put(JSONObject().apply {
                    put("address", add)
                    put("port", port)
                    put("password", password)
                }))
            })
            put("streamSettings", buildStreamSettings(net, security, sni, host, path, "", pbk, fp, sid, spx, alpn))
        }

        addOutbounds(template, proxyOutbound)
        return template.toString()
    }

    private fun buildShadowsocksJson(ssUrl: String, forTest: Boolean): String {
        var base64Part = ssUrl.removePrefix("ss://")
        val hashIndex = base64Part.indexOf("#")
        if (hashIndex != -1) {
            base64Part = base64Part.substring(0, hashIndex)
        }

        var method = ""
        var password = ""
        var add = ""
        var port = 0

        try {
            if (base64Part.contains("@")) {
                // Format: ss://method:password@host:port
                // OR ss://base64(method:password)@host:port
                val parts = base64Part.split("@")
                val userInfo = parts[0]
                val serverInfo = parts[1]

                if (userInfo.contains(":")) {
                    val userParts = userInfo.split(":")
                    method = userParts[0]
                    password = userParts[1]
                } else {
                    val decodedUserInfo = String(Base64.decode(userInfo, Base64.DEFAULT), Charsets.UTF_8)
                    if (decodedUserInfo.contains(":")) {
                        val userParts = decodedUserInfo.split(":")
                        method = userParts[0]
                        password = userParts[1]
                    }
                }

                if (serverInfo.contains(":")) {
                    val serverParts = serverInfo.split(":")
                    add = serverParts[0]
                    port = serverParts[1].toIntOrNull() ?: 443
                }
            } else {
                // Format: ss://base64(method:password@host:port)
                val decoded = String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
                val atIndex = decoded.lastIndexOf("@")
                if (atIndex != -1) {
                    val creds = decoded.substring(0, atIndex)
                    val server = decoded.substring(atIndex + 1)
                    
                    if (creds.contains(":")) {
                        val credParts = creds.split(":")
                        method = credParts[0]
                        password = credParts[1]
                    }
                    
                    if (server.contains(":")) {
                        val serverParts = server.split(":")
                        add = serverParts[0]
                        port = serverParts[1].toIntOrNull() ?: 443
                    }
                }
            }
            
            if (method.isEmpty() || add.isEmpty() || port == 0) {
                throw IllegalArgumentException("Malformed SS link")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("فرمت لینک Shadowsocks نامعتبر است.")
        }

        val template = getBaseTemplate(forTest)

        val proxyOutbound = JSONObject().apply {
            put("protocol", "shadowsocks")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().put(JSONObject().apply {
                    put("address", add)
                    put("port", port)
                    put("method", method)
                    put("password", password)
                }))
            })
        }

        addOutbounds(template, proxyOutbound)
        return template.toString()
    }
}
