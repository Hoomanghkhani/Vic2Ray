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
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
            val socket = Socket(proxy)
            // Let the remote proxy resolve the DNS to avoid local DNS issues
            val dest = InetSocketAddress.createUnresolved("www.google.com", 80)
            
            socket.connect(dest, 3000)
            socket.close()
            
            val endTime = System.currentTimeMillis()
            return@withContext (endTime - startTime).toInt()
        } catch (e: Exception) {
            android.util.Log.e("RealPingTester", "Ping failed: ${e.message}")
            return@withContext -1
        }
    }
}
