package com.vic2ray.vpn

import android.net.Uri
import android.util.Base64
import com.vic2ray.models.ProtocolType
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

object V2rayConfigGenerator {
    
    fun generateJsonConfig(rawConfig: String, protocol: ProtocolType): String {
        return when (protocol) {
            ProtocolType.VMESS -> buildVmessJson(rawConfig)
            ProtocolType.VLESS -> buildVlessJson(rawConfig)
            ProtocolType.TROJAN -> buildTrojanJson(rawConfig)
            ProtocolType.SS -> buildShadowsocksJson(rawConfig)
            else -> throw IllegalArgumentException("پروتکل ${protocol.name} در حال حاضر پشتیبانی نمی‌شود.")
        }
    }

    private fun getBaseTemplate(): JSONObject {
        val template = JSONObject()
        
        template.put("log", JSONObject().apply {
            put("loglevel", "debug")
        })

        val inbounds = JSONArray()
        val socksInbound = JSONObject().apply {
            put("port", 10808)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls"))
            })
        }
        val httpInbound = JSONObject().apply {
            put("port", 10809)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject())
        }
        val tunInbound = JSONObject().apply {
            put("port", 10810) // Port is ignored for tun, but required by schema
            put("listen", "127.0.0.1")
            put("protocol", "tun")
            put("settings", JSONObject().apply {
                put("name", "tun0")
                put("mtu", 1500)
                put("autoRoute", false)
                put("strictRoute", false)
            })
            put("tag", "tun")
        }
        inbounds.put(socksInbound)
        inbounds.put(httpInbound)
        inbounds.put(tunInbound)
        template.put("inbounds", inbounds)

        // Explicit empty routing to avoid V2Ray loading default rules that depend on geoip/geosite
        template.put("routing", JSONObject().apply {
            put("domainStrategy", "AsIs")
            put("rules", JSONArray())
        })

        return template
    }

    private fun addOutbounds(template: JSONObject, proxyOutbound: JSONObject) {
        val outbounds = JSONArray()
        outbounds.put(proxyOutbound)
        outbounds.put(JSONObject().apply {
            put("protocol", "freedom")
            put("tag", "direct")
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

    private fun buildVmessJson(vmessUrl: String): String {
        val base64Part = vmessUrl.removePrefix("vmess://")
        val jsonString = String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
        val vmessParams = JSONObject(jsonString)

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

        val template = getBaseTemplate()
        
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

    private fun buildVlessJson(vlessUrl: String): String {
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

        val template = getBaseTemplate()

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

    private fun buildTrojanJson(trojanUrl: String): String {
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

        val template = getBaseTemplate()

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

    private fun buildShadowsocksJson(ssUrl: String): String {
        var base64Part = ssUrl.removePrefix("ss://")
        val hashIndex = base64Part.indexOf("#")
        if (hashIndex != -1) {
            base64Part = base64Part.substring(0, hashIndex)
        }

        // SS format 1: ss://base64(method:password)@hostname:port
        // SS format 2: ss://base64(method:password@hostname:port)
        
        var method = ""
        var password = ""
        var add = ""
        var port = 0

        try {
            if (base64Part.contains("@")) {
                val parts = base64Part.split("@")
                val decodedCreds = String(Base64.decode(parts[0], Base64.DEFAULT), Charsets.UTF_8)
                val credParts = decodedCreds.split(":")
                method = credParts[0]
                password = credParts[1]
                
                val serverParts = parts[1].split(":")
                add = serverParts[0]
                port = serverParts[1].toInt()
            } else {
                val decoded = String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
                val atIndex = decoded.lastIndexOf("@")
                val creds = decoded.substring(0, atIndex)
                val server = decoded.substring(atIndex + 1)
                
                val credParts = creds.split(":")
                method = credParts[0]
                password = credParts[1]
                
                val serverParts = server.split(":")
                add = serverParts[0]
                port = serverParts[1].toInt()
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("فرمت لینک Shadowsocks نامعتبر است.")
        }

        val template = getBaseTemplate()

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
