package com.vic2ray.core

import com.vic2ray.models.ProtocolType
import com.vic2ray.parser.ConfigParser
import com.vic2ray.vpn.V2rayConfigGenerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigRegressionTest {
    private val uuid = "11111111-1111-4111-8111-111111111111"

    @Test fun shadowsocksPasswordKeepsColons() {
        val config = V2rayConfigGenerator.generateJsonConfig(
            "ss://aes-128-gcm:secret:10808:tail@example.com:443#Example", ProtocolType.SS, true
        )
        val server = JSONObject(config).getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("secret:10808:tail", server.getString("password"))
    }

    @Test fun malformedLinksAreRejectedBeforeTheyReachTheCore() {
        val configs = ConfigParser().parseConfigs(listOf(
            "vless://not-a-valid-proxy-identifier-with-more-than-thirty-bytes@server.example:443",
            "trojan://@server.example:443",
            "ss://aes-128-gcm:password@:443",
            "vless://11111111-1111-4111-8111-111111111111@example.com:99999"
        ))
        assertTrue(configs.isEmpty())
    }

    @Test fun duplicateLinksAppearOnce() {
        val link = "ss://aes-128-gcm:password@example.com:443#Example"
        assertEquals(1, ConfigParser().parseConfigs(listOf(link, link)).size)
    }

    @Test fun shadowsocksSupportsIpv6AndEscapedPassword() {
        val json = generate("ss://aes-128-gcm:p%40ss%3Aword%2Btail@[2001:db8::1]:8443#IPv6", ProtocolType.SS)
        val server = json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
            .getJSONArray("servers").getJSONObject(0)
        assertEquals("2001:db8::1", server.getString("address"))
        assertEquals(8443, server.getInt("port"))
        assertEquals("p@ss:word+tail", server.getString("password"))
    }

    @Test fun wrappedUrlSafeSubscriptionKeepsUnicodeNames() {
        val link = "vless://$uuid@example.com:443?security=tls#%D8%AA%D9%87%D8%B1%D8%A7%D9%86%20%2B%20Europe"
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(link.toByteArray())
        val configs = ConfigParser().parseConfigs(listOf(encoded.chunked(19).joinToString("\n")))
        assertEquals(1, configs.size)
        assertEquals("تهران + Europe", configs.single().name)
        assertEquals(link, configs.single().rawConfig)
    }

    @Test fun separateBase64DocumentsAreNeverConcatenated() {
        val first = "ss://aes-128-gcm:p@example.com:443#A"
        val second = "ss://aes-128-gcm:p@example.com:443#BB"
        val encoder = java.util.Base64.getEncoder().withoutPadding()
        val configs = ConfigParser().parseConfigs(listOf(
            encoder.encodeToString(first.toByteArray()), encoder.encodeToString(second.toByteArray())
        ))
        assertEquals(listOf(first, second), configs.map { it.rawConfig })
    }

    @Test fun legacyEncodedShadowsocksKeepsWholePassword() {
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-128-gcm:a:b@c@[2001:db8::1]:8443".toByteArray())
        val json = generate("ss://$encoded#Legacy", ProtocolType.SS)
        val server = json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
            .getJSONArray("servers").getJSONObject(0)
        assertEquals("a:b@c", server.getString("password"))
        assertEquals("2001:db8::1", server.getString("address"))
    }

    @Test fun tlsVerifiesCertificatesUnlessExplicitlyDisabled() {
        fun tls(query: String) = generate("trojan://password@example.com:443?$query", ProtocolType.TROJAN)
            .getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings").getJSONObject("tlsSettings")
        assertFalse(tls("security=tls").getBoolean("allowInsecure"))
        assertFalse(tls("security=tls&allowInsecure=0").getBoolean("allowInsecure"))
        assertTrue(tls("security=tls&allowInsecure=1").getBoolean("allowInsecure"))
    }

    @Test fun assigningTestPortPreservesCredentialsAndRemotePort() {
        val json = JSONObject(V2rayConfigGenerator.generateJsonConfig(
            "ss://aes-128-gcm:secret10808tail@example.com:10808", ProtocolType.SS, forTest = true, socksPort = 14567
        ))
        assertEquals(14567, json.getJSONArray("inbounds").getJSONObject(0).getInt("port"))
        val server = json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
            .getJSONArray("servers").getJSONObject(0)
        assertEquals("secret10808tail", server.getString("password"))
        assertEquals(10808, server.getInt("port"))
    }

    @Test fun upperCaseSchemeIsNormalizedBeforeDeduplication() {
        val configs = ConfigParser().parseConfigs(listOf(
            "SS://aes-128-gcm:password@example.com:443#One",
            "ss://aes-128-gcm:password@example.com:443#One"
        ))
        assertEquals(1, configs.size)
        assertTrue(configs.single().rawConfig.startsWith("ss://"))
    }

    @Test fun vmessUrlSafeJsonPreservesWebsocketPath() {
        val vmess = """{"add":"example.com","port":443,"id":"$uuid","net":"ws","tls":"tls","path":"/tunnel?ed=2048","ps":"تهران"}"""
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(vmess.toByteArray())
        val json = generate("vmess://$encoded", ProtocolType.VMESS)
        val stream = json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
        assertEquals("/tunnel?ed=2048", stream.getJSONObject("wsSettings").getString("path"))
        assertFalse(stream.getJSONObject("tlsSettings").getBoolean("allowInsecure"))
        assertEquals("تهران", ConfigParser().parseConfigs(listOf("vmess://$encoded")).single().name)
    }

    @Test fun unsupportedPluginsAreRejectedInsteadOfSilentlyConnectingWithoutThem() {
        assertTrue(ConfigParser().parseConfigs(listOf(
            "ss://aes-128-gcm:password@example.com:443?plugin=obfs-local%3Bobfs%3Dhttp"
        )).isEmpty())
    }

    @Test fun customShortXrayIdentifiersRemainUsable() {
        val configs = ConfigParser().parseConfigs(listOf("vless://my-server-id@example.com:443?security=tls"))
        assertEquals(1, configs.size)
        val user = generate(configs.single().rawConfig, ProtocolType.VLESS).getJSONArray("outbounds")
            .getJSONObject(0).getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)
        assertEquals("my-server-id", user.getString("id"))
    }

    private fun generate(link: String, protocol: ProtocolType): JSONObject =
        JSONObject(V2rayConfigGenerator.generateJsonConfig(link, protocol, forTest = true))
}
