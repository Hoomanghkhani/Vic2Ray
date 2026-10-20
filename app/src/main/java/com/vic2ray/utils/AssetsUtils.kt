package com.vic2ray.utils

import android.content.Context
import android.system.Os
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetsUtils {
    private const val XUDP_BASE_KEY = "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE"
    private var isInitialized = false
    private var assetsPrepared = false

    @Synchronized
    fun copyGeoAssets(context: Context, force: Boolean = false) {
        if (!assetsPrepared || force) {
            val prefs = context.getSharedPreferences("Vic2rayCoreAssets", Context.MODE_PRIVATE)
            @Suppress("DEPRECATION")
            val installedVersion = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            val changedVersion = prefs.getLong("installed_version", -1) != installedVersion
            listOf("geoip.dat", "geosite.dat").forEach { fileName ->
                val target = File(context.filesDir, fileName)
                if (force || changedVersion || !target.isFile || target.length() == 0L) {
                    val temporary = File(context.filesDir, "$fileName.tmp")
                    try {
                        context.assets.open(fileName).use { input ->
                            FileOutputStream(temporary).use { output ->
                                input.copyTo(output)
                                output.fd.sync()
                            }
                        }
                        if (temporary.length() == 0L || !temporary.renameTo(target)) {
                            throw IOException("Unable to install the VPN data files.")
                        }
                    } finally {
                        temporary.delete()
                    }
                }
            }
            prefs.edit().putLong("installed_version", installedVersion).apply()
            assetsPrepared = true
        }
        initCore(context)
    }

    @Synchronized
    fun initCore(context: Context) {
        if (isInitialized) return
        val assetsPath = context.filesDir.absolutePath
        Os.setenv("v2ray.location.asset", assetsPath, true)
        Os.setenv("xray.location.asset", assetsPath, true)
        Os.setenv("GEOIP_ASSET_PATH", assetsPath, true)
        Os.setenv("GEOSITE_ASSET_PATH", assetsPath, true)
        Libv2ray.initCoreEnv(assetsPath, XUDP_BASE_KEY)
        isInitialized = true
    }
}
