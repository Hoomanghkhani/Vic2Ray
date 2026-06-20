package com.vic2ray.fetcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ConfigFetcher {

    suspend fun fetchAllRawConfigs(sources: List<String>): List<String> = withContext(Dispatchers.IO) {
        val rawLines = mutableListOf<String>()

        for (url in sources) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000 // 10 seconds timeout
                connection.readTimeout = 10000 // 10 seconds timeout
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                    val content = reader.readText()
                    reader.close()

                    val lines = content.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    rawLines.addAll(lines)
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext rawLines.distinct()
    }
}
