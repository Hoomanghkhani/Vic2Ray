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
            // V2Ray default local HTTP proxy in this app
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809))
            // Use an IP address directly to avoid local DNS resolution timing out in tun0 blackhole
            val url = URL("http://1.1.1.1/")
            val connection = url.openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            val endTime = System.currentTimeMillis()
            
            // Any response means the proxy is working and reachable
            if (responseCode > 0) {
                return@withContext (endTime - startTime).toInt()
            } else {
                return@withContext -1
            }
        } catch (e: Exception) {
            return@withContext -1
        }
    }
}
