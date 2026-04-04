// File: DeviceIntegrityChecker.kt
package com.paysetu.app.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import java.io.File

interface DeviceIntegrityChecker {
    fun isDeviceTrusted(): Boolean
}

class DeviceIntegrityCheckerImpl(
    private val context: Context
) : DeviceIntegrityChecker {

    override fun isDeviceTrusted(): Boolean {
        return !isRooted() && !isEmulator() && !isDebuggable()
    }

    private fun isRooted(): Boolean {
        // Check for test-keys which indicate a custom ROM or non-production build
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // Comprehensive list of common su binary paths
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/magisk/.core/bin/su"
        )
        if (paths.any { File(it).exists() }) {
            return true
        }

        // Check if su is executable via runtime
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val inReader = process.inputStream.bufferedReader()
            inReader.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu"))
    }

    private fun isDebuggable(): Boolean {
        val isAppDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isDebuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        return isAppDebuggable || isDebuggerAttached
    }
}