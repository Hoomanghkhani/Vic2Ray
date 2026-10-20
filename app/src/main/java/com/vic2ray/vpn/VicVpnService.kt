package com.vic2ray.vpn

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.vic2ray.tester.RealPingTester
import com.vic2ray.utils.AssetsUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class VicVpnService : VpnService() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var lifecycleJob: Job? = null
    private var pingJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null

    companion object {
        const val ACTION_CONNECT = "com.vic2ray.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.vic2ray.vpn.DISCONNECT"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_RAW_CONFIG = "raw_config"
        private const val CHANNEL_ID = "vic2ray_vpn"
        private const val NOTIFICATION_ID = 1
        private val tunFdEnvironmentKeys = listOf("xray.tun.fd", "v2ray.tun.fd", "XRAY_TUN_FD", "V2RAY_TUN_FD", "vpn.tun.fd")
        // Shared across service recreation, so a late teardown cannot overlap a new native startup.
        private val lifecycleMutex = Mutex()
        private val generation = AtomicLong()
        private var resourceOwner: VicVpnService? = null

        val isConnected = MutableStateFlow(false)
        val currentPing = MutableStateFlow(-1)
        val connectedRawConfig = MutableStateFlow<String?>(null)
        private val _isConnecting = MutableStateFlow(false)
        val isConnecting: StateFlow<Boolean> = _isConnecting
        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError
        private val _connectedSince = MutableStateFlow(0L)
        val connectedSince: StateFlow<Long> = _connectedSince
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config.isNullOrBlank()) {
                    requestDisconnect(startId, serviceText("Choose a server and connect again.", "یک سرور انتخاب کنید و دوباره متصل شوید."))
                } else if (prepare(this) != null) {
                    requestDisconnect(startId, serviceText("VPN permission is required. Please connect again.", "مجوز وی‌پی‌ان لازم است. دوباره متصل شوید."))
                } else {
                    try {
                        startVpnForeground()
                        requestConnect(config, intent.getStringExtra(EXTRA_RAW_CONFIG), startId)
                    } catch (_: Exception) {
                        requestDisconnect(startId, serviceText("Unable to start the VPN service. Please reconnect from the app.", "سرویس وی‌پی‌ان اجرا نشد. از داخل برنامه دوباره متصل شوید."))
                    }
                }
            }
            ACTION_DISCONNECT -> requestDisconnect(startId)
            else -> requestDisconnect(startId)
        }
        return START_NOT_STICKY
    }

    // Commands invalidate earlier work immediately; the mutex serializes blocking native calls.
    private fun requestConnect(jsonConfig: String, rawConfig: String?, startId: Int) {
        val token = generation.incrementAndGet()
        lifecycleJob?.cancel()
        pingJob?.cancel()
        resetConnectionState()
        _isConnecting.value = true
        connectedRawConfig.value = rawConfig
        _lastError.value = null
        lifecycleJob = serviceScope.launch {
            lifecycleMutex.withLock {
                resourceOwner?.closeResources()
                closeResources()
                resourceOwner = this@VicVpnService
                var failureMessage = serviceText("Unable to load the VPN data files. Please restart the app.", "فایل‌های وی‌پی‌ان بارگذاری نشد. برنامه را دوباره باز کنید.")
                try {
                    ensureActive()
                    AssetsUtils.copyGeoAssets(applicationContext)
                    ensureActive()
                    failureMessage = serviceText("Android could not establish the VPN interface. Please grant VPN permission again.", "رابط وی‌پی‌ان ایجاد نشد. مجوز وی‌پی‌ان را دوباره تأیید کنید.")
                    vpnInterface = Builder().apply {
                        setSession("Vic2Ray")
                        addAddress("10.0.0.2", 24)
                        addRoute("0.0.0.0", 0)
                        addAddress("fc00::2", 128)
                        addRoute("::", 0)
                        addDnsServer("1.1.1.1")
                        addDnsServer("8.8.8.8")
                        setMtu(1280)
                        setBlocking(false)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", 10809))
                            setMetered(false)
                        }
                        // Core sockets must use the physical network, outside their own VPN.
                        addDisallowedApplication(packageName)
                    }.establish() ?: error(failureMessage)
                    ensureActive()
                    val descriptor = requireNotNull(vpnInterface).fd
                    // The bundled Xray fork uses this environment key and the explicit startLoop FD.
                    tunFdEnvironmentKeys.forEach { android.system.Os.setenv(it, descriptor.toString(), true) }
                    val finalConfig = JSONObject(jsonConfig).apply {
                        val inbounds = getJSONArray("inbounds")
                        for (index in 0 until inbounds.length()) {
                            val inbound = inbounds.getJSONObject(index)
                            if (inbound.optString("protocol") == "tun") {
                                inbound.getJSONObject("settings").apply {
                                    put("fd", descriptor)
                                    put("androidTunFd", descriptor)
                                    put("tunFd", descriptor)
                                    put("tun-fd", descriptor)
                                }
                            }
                        }
                    }.toString()
                    val callback = object : CoreCallbackHandler {
                        override fun onEmitStatus(status: Long, message: String?): Long = 0
                        override fun startup(): Long = 0
                        override fun shutdown(): Long {
                            serviceScope.launch(Dispatchers.Main) {
                                if (generation.get() == token && (isConnected.value || _isConnecting.value)) {
                                    requestDisconnect(startId, serviceText("The VPN core stopped. Please reconnect.", "هسته وی‌پی‌ان متوقف شد. دوباره متصل شوید."))
                                }
                            }
                            return 0
                        }
                    }
                    val core = Libv2ray.newCoreController(callback)
                    coreController = core
                    failureMessage = serviceText("The VPN core could not start. Try a different server.", "هسته وی‌پی‌ان اجرا نشد. سرور دیگری را امتحان کنید.")
                    core.startLoop(finalConfig, descriptor)
                    ensureActive()
                    check(core.isRunning) { failureMessage }
                    withContext(Dispatchers.Main) {
                        if (generation.get() == token) {
                            check(core.isRunning) { failureMessage }
                            _isConnecting.value = false
                            isConnected.value = true
                            connectedRawConfig.value = rawConfig
                            _connectedSince.value = System.currentTimeMillis()
                            updateNotification()
                            startPing(token)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    closeResources()
                    throw cancelled
                } catch (_: Exception) {
                    closeResources()
                    withContext(Dispatchers.Main) {
                        if (generation.get() == token) {
                            resetConnectionState()
                            _lastError.value = failureMessage
                            finishService(startId)
                        }
                    }
                }
            }
        }
    }

    private fun startPing(token: Long) {
        pingJob?.cancel()
        currentPing.value = -2
        pingJob = serviceScope.launch {
            delay(700)
            var ping = RealPingTester.testCurrentConnectionPing()
            if (ping < 0) {
                delay(1_000)
                ping = RealPingTester.testCurrentConnectionPing()
            }
            withContext(Dispatchers.Main) {
                if (generation.get() == token && isConnected.value) currentPing.value = ping
            }
        }
    }

    private fun requestDisconnect(startId: Int, error: String? = null) {
        val token = generation.incrementAndGet()
        lifecycleJob?.cancel()
        pingJob?.cancel()
        resetConnectionState()
        _lastError.value = error
        lifecycleJob = serviceScope.launch {
            lifecycleMutex.withLock { closeResources() }
            withContext(Dispatchers.Main) {
                if (generation.get() == token) finishService(startId)
            }
        }
    }

    private fun resetConnectionState() {
        isConnected.value = false
        _isConnecting.value = false
        currentPing.value = -1
        connectedRawConfig.value = null
        _connectedSince.value = 0L
    }

    private fun closeResources() {
        val core = coreController
        coreController = null
        runCatching { core?.stopLoop() }
        val descriptor = vpnInterface
        vpnInterface = null
        runCatching { descriptor?.close() }
        if (resourceOwner === this) {
            resourceOwner = null
            tunFdEnvironmentKeys.forEach { runCatching { android.system.Os.unsetenv(it) } }
        }
    }

    private fun startVpnForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Vic2Ray VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val persian = isPersian()
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, VicVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val launchIntent = requireNotNull(packageManager.getLaunchIntentForPackage(packageName))
        val openApp = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val status = if (isConnected.value) {
            if (persian) "وی‌پی‌ان متصل است" else "VPN connected"
        } else {
            if (persian) "در حال اتصال…" else "Connecting…"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Vic2Ray")
            .setContentText(status)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, if (persian) "قطع اتصال" else "Disconnect", disconnect)
            .build()
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun isPersian(): Boolean = getSharedPreferences("Vic2rayPrefs", MODE_PRIVATE)
        .getString("language", null)?.let { it == "fa" } ?: (Locale.getDefault().language == "fa")

    private fun serviceText(english: String, persian: String): String = if (isPersian()) persian else english

    private fun finishService(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    override fun onDestroy() {
        generation.incrementAndGet()
        lifecycleJob?.cancel()
        pingJob?.cancel()
        resetConnectionState()
        // Native startup can finish after cancellation. Wait for its mutex before final teardown.
        serviceScope.launch {
            lifecycleMutex.withLock { closeResources() }
            serviceJob.cancel()
        }
        super.onDestroy()
    }

    override fun onRevoke() {
        _lastError.value = serviceText("VPN permission was revoked. Please reconnect to grant permission.", "مجوز وی‌پی‌ان لغو شد. برای تأیید مجوز دوباره متصل شوید.")
        stopSelf()
        super.onRevoke()
    }
}
