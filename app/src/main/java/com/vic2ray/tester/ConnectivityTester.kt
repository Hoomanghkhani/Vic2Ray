package com.vic2ray.tester

import android.content.Context
import com.vic2ray.models.VpnConfig
import com.vic2ray.models.TestFailure
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
        var stage = TestFailure.INITIALIZATION
        var result = ProbeResult(failure = stage)
        try {
            ensureActive()
            AssetsUtils.copyGeoAssets(applicationContext)
            stage = TestFailure.CONFIGURATION
            val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val json = V2rayConfigGenerator.generateJsonConfig(
                config.rawConfig, config.protocol, forTest = true, socksPort = port
            )
            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(status: Long, message: String?): Long = 0
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
            }
            stage = TestFailure.CORE_START
            core = Libv2ray.newCoreController(callback)
            core.startLoop(json, 0)
            ensureActive()
            result = if (core.isRunning) RealPingTester.testProxy(port)
                else ProbeResult(failure = TestFailure.CORE_START)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            result = ProbeResult(failure = stage)
        } catch (_: LinkageError) {
            result = ProbeResult(failure = TestFailure.INITIALIZATION)
        } finally {
            runCatching { core?.stopLoop() }
        }
        config.copy(ping = result.ping, isWorking = result.ping > 0,
            lastTestFailure = result.failure, lastHttpStatus = result.httpStatus)
    }

    suspend fun testAllStreaming(
        configs: List<VpnConfig>,
        onResult: (VpnConfig) -> Unit
    ) = withContext(Dispatchers.IO) {
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
