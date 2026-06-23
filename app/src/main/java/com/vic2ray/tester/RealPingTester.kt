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
            android.util.Log.d("RealPingTester", "Attempting ping via SOCKS proxy 127.0.0.1:10808...")
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
            // Use Cloudflare's HTTP 204 endpoint to avoid SSL negotiation overhead and CONNECT issues
            val url = URL("http://cp.cloudflare.com/generate_204")
            
            val connection = url.openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            
            android.util.Log.d("RealPingTester", "Connecting to cloudflare.com via SOCKS proxy...")
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
