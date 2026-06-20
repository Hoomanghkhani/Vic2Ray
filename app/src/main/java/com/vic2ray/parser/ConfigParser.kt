package com.vic2ray.parser

import android.util.Base64
import com.vic2ray.models.ProtocolType
import com.vic2ray.models.VpnConfig
import org.json.JSONObject

class ConfigParser {

    /**
     * تبدیل لیست رشته‌های خام به مدل‌های VpnConfig
     */
    fun parseConfigs(rawLines: List<String>): List<VpnConfig> {
        val configs = mutableListOf<VpnConfig>()

        for (line in rawLines) {
            val decodedLine = tryDecodeBase64IfNeeded(line)
            
            // اگر خط شامل چندین کانفیگ است (مثلا با \n جدا شده‌اند پس از دی‌کد شدن)
            val individualConfigs = decodedLine.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            
            for (configUrl in individualConfigs) {
                val protocol = ProtocolType.fromString(configUrl)
                if (protocol != ProtocolType.UNKNOWN) {
                    val name = extractName(configUrl, protocol)
                    configs.add(
                        VpnConfig(
                            rawConfig = configUrl,
                            protocol = protocol,
                            name = name
                        )
                    )
                }
            }
        }

        return configs
    }

    /**
     * اگر رشته با پروتکل شروع نمی‌شود، ممکن است Base64 باشد.
     * سعی می‌کنیم آن را دی‌کد کنیم.
     */
    private fun tryDecodeBase64IfNeeded(line: String): String {
        return if (!line.contains("://")) {
            try {
                // برخی لیست‌ها Base64 هستند
                String(Base64.decode(line, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                line
            }
        } else {
            line
        }
    }

    /**
     * استخراج نام سرور (Remark) برای نمایش به کاربر
     */
    private fun extractName(url: String, protocol: ProtocolType): String {
        try {
            when (protocol) {
                ProtocolType.VMESS -> {
                    // vmess در فرمت base64 ذخیره می‌شود بعد از پیشوند
                    val base64Part = url.removePrefix("vmess://")
                    val jsonString = String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
                    val json = JSONObject(jsonString)
                    return json.optString("ps", "VMess Server")
                }
                ProtocolType.VLESS, ProtocolType.TROJAN -> {
                    // فرمت: protocol://uuid@host:port?params#name
                    val hashIndex = url.indexOf("#")
                    if (hashIndex != -1 && hashIndex < url.length - 1) {
                        // URL Decoder
                        return java.net.URLDecoder.decode(url.substring(hashIndex + 1), "UTF-8")
                    }
                    return "${protocol.name} Server"
                }
                ProtocolType.SS -> {
                    val hashIndex = url.indexOf("#")
                    if (hashIndex != -1 && hashIndex < url.length - 1) {
                        return java.net.URLDecoder.decode(url.substring(hashIndex + 1), "UTF-8")
                    }
                    return "Shadowsocks Server"
                }
                else -> return "Unknown Server"
            }
        } catch (e: Exception) {
            return "${protocol.name} Server (Parsed)"
        }
    }
}
