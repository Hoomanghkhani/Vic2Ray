package com.vic2ray.utils

import android.content.Context
import android.system.Os
import android.util.Log
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream

object AssetsUtils {
    private const val TAG = "AssetsUtils"
    private const val XUDP_BASE_KEY = "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE"
    private var isInitialized = false

    fun copyGeoAssets(context: Context, force: Boolean = false) {
        val assets = listOf("geoip.dat", "geosite.dat")
        assets.forEach { fileName ->
            val targetFile = File(context.filesDir, fileName)
            // If file doesn't exist, is empty, or is suspiciously small (< 1MB), or force is true, copy it.
            // Note: Valid geodata files are typically several megabytes.
            val shouldCopy = force || !targetFile.exists() || targetFile.length() < 1024 * 1024 
            
            if (shouldCopy) {
                try {
                    Log.d(TAG, "Copying $fileName to ${targetFile.absolutePath}")
                    val tempFile = File(context.filesDir, "$fileName.tmp")
                    context.assets.open(fileName).use { inputStream ->
                        Log.d(TAG, "Asset $fileName available size: ${inputStream.available()}")
                        FileOutputStream(tempFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        Log.d(TAG, "Successfully copied $fileName. New size: ${targetFile.length()}")
                    } else {
                        Log.e(TAG, "Failed to copy $fileName: temp file is empty")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying $fileName: ${e.message}")
                }
            } else {
                Log.d(TAG, "$fileName already exists and seems valid (Size: ${targetFile.length()}), skipping copy.")
            }
        }
        
        // After copying (or if already present), initialize the environment
        initCore(context, force)
    }

    /**
     * Centralized initialization of the V2Ray/Xray core environment.
     * Sets necessary environment variables and initializes the library.
     */
    fun initCore(context: Context, force: Boolean = false) {
        if (isInitialized && !force) {
            Log.d(TAG, "Core already initialized, skipping.")
            return
        }
        
        try {
            val assetsPath = context.filesDir.absolutePath
            
            // 1. Explicitly set environment variables for the Go core
            // These tell the core where to look for geoip.dat and geosite.dat
            Os.setenv("v2ray.location.asset", assetsPath, true)
            Os.setenv("xray.location.asset", assetsPath, true)
            
            // Some versions also look for these environment variables
            Os.setenv("GEOIP_ASSET_PATH", assetsPath, true)
            Os.setenv("GEOSITE_ASSET_PATH", assetsPath, true)
            
            Log.d(TAG, "Environment variables set in process: $assetsPath")

            // 2. Initialize the Libv2ray core environment
            // The second parameter is the XUDP base key used for encryption.
            Libv2ray.initCoreEnv(assetsPath, XUDP_BASE_KEY)
            Log.d(TAG, "Libv2ray.initCoreEnv called successfully")
            
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize core environment", e)
        }
    }
}
