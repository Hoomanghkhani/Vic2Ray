package com.vic2ray.fetcher

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class FetchResult(val rawLines: List<String>, val failedSources: List<String>)

class ConfigFetcher {
    /** Each entry keeps a whole source document, including wrapped Base64 subscriptions. */
    suspend fun fetchSources(sources: List<String>): FetchResult = coroutineScope {
        val permits = Semaphore(3)
        val results = sources.distinct().map { source ->
            async {
                permits.withPermit {
                    try {
                        source to withTimeoutOrNull(20_000) { fetchSource(source) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        source to null
                    }
                }
            }
        }.awaitAll()
        FetchResult(
            rawLines = results.mapNotNull { it.second }.distinct(),
            failedSources = results.filter { it.second == null }.map { it.first }
        )
    }

    suspend fun fetchAllRawConfigs(sources: List<String>): List<String> = fetchSources(sources).rawLines

    private suspend fun fetchSource(source: String): String {
        var address = source
        repeat(4) { redirectCount ->
            val uri = URI(address)
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null) {
                "Subscriptions must use a valid HTTPS URL."
            }
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "text/plain, */*;q=0.5")
            connection.setRequestProperty("User-Agent", "Vic2Ray/1.1")
            val response = cancellableRequest(connection) {
                val status = it.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = it.getHeaderField("Location") ?: throw IOException("Missing redirect URL")
                    SourceResponse(redirect = uri.resolve(location).toString())
                } else {
                    if (status != HttpURLConnection.HTTP_OK) throw IOException("Source returned HTTP $status")
                    if (it.contentLengthLong > MAX_SOURCE_BYTES) throw IOException("Subscription is too large")
                    val bytes = ByteArrayOutputStream()
                    it.inputStream.use { input ->
                        val buffer = ByteArray(8_192)
                        var total = 0
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_SOURCE_BYTES) throw IOException("Subscription is too large")
                            bytes.write(buffer, 0, count)
                        }
                    }
                    val body = bytes.toString(Charsets.UTF_8.name()).trim().removePrefix("\uFEFF")
                    if (body.isBlank()) throw IOException("Subscription is empty")
                    SourceResponse(body = body)
                }
            }
            response.body?.let { return it }
            if (redirectCount == 3) throw IOException("Too many subscription redirects")
            address = requireNotNull(response.redirect)
        }
        throw IOException("Unable to read subscription")
    }

    private suspend fun <T> cancellableRequest(
        connection: HttpURLConnection,
        block: suspend (HttpURLConnection) -> T
    ): T = coroutineScope {
        val request = async(Dispatchers.IO) {
            try { block(connection) } finally { connection.disconnect() }
        }
        try {
            request.await()
        } finally {
            // Closing from the waiting coroutine also interrupts a blocking network read on cancel.
            request.cancel()
            connection.disconnect()
        }
    }

    private data class SourceResponse(val body: String? = null, val redirect: String? = null)
    private companion object { const val MAX_SOURCE_BYTES = 2 * 1024 * 1024 }
}
