package com.vic2ray.core

import com.vic2ray.models.TestFailure
import com.vic2ray.tester.RealPingTester
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ProbeDiagnosticsTest {
    @Test fun `successful endpoint retains status and measured latency`() = runBlocking {
        val response = Response { 204 }
        val result = RealPingTester.measureProbe(response)
        assertTrue(result.ping > 0)
        assertNull(result.failure)
        assertEquals(204, result.httpStatus)
        assertTrue(response.disconnected)
    }

    @Test fun `unexpected HTTP response is distinct from an unreachable proxy`() = runBlocking {
        val response = Response { 200 }
        val result = RealPingTester.measureProbe(response)
        assertEquals(-1, result.ping)
        assertEquals(TestFailure.HTTP_STATUS, result.failure)
        assertEquals(200, result.httpStatus)
        assertTrue(response.disconnected)
    }

    @Test fun `hostname resolution failure is visible without private exception text`() = runBlocking {
        val response = Response { throw UnknownHostException("private-proxy.example") }
        val result = RealPingTester.measureProbe(response)
        assertEquals(TestFailure.DNS, result.failure)
        assertFalse(result.toString().contains("private-proxy.example"))
        assertTrue(response.disconnected)
    }

    @Test fun `socket timeout is reported separately from TLS failure`() = runBlocking {
        val result = RealPingTester.measureProbe(Response { throw SocketTimeoutException() })
        assertEquals(TestFailure.TIMEOUT, result.failure)
    }

    @Test fun `certificate rejection is reported as TLS failure`() = runBlocking {
        val result = RealPingTester.measureProbe(Response { throw SSLHandshakeException("certificate") })
        assertEquals(TestFailure.TLS, result.failure)
    }

    @Test fun `other connection errors remain private and never become successful`() = runBlocking {
        val result = RealPingTester.measureProbe(Response { throw IOException("private credential") })
        assertEquals(-1, result.ping)
        assertEquals(TestFailure.CONNECTION, result.failure)
        assertNull(result.httpStatus)
        assertFalse(result.toString().contains("private credential"))
    }

    private class Response(private val status: () -> Int) : HttpURLConnection(URL("https://probe.example/")) {
        var disconnected = false
            private set
        override fun connect() = Unit
        override fun usingProxy() = true
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = status()
    }
}
