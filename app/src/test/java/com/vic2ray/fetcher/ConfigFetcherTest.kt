package com.vic2ray.fetcher

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConfigFetcherTest {
    @Test fun `documents preserve each originating source even when response bodies match`() = runBlocking {
        val first = "https://first.example/sub"
        val second = "https://second.example/sub"
        val body = "vless://shared@example.org:443#Shared"
        val connections = listOf(first, second).associateWith { Response(it, body) }
        val fetcher = ConfigFetcher { url -> connections.getValue(url.toString()) }

        val result = fetcher.fetchSources(listOf(first, second, first))

        assertEquals(listOf(SourceDocument(first, body), SourceDocument(second, body)), result.documents)
        assertEquals(listOf(body), result.rawLines)
        assertTrue(result.failedSources.isEmpty())
        assertTrue(connections.values.all { it.disconnected })
    }

    @Test fun `failed HTTP source is reported and disconnected without losing successful documents`() = runBlocking {
        val successful = "https://successful.example/sub"
        val failed = "https://failed.example/sub"
        val body = "vless://working@example.org:443#Working"
        val connections = mapOf(successful to Response(successful, body),
            failed to Response(failed, status = HttpURLConnection.HTTP_UNAVAILABLE))
        val fetcher = ConfigFetcher { url -> connections.getValue(url.toString()) }

        val result = fetcher.fetchSources(listOf(failed, successful))

        assertEquals(listOf(SourceDocument(successful, body)), result.documents)
        assertEquals(listOf(failed), result.failedSources)
        assertTrue(connections.values.all { it.disconnected })
    }

    @Test fun `fragment and invalid port sources fail before opening a connection`() = runBlocking {
        val opened = mutableListOf<String>()
        val fetcher = ConfigFetcher { url ->
            opened.add(url.toString())
            Response(url.toString(), "vless://unexpected@example.org:443")
        }
        val invalid = listOf("https://example.org/sub#fragment", "https://example.org:99999/sub")

        val result = fetcher.fetchSources(invalid)

        assertEquals(invalid, result.failedSources)
        assertTrue(result.documents.isEmpty())
        assertTrue(opened.isEmpty())
    }

    @Test fun `redirect to a fragment URL fails and disconnects before opening invalid target`() = runBlocking {
        val source = "https://example.org/sub"
        val redirect = Response(source, status = HttpURLConnection.HTTP_MOVED_TEMP,
            redirect = "https://example.org/other#fragment")
        val opened = mutableListOf<String>()
        val fetcher = ConfigFetcher { url ->
            opened.add(url.toString())
            redirect
        }

        val result = fetcher.fetchSources(listOf(source))

        assertEquals(listOf(source), result.failedSources)
        assertTrue(result.documents.isEmpty())
        assertEquals(listOf(source), opened)
        assertTrue(redirect.disconnected)
    }

    @Test(timeout = 5_000) fun `cancelling a blocked fetch disconnects its active connection`() = runBlocking {
        val source = "https://blocked.example/sub"
        val response = BlockingResponse(source)
        val fetcher = ConfigFetcher { response }
        val request = launch(Dispatchers.Default) { fetcher.fetchSources(listOf(source)) }
        try {
            assertTrue("The source must be reading before cancellation", response.readStarted.await(2, TimeUnit.SECONDS))
            withTimeout(2_000) { request.cancelAndJoin() }
            assertTrue(response.disconnected)
        } finally {
            response.disconnect()
            request.cancelAndJoin()
        }
    }

    private class Response(address: String, body: String = "", private val status: Int = HTTP_OK,
        private val redirect: String? = null) : HttpURLConnection(URL(address)) {
        private val bytes = body.toByteArray(Charsets.UTF_8)
        @Volatile var disconnected = false
            private set

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?): String? = if (name == "Location") redirect else null
    }

    private class BlockingResponse(address: String) : HttpURLConnection(URL(address)) {
        val readStarted = CountDownLatch(1)
        private val closed = CountDownLatch(1)
        @Volatile var disconnected = false
            private set

        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
            closed.countDown()
        }
        override fun usingProxy() = false
        override fun getResponseCode() = HTTP_OK
        override fun getContentLengthLong() = -1L
        override fun getInputStream() = object : InputStream() {
            override fun read(): Int {
                readStarted.countDown()
                check(closed.await(4, TimeUnit.SECONDS)) { "Connection was not disconnected" }
                return -1
            }
        }
    }
}
