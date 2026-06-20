package com.vic2ray.models

data class VpnConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawConfig: String,
    val protocol: ProtocolType,
    val name: String,
    var ping: Int = -1, // -1 means untested or timeout
    var isWorking: Boolean = false
)

enum class ProtocolType {
    VMESS,
    VLESS,
    TROJAN,
    SS,
    UNKNOWN;

    companion object {
        fun fromString(url: String): ProtocolType {
            return when {
                url.startsWith("vmess://", ignoreCase = true) -> VMESS
                url.startsWith("vless://", ignoreCase = true) -> VLESS
                url.startsWith("trojan://", ignoreCase = true) -> TROJAN
                url.startsWith("ss://", ignoreCase = true) -> SS
                else -> UNKNOWN
            }
        }
    }
}
