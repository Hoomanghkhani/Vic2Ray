package com.vic2ray.tester

import com.vic2ray.models.VpnConfig
import com.vic2ray.models.ProtocolType
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import android.net.Uri
import android.util.Base64
import org.json.JSONObject

class ConnectivityTester {

    /**
     * تست پینگ ساده سرور برای بررسی زنده بودن (تایم اوت ۲ ثانیه).
     * نکته: در یک اپلیکیشن واقعی باید با خود Core تست شود. در اینجا پینگ ساده به هاست/پورت یا فقط شبیه‌سازی انجام می‌شود.
     */
    suspend fun testConfig(config: VpnConfig): VpnConfig = withContext(Dispatchers.IO) {
        var host = ""
        var port = 443

        try {
            when (config.protocol) {
                ProtocolType.VMESS -> {
                    val base64Part = config.rawConfig.removePrefix("vmess://")
                    val jsonString = String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
                    val json = JSONObject(jsonString)
                    host = json.optString("add", "")
                    port = json.optString("port", "443").toIntOrNull() ?: 443
                }
                else -> {
                    val uri = Uri.parse(config.rawConfig)
                    var uriHost = uri.host ?: ""
                    // Handle case where URI parsing fails (e.g. missing '//' or bad password)
                    if (uriHost.isEmpty() && config.rawConfig.contains("@")) {
                        val partAfterAt = config.rawConfig.substringAfter("@")
                        uriHost = partAfterAt.substringBefore(":")
                        val portStr = partAfterAt.substringAfter(":").substringBefore("?")
                        port = portStr.toIntOrNull() ?: 443
                    }
                    host = uriHost
                    if (uri.port > 0) port = uri.port
                }
            }
        } catch (e: Exception) {
            // parsing failed
        }

        var isSuccess = false
        var pingResult = -1
        val startTime = System.currentTimeMillis()

        if (host.isNotEmpty()) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 2000)
                socket.close()
                isSuccess = true
            } catch (e: Exception) {
                isSuccess = false
            }
        }

        val endTime = System.currentTimeMillis()
        if (isSuccess) {
            pingResult = (endTime - startTime).toInt()
        }

        config.copy(
            ping = pingResult,
            isWorking = isSuccess
        )
    }

    /**
     * تست تمامی سرورها به صورت موازی (Parallel)
     */
    suspend fun testAllStreaming(
        configs: List<VpnConfig>,
        onResult: (VpnConfig) -> Unit
    ) = withContext(Dispatchers.IO) {
        // ایجاد جاب‌های موازی با محدودیت اجرای همزمان (استفاده از async)
        // برای جلوگیری از کرش سیستم، کانکشن‌ها در دسته‌های 50 تایی اجرا می‌شوند
        configs.chunked(50).forEach { chunk ->
            val deferreds = chunk.map { config ->
                async {
                    val result = testConfig(config)
                    if (result.isWorking) {
                        withContext(Dispatchers.Main) {
                            onResult(result)
                        }
                    }
                }
            }
            deferreds.awaitAll()
        }
    }
}
