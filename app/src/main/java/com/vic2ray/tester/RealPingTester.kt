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
            // Use Google connectivity check as it's usually more stable in various regions
            val url = URL("http://connectivitycheck.gstatic.com/generate_204")
            
            val connection = url.openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = 6000 // 6 seconds for better stability in Iran
            connection.readTimeout = 6000
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            if (responseCode >= 200 && responseCode < 400) {
                val endTime = System.currentTimeMillis()
                return@withContext (endTime - startTime).toInt()
            } else {
                return@withContext -1
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("RealPingTester", "Ping failed on port $port: ${e.message}", e)
            return@withContext -1
        }
    }
    
    // Legacy support for VicVpnService
    suspend fun testCurrentConnectionPing(): Int = testProxyPing(10808)
}
