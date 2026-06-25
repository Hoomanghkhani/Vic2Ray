package com.vic2ray.vpn

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import libv2ray.CoreController
import kotlinx.coroutines.flow.MutableStateFlow

class VicVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null

    companion object {
        private const val TAG = "VicVpnService"
        const val ACTION_CONNECT = "com.vic2ray.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.vic2ray.vpn.DISCONNECT"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_RAW_CONFIG = "raw_config"
        
        val isConnected = MutableStateFlow(false)
        val currentPing = MutableStateFlow(-1)
        val connectedRawConfig = MutableStateFlow<String?>(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                val rawConfig = intent.getStringExtra(EXTRA_RAW_CONFIG)
                if (config == null) {
                    Log.e(TAG, "Config is null, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
                connect(config, rawConfig)
            }
            ACTION_DISCONNECT -> {
                disconnect()
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action, stopping")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private fun connect(jsonConfig: String, rawConfig: String?) {
        Log.d(TAG, "connect() called")
        disconnect()
        connectedRawConfig.value = rawConfig

        serviceScope.launch {
            try {
                Log.d(TAG, "Setting up VPN tunnel...")
                val builder = Builder()
                builder.setSession("Vic2Ray")
                // A minimum configuration to establish the VPN interface
                builder.addAddress("10.0.0.2", 24)
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute("::", 0)
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("8.8.8.8")
                builder.setMtu(1500)
                builder.setBlocking(false)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", 10809))
                }
                
                // CRITICAL FIX: Exclude our own app from the VPN so Xray can dial out!
                // Without this, Android kernel drops Xray's raw outbound TCP packets to prevent VPN loops.
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                }

                // This line automatically shows the VPN system notification
                vpnInterface = builder.establish()
                Log.d(TAG, "VPN tunnel established: ${vpnInterface != null}")
                
                vpnInterface?.fd?.let { fd ->
                    android.system.Os.setenv("xray.tun.fd", fd.toString(), true)
                    Log.d(TAG, "Set xray.tun.fd to $fd")
                }

                Log.d(TAG, "Starting V2Ray core...")
                try {
                    // The second parameter for Xray's initCoreEnv is xudpBaseKey (Base64 encoded).
                    // A 32-byte key encoded in Raw Base64 (no padding) is 43 characters long.
                    val xudpBaseKey = "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE"
                    Libv2ray.initCoreEnv(applicationContext.filesDir.absolutePath, xudpBaseKey)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in initCoreEnv", e)
                }
                
                val callbackHandler = object : libv2ray.CoreCallbackHandler {
                    override fun onEmitStatus(status: Long, message: String?): Long {
                        Log.d(TAG, "V2Ray Status: $status, Message: $message")
                        return 0
                    }
                    override fun shutdown(): Long {
                        Log.d(TAG, "V2Ray Shutdown")
                        return 0
                    }
                    override fun startup(): Long {
                        Log.d(TAG, "V2Ray Startup")
                        return 0
                    }
                }
                
                coreController = Libv2ray.newCoreController(callbackHandler)
                coreController?.startLoop(jsonConfig, 0)
                isConnected.value = true
                Log.d(TAG, "V2Ray core started")
                
                // Initial ping test
                serviceScope.launch {
                    currentPing.value = -2 // -2 means loading
                    kotlinx.coroutines.delay(1000) // Give the proxy a moment to establish connection
                    var ping = com.vic2ray.tester.RealPingTester.testCurrentConnectionPing()
                    
                    if (ping == -1) {
                        // Sometimes the VMess handshake takes a bit longer, let's retry once
                        kotlinx.coroutines.delay(2000)
                        ping = com.vic2ray.tester.RealPingTester.testCurrentConnectionPing()
                    }
                    
                    currentPing.value = ping
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in connect()", e)
                disconnect()
                stopSelf()
            }
        }
    }

    private fun disconnect() {
        Log.d(TAG, "disconnect() called")
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping core", e)
        }
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        coreController = null
        isConnected.value = false
        currentPing.value = -1
        connectedRawConfig.value = null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        disconnect()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.d(TAG, "onRevoke() - VPN permission removed")
        disconnect()
        stopSelf()
        super.onRevoke()
    }
}
