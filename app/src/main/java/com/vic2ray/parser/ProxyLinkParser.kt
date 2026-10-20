package com.vic2ray.parser

import com.vic2ray.models.ProtocolType
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID

internal data class ProxyLink(
    val protocol: ProtocolType,
    val address: String,
    val port: Int,
    val credential: String,
    val name: String,
    val parameters: Map<String, String>,
    val cipher: String = ""
)

/** Shared validation for imports and core generation; URI decoding preserves literal plus signs. */
internal object ProxyLinkParser {
    fun parse(raw: String): ProxyLink {
        val protocol = ProtocolType.fromString(raw)
        require(protocol != ProtocolType.UNKNOWN) { "Unsupported proxy protocol." }
        val payload = raw.substringAfter("://")
        val encodedName = payload.substringAfter('#', "")
        val content = payload.substringBefore('#')
        if (protocol == ProtocolType.VMESS) return parseVmess(content)
        val parameters = queryParameters(content.substringAfter('?', ""))
        require(parameters["plugin"].isNullOrEmpty()) { "Shadowsocks plugins are not supported." }
        val body = content.substringBefore('?').removeSuffix("/")
        val authority = if (protocol == ProtocolType.SS && !body.contains('@')) decodeBase64(body) else body
        val separator = authority.lastIndexOf('@')
        require(separator > 0) { "The proxy credentials or server address are missing." }
        val encodedCredentials = authority.substring(0, separator)
        val endpoint = URI("http://${authority.substring(separator + 1)}")
        val address = endpoint.host?.removeSurrounding("[", "]").orEmpty()
        require(address.isNotBlank() && endpoint.rawPath.isNullOrEmpty()) { "The server address is invalid." }
        val port = if (endpoint.port == -1 && protocol != ProtocolType.SS) 443 else endpoint.port
        require(port in 1..65535) { "The server port must be between 1 and 65535." }
        var credentials = decodeComponent(encodedCredentials)
        var cipher = ""
        if (protocol == ProtocolType.SS) {
            if (!credentials.contains(':')) credentials = decodeBase64(credentials)
            val colon = credentials.indexOf(':')
            require(colon > 0) { "The Shadowsocks credentials are invalid." }
            cipher = credentials.substring(0, colon)
            credentials = credentials.substring(colon + 1)
            require(cipher in supportedCiphers) { "This Shadowsocks cipher is not supported." }
        }
        require(credentials.isNotBlank() && credentials.none { it.isISOControl() }) { "The proxy credentials are missing or invalid." }
        if (protocol == ProtocolType.VLESS) requireXrayId(credentials)
        validateTransport(parameters["type"] ?: "tcp", parameters["security"] ?: if (protocol == ProtocolType.TROJAN) "tls" else "none")
        val name = decodeComponent(encodedName).ifBlank { "${protocol.name} Server" }
        return ProxyLink(protocol, address, port, credentials, name, parameters, cipher)
    }

    private fun parseVmess(content: String): ProxyLink {
        val json = JSONObject(decodeBase64(content))
        val address = json.optString("add").trim().removeSurrounding("[", "]")
        require(address.isNotBlank() && address.none { it.isWhitespace() || it == '/' || it == '@' }) { "The VMess server address is invalid." }
        val port = json.optString("port").toIntOrNull() ?: throw IllegalArgumentException("The VMess port is invalid.")
        require(port in 1..65535) { "The server port must be between 1 and 65535." }
        val id = json.optString("id")
        requireXrayId(id)
        val values = json.keys().asSequence().associateWith { json.optString(it) }
        val transport = values["net"]?.ifBlank { "tcp" } ?: "tcp"
        val security = values["tls"]?.ifBlank { "none" } ?: "none"
        validateTransport(transport, security)
        return ProxyLink(ProtocolType.VMESS, address, port, id, json.optString("ps").ifBlank { "VMESS Server" }, values)
    }

    private fun requireXrayId(value: String) {
        val customId = value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size in 1..30
        val uuid = runCatching { UUID.fromString(value).toString().equals(value, true) }.getOrDefault(false)
        require(customId || uuid || value.matches(Regex("[0-9a-fA-F]{32}"))) {
            "The VMess/VLESS identifier is invalid."
        }
    }

    private fun validateTransport(transport: String, security: String) {
        require(transport.lowercase(Locale.ROOT) in supportedTransports) { "This proxy transport is not supported." }
        require(security.lowercase(Locale.ROOT) in setOf("", "none", "tls", "reality")) { "This proxy security mode is not supported." }
    }

    private fun queryParameters(query: String): Map<String, String> = query.split('&').filter { it.isNotBlank() }
        .associate { decodeComponent(it.substringBefore('=')) to decodeComponent(it.substringAfter('=', "")) }

    fun decodeComponent(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")

    // Android's java.util.Base64 requires API 26. This small decoder also works on API 24/25 and JVM tests.
    fun decodeBase64(encoded: String): String {
        val input = encoded.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/')
        val content = input.trimEnd('=')
        require(content.isNotEmpty() && content.length % 4 != 1 && input.length - content.length <= 2) { "Invalid Base64 subscription." }
        require(!content.contains('=') && (input.length == content.length || input.length % 4 == 0)) { "Invalid Base64 padding." }
        val output = ByteArray(content.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var index = 0
        for (character in content) {
            val digit = alphabet.indexOf(character)
            require(digit >= 0) { "Invalid Base64 character." }
            buffer = (buffer shl 6) or digit
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output[index++] = (buffer shr bits).toByte()
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        require(buffer == 0) { "Invalid Base64 trailing bits." }
        return String(output, 0, index, Charsets.UTF_8)
    }

    private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val supportedTransports = setOf("tcp", "raw", "ws", "grpc", "httpupgrade", "xhttp", "splithttp")
    private val supportedCiphers = setOf(
        "aes-128-gcm", "aes-256-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305", "none", "plain"
    )
}
