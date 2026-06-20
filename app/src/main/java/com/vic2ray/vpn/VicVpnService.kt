package com.vic2ray.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vic2ray.ui.MainActivity
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
        private const val CHANNEL_ID = "vic2ray_vpn"
        private const val NOTIFICATION_ID = 1
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

        // این باید اولین کار باشد! بدون این، سیستم اندروید سرویس را می‌کشد
        showForegroundNotification()

        try {
            Log.d(TAG, "Starting V2Ray core...")
            coreController = Libv2ray.newCoreController(null)
            coreController?.startLoop(jsonConfig, 0)
            Log.d(TAG, "V2Ray core started")

            Log.d(TAG, "Setting up VPN tunnel...")
            val builder = Builder()
            builder.setSession("Vic2ray VPN")
            builder.addAddress("10.0.0.2", 24)
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("8.8.4.4")
            builder.addRoute("0.0.0.0", 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", 10809))
            }

            vpnInterface = builder.establish()
            Log.d(TAG, "VPN tunnel established: ${vpnInterface != null}")

        } catch (e: Exception) {
            Log.e(TAG, "Error in connect()", e)
            disconnect()
            stopSelf()
        }
    }

    private fun showForegroundNotification() {
        try {
            // ساخت کانال نوتیفیکیشن (اندروید ۸+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Vic2Ray VPN",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "وضعیت اتصال VPN"
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }

            val activityIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Vic2Ray VPN")
                .setContentText("اتصال برقرار است")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Foreground notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing foreground notification", e)
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
        try {
            @Suppress("DEPRECATION")
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
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
