package com.vic2ray.tester

import com.vic2ray.models.TestFailure
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

internal data class ProbeResult(val ping: Int = -1, val failure: TestFailure? = null,
    val httpStatus: Int? = null)

object RealPingTester {
    suspend fun testProxyPing(port: Int = 10808): Int = testProxy(port).ping

    internal suspend fun testProxy(port: Int = 10808): ProbeResult {
        require(port in 1..65535)
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            val connection = URL("https://www.gstatic.com/generate_204").openConnection(proxy) as HttpURLConnection
            withTimeoutOrNull(10_000) { measureProbe(connection) }
                ?: ProbeResult(failure = TestFailure.TIMEOUT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ProbeResult(failure = classify(error))
        }
    }

    internal suspend fun measureConnection(connection: HttpURLConnection): Int = measureProbe(connection).ping

    internal suspend fun measureProbe(connection: HttpURLConnection): ProbeResult = coroutineScope {
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
                    ProbeResult(ping = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                        .coerceIn(1, Int.MAX_VALUE.toLong()).toInt(), httpStatus = responseCode)
                } else ProbeResult(failure = TestFailure.HTTP_STATUS, httpStatus = responseCode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ProbeResult(failure = classify(error))
            } finally {
                connection.disconnect()
            }
        }
        try { request.await() } finally {
            request.cancel()
            connection.disconnect()
        }
    }

    private fun classify(error: Exception): TestFailure = when (error) {
        is UnknownHostException -> TestFailure.DNS
        is SocketTimeoutException -> TestFailure.TIMEOUT
        is SSLException -> TestFailure.TLS
        else -> TestFailure.CONNECTION
    }

    suspend fun testCurrentConnectionPing(): Int = testProxyPing(10808)
}
