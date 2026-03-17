package com.paysetu.app.security.integrity

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import com.paysetu.app.domain.security.DeviceIntegrityChecker
import java.io.File

class DeviceIntegrityCheckerImpl(
    private val context: Context
) : DeviceIntegrityChecker {

    override fun isDeviceTrusted(): Boolean {
        return !isRooted() && !isEmulator() && !isDebuggable()
    }

    private fun isRooted(): Boolean {
        return listOf(
            "/system/bin/su",
            "/system/xbin/su"
        ).any { File(it).exists() }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic")
    }

    private fun isDebuggable(): Boolean {
        return (context.applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
