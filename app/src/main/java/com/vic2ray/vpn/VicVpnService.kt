package com.vic2ray.vpn

import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import libv2ray.Libv2ray
import libv2ray.CoreController

class VicVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null

    companion object {
        private const val TAG = "VicVpnService"
        const val ACTION_CONNECT = "com.vic2ray.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.vic2ray.vpn.DISCONNECT"
        const val EXTRA_CONFIG = "config"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config == null) {
                    Log.e(TAG, "Config is null, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
                connect(config)
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

    private fun connect(jsonConfig: String) {
        Log.d(TAG, "connect() called")
        disconnect()

        try {
            Log.d(TAG, "Starting V2Ray core...")
            coreController = Libv2ray.newCoreController(null)
            coreController?.startLoop(jsonConfig, 0)
            Log.d(TAG, "V2Ray core started")

            Log.d(TAG, "Setting up VPN tunnel...")
            val builder = Builder()
            builder.setSession("Vic2Ray")
            builder.addAddress("10.0.0.2", 24)
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("8.8.4.4")
            builder.addRoute("0.0.0.0", 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", 10809))
            }

            // This line automatically shows the VPN system notification, no startForeground needed!
            vpnInterface = builder.establish()
            Log.d(TAG, "VPN tunnel established: ${vpnInterface != null}")

        } catch (e: Exception) {
            Log.e(TAG, "Error in connect()", e)
            disconnect()
            stopSelf()
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
