package com.vic2ray.tester

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object RealPingTester {
    suspend fun testCurrentConnectionPing(): Int = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            android.util.Log.d("RealPingTester", "Attempting HTTP ping via proxy 127.0.0.1:10809...")
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809))
            // Use Google's reliable 204 endpoint over HTTPS
            val url = URL("https://www.google.com/generate_204")
            
            val connection = url.openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            
            android.util.Log.d("RealPingTester", "Connecting to google.com via HTTP proxy...")
            val responseCode = connection.responseCode
            android.util.Log.d("RealPingTester", "Response Code: $responseCode")
            
            val endTime = System.currentTimeMillis()
            val ping = (endTime - startTime).toInt()
            android.util.Log.d("RealPingTester", "Ping success: $ping ms")
            return@withContext ping
        } catch (e: Exception) {
            android.util.Log.e("RealPingTester", "Ping failed! Exception: ${e.javaClass.simpleName}, Message: ${e.message}")
            return@withContext -1
        }
    }
}
