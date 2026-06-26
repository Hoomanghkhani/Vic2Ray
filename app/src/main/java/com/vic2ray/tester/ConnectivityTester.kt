package com.vic2ray.tester

import com.vic2ray.models.VpnConfig
import com.vic2ray.vpn.V2rayConfigGenerator
import kotlinx.coroutines.*
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicInteger

class ConnectivityTester(private val context: android.content.Context) {
    private val nextPort = AtomicInteger(11000)

    init {
        // Ensure core is initialized when tester is created
        com.vic2ray.utils.AssetsUtils.initCore(context)
    }

    /**
     * تست پینگ واقعی سرور با اجرای موقت هسته Xray
     * این متد هسته را برای هر سرور به صورت مجزا استارت کرده و یک درخواست واقعی ارسال می‌کند.
     */
    suspend fun testConfigReal(config: VpnConfig): VpnConfig = withContext(Dispatchers.IO) {
        val testPort = nextPort.getAndIncrement()
        var pingResult = -1
        var isSuccess = false
        
        var core: libv2ray.CoreController? = null
        try {
            // تولید کانفیگ JSON برای تست با پورت اختصاصی - بدون لایه TUN
            val fullConfigJson = V2rayConfigGenerator.generateJsonConfig(config.rawConfig, config.protocol, forTest = true)
            // تغییر پورت پیش‌فرض ۱۰۸۰۸ به پورت تست اختصاصی برای جلوگیری از تداخل
            val testConfigJson = fullConfigJson.replace("10808", testPort.toString())

            val callback = object : libv2ray.CoreCallbackHandler {
                override fun onEmitStatus(status: Long, message: String?): Long {
                    android.util.Log.d("ConnectivityTester", "Core Status [${testPort}]: $status, $message")
                    return 0
                }
                override fun shutdown(): Long = 0
                override fun startup(): Long = 0
            }
            
            android.util.Log.d("ConnectivityTester", "Starting core on port $testPort for ${config.name}")
            core = Libv2ray.newCoreController(callback)
            // استارت هسته روی پورت تست
            core.startLoop(testConfigJson, 0)
            android.util.Log.d("ConnectivityTester", "Core started on port $testPort for ${config.name}")
            
            // صبر کوتاه برای بالا آمدن سرویس پروکسی
            delay(500)
            
            // انجام تست HTTP واقعی از طریق پورت تست
            pingResult = RealPingTester.testProxyPing(testPort)
            isSuccess = pingResult > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConnectivityTester", "Error testing ${config.name}: ${e.message}")
        } finally {
            // توقف هسته پس از اتمام تست یا در صورت خطا
            try {
                android.util.Log.d("ConnectivityTester", "Stopping core on port $testPort...")
                core?.stopLoop()
                android.util.Log.d("ConnectivityTester", "Core stopped on port $testPort.")
            } catch (e: Exception) {
                android.util.Log.e("ConnectivityTester", "Error stopping core on port $testPort", e)
            }
        }

        config.copy(
            ping = pingResult,
            isWorking = isSuccess
        )
    }

    /**
     * تست تمامی سرورها به صورت موازی با پینگ واقعی
     */
    suspend fun testAllStreaming(
        configs: List<VpnConfig>,
        onResult: (VpnConfig) -> Unit
    ) = withContext(Dispatchers.IO) {
        // برای تست سریع‌تر، تست‌ها در دسته‌های ۶ تایی اجرا می‌شوند
        configs.chunked(6).forEach { chunk ->
            val deferreds = chunk.map { config ->
                async {
                    val result = testConfigReal(config)
                    if (result.isWorking) {
                        withContext(Dispatchers.Main) {
                            onResult(result)
                        }
                    }
                }
            }
            deferreds.awaitAll()
        }
    }
}
