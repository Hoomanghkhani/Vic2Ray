package com.vic2ray.tester

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

object RealPingTester {
    suspend fun testCurrentConnectionPing(): Int = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            // V2Ray default local SOCKS proxy in this app
            android.util.Log.d("RealPingTester", "Attempting to connect to proxy 127.0.0.1:10808...")
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
            val socket = Socket(proxy)
            // Let the remote proxy resolve the DNS to avoid local DNS issues
            val dest = InetSocketAddress.createUnresolved("www.google.com", 80)
            
            android.util.Log.d("RealPingTester", "Socket connect to www.google.com:80 via proxy...")
            socket.connect(dest, 3000)
            
            android.util.Log.d("RealPingTester", "Socket connected successfully!")
            socket.close()
            
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
