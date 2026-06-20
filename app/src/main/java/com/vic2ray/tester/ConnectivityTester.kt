package com.vic2ray.tester

import com.vic2ray.models.VpnConfig
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

class ConnectivityTester {

    /**
     * تست پینگ ساده سرور برای بررسی زنده بودن (تایم اوت ۲ ثانیه).
     * نکته: در یک اپلیکیشن واقعی باید با خود Core تست شود. در اینجا پینگ ساده به هاست/پورت یا فقط شبیه‌سازی انجام می‌شود.
     */
    suspend fun testConfig(config: VpnConfig): VpnConfig = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var isSuccess = false
        var pingResult = -1

        try {
            // شبیه‌سازی تست واقعی
            delay((50..1500).random().toLong())
            isSuccess = Math.random() > 0.4 // شانس 60 درصدی برای موفقیت برای نمایش
            
        } catch (e: Exception) {
            isSuccess = false
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
