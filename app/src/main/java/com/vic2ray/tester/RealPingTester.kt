package com.vic2ray.tester

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object RealPingTester {
    /**
     * تست پینگ واقعی از طریق پروکسی محلی SOCKS
     * این متد تلاش می‌کند یک درخواست HTTP به کلودفلر ارسال کند.
     * اگر موفقیت‌آمیز باشد، یعنی پروکسی واقعاً کار می‌کند.
     */
    suspend fun testProxyPing(port: Int = 10808): Int = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            // استفاده از HTTPS برای جلوگیری از محدودیت‌های Cleartext در اندروید
            val url = URL("https://cp.cloudflare.com/generate_204")
            
            val connection = url.openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = 4000 // 4 seconds
            connection.readTimeout = 4000
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            if (responseCode >= 200 && responseCode < 400) {
                val endTime = System.currentTimeMillis()
                return@withContext (endTime - startTime).toInt()
            } else {
                return@withContext -1
            }
        } catch (e: Exception) {
            return@withContext -1
        }
    }
    
    // Legacy support for VicVpnService
    suspend fun testCurrentConnectionPing(): Int = testProxyPing(10808)
}
