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
import org.json.JSONObject

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
                // Add IPv6 address and route to intercept IPv6 traffic (fixes Telegram/WhatsApp bypassing VPN)
                try {
                    builder.addAddress("fc00::2", 128)
                    builder.addRoute("::", 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding IPv6 to VPN: ${e.message}")
                }
                
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("8.8.8.8")
                builder.setMtu(1280) // Lower MTU to 1280 for better compatibility with fragmented UDP (Telegram)
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
                    android.system.Os.setenv("v2ray.tun.fd", fd.toString(), true)
                    // Some variants use uppercase
                    android.system.Os.setenv("XRAY_TUN_FD", fd.toString(), true)
                    android.system.Os.setenv("V2RAY_TUN_FD", fd.toString(), true)
                    // And some use dots but with different prefixes
                    android.system.Os.setenv("vpn.tun.fd", fd.toString(), true)
                    Log.d(TAG, "Set tun fd environment variables to $fd")
                    
                    // Try to find a way to set env via Libv2ray if reflection reveals a method
                    logLibv2rayMethods()
                    trySetEnvViaReflection("xray.tun.fd", fd.toString())
                    trySetEnvViaReflection("v2ray.tun.fd", fd.toString())
                }

                Log.d(TAG, "Starting V2Ray core...")
                try {
                    // Centralized initialization of environment variables and core
                    // Use force=true to ensure environment variables are refreshed in this process scope
                    com.vic2ray.utils.AssetsUtils.initCore(applicationContext, force = true)
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
                
                // Inject the real TUN file descriptor into the JSON config robustly
                val fd = vpnInterface?.fd ?: -1
                val finalConfig = if (fd != -1) {
                    try {
                        val jsonObj = JSONObject(jsonConfig)
                        val inbounds = jsonObj.getJSONArray("inbounds")
                        for (i in 0 until inbounds.length()) {
                            val inbound = inbounds.getJSONObject(i)
                            if (inbound.optString("protocol") == "tun") {
                                val settings = inbound.getJSONObject("settings")
                                // Inject multiple possible keys for the FD
                                settings.put("fd", fd)
                                settings.put("androidTunFd", fd)
                                settings.put("tunFd", fd)
                                settings.put("tun-fd", fd)
                                Log.d(TAG, "Injected FD $fd into TUN settings with multiple keys")
                            }
                        }
                        jsonObj.toString()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error injecting FD into JSON", e)
                        // Fallback simple replacement if JSON parsing fails
                        jsonConfig.replace("\"fd\":-1", "\"fd\":$fd")
                    }
                } else {
                    jsonConfig
                }
                
                // CRITICAL: Pass the real TUN fd to libv2ray - this is what enables tun2socks bridging inside the library.
                // Passing 0 (as before) meant libv2ray never read traffic from the VPN tunnel.
                val tunFd = vpnInterface?.fd ?: 0
                coreController?.startLoop(finalConfig, tunFd)
                isConnected.value = true
                Log.d(TAG, "V2Ray core started with TUN fd=$tunFd")
                
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

    private fun logLibv2rayMethods() {
        try {
            val clazz = Libv2ray::class.java
            Log.d(TAG, "--- Libv2ray Methods ---")
            clazz.declaredMethods.forEach { method ->
                Log.d(TAG, "Method: ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
            }
            Log.d(TAG, "------------------------")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log Libv2ray methods", e)
        }
    }

    private fun trySetEnvViaReflection(key: String, value: String) {
        try {
            val clazz = Libv2ray::class.java
            // Try to find a method named setEnv or similar
            val methods = clazz.declaredMethods
            val setEnvMethod = methods.find { 
                (it.name == "setEnv" || it.name == "setenv") && 
                it.parameterTypes.size == 2 && 
                it.parameterTypes[0] == String::class.java && 
                it.parameterTypes[1] == String::class.java 
            }
            
            if (setEnvMethod != null) {
                Log.d(TAG, "Found ${setEnvMethod.name} method, calling it for $key=$value")
                setEnvMethod.invoke(null, key, value)
            } else {
                Log.d(TAG, "No setEnv method found in Libv2ray via reflection")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling setEnv via reflection", e)
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
