package com.vic2ray.core

import com.vic2ray.tester.RealPingTester
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RealPingTesterTest {
    @Test fun onlyAnExact204CountsAsAWorkingConnection() = runBlocking {
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val okRequests = AtomicInteger()
        val serving = executor.submit {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    it.soTimeout = 2_000
                    val reader = it.getInputStream().bufferedReader()
                    val path = reader.readLine().split(' ')[1]
                    while (!reader.readLine().isNullOrEmpty()) { /* consume request headers */ }
                    val status = when (path) {
                        "/ok" -> { okRequests.incrementAndGet(); "204 No Content" }
                        "/redirect" -> "302 Found"
                        else -> "200 OK"
                    }
                    val location = if (path == "/redirect") "Location: /ok\r\n" else ""
                    val response = "HTTP/1.1 $status\r\n${location}Content-Length: 0\r\nConnection: close\r\n\r\n"
                    it.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                    it.getOutputStream().flush()
                }
            }
        }
        try {
            fun connection(path: String) = URL("http://127.0.0.1:${server.localPort}$path")
                .openConnection() as HttpURLConnection
            assertTrue(RealPingTester.measureConnection(connection("/ok")) > 0)
            assertEquals(-1, RealPingTester.measureConnection(connection("/portal")))
            assertEquals(-1, RealPingTester.measureConnection(connection("/redirect")))
            assertEquals(1, okRequests.get())
        } finally {
            server.close()
            try { serving.get(2, TimeUnit.SECONDS) } finally { executor.shutdownNow() }
        }
    }

    @Test fun cancellingAPingClosesItsBlockingHttpConnection() = runBlocking {
        val enteredRead = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val responses = AtomicInteger()
        val connection = object : HttpURLConnection(URL("http://localhost/")) {
            override fun connect() = Unit
            override fun usingProxy() = true
            override fun disconnect() { closed.countDown() }
            override fun getResponseCode(): Int {
                enteredRead.countDown()
                check(closed.await(3, TimeUnit.SECONDS)) { "The cancelled HTTP read stayed open" }
                responses.incrementAndGet()
                return 204
            }
        }
        val ping = async { RealPingTester.measureConnection(connection) }
        assertTrue(withContext(Dispatchers.IO) { enteredRead.await(2, TimeUnit.SECONDS) })
        withTimeout(1_000) { ping.cancelAndJoin() }
        assertTrue(ping.isCancelled)
        assertEquals(0L, closed.count)
        assertEquals(1, responses.get())
    }
}
