package com.vic2ray.models

data class VpnConfig(
    val rawConfig: String,
    val protocol: ProtocolType,
    val name: String,
    val id: String = java.util.UUID.nameUUIDFromBytes(rawConfig.toByteArray(Charsets.UTF_8)).toString(),
    val ping: Int = -1, // -1 means untested or timeout
    val isWorking: Boolean = false,
    val lastTestFailure: TestFailure? = null,
    val lastHttpStatus: Int? = null
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
