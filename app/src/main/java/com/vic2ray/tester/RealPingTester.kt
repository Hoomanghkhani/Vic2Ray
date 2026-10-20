package com.vic2ray.tester

import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit

object RealPingTester {
    suspend fun testProxyPing(port: Int = 10808): Int {
        require(port in 1..65535)
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            val connection = URL("https://www.gstatic.com/generate_204").openConnection(proxy) as HttpURLConnection
            withTimeoutOrNull(10_000) { measureConnection(connection) } ?: -1
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            -1
        }
    }

    internal suspend fun measureConnection(connection: HttpURLConnection): Int = coroutineScope {
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        val request = async(Dispatchers.IO) {
            val start = System.nanoTime()
            try {
                val responseCode = connection.responseCode
                ensureActive()
                if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                        .coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
                } else -1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                -1
            } finally {
                connection.disconnect()
            }
        }
        try { request.await() } finally {
            request.cancel()
            connection.disconnect()
        }
    }

    suspend fun testCurrentConnectionPing(): Int = testProxyPing(10808)
}
