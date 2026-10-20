package com.vic2ray.tester

import android.content.Context
import com.vic2ray.models.VpnConfig
import com.vic2ray.utils.AssetsUtils
import com.vic2ray.vpn.V2rayConfigGenerator
import kotlinx.coroutines.*
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.net.InetAddress
import java.net.ServerSocket

class ConnectivityTester(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun testConfigReal(config: VpnConfig): VpnConfig = withContext(Dispatchers.IO) {
        var core: CoreController? = null
        var ping = -1
        try {
            ensureActive()
            AssetsUtils.copyGeoAssets(applicationContext)
            val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val json = V2rayConfigGenerator.generateJsonConfig(
                config.rawConfig, config.protocol, forTest = true, socksPort = port
            )
            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(status: Long, message: String?): Long = 0
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
            }
            core = Libv2ray.newCoreController(callback)
            core.startLoop(json, 0)
            ensureActive()
            if (core.isRunning) ping = RealPingTester.testProxyPing(port)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Treat an invalid/unreachable server as a failed test; never log private proxy links.
        } finally {
            runCatching { core?.stopLoop() }
        }
        config.copy(ping = ping, isWorking = ping > 0)
    }

    suspend fun testAllStreaming(
        configs: List<VpnConfig>,
        onResult: (VpnConfig) -> Unit
    ) = withContext(Dispatchers.IO) {
        AssetsUtils.copyGeoAssets(applicationContext)
        configs.chunked(6).forEach { chunk ->
            ensureActive()
            chunk.map { config ->
                async {
                    val result = testConfigReal(config)
                    withContext(Dispatchers.Main) { onResult(result) }
                }
            }.awaitAll()
        }
    }
}
