package com.paysetu.app.Core.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.lang.reflect.Method
import java.util.Locale

object MiuiPermissionUtils {

    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    // 💡 CACHE 1: String manipulation is slow. Compute this exactly once.
    private val isXiaomiDevice by lazy {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        manufacturer.contains("xiaomi") || manufacturer.contains("poco") || manufacturer.contains("redmi")
    }

    // 💡 CACHE 2: Reflection is incredibly slow. Look up the method once and save it in memory.
    private var checkOpMethod: Method? = null
    private var isMethodLoaded = false

    private fun getCheckOpMethod(): Method? {
        if (!isMethodLoaded) {
            try {
                // Using checkOpNoThrow is slightly faster as it avoids internal exception generation
                checkOpMethod = AppOpsManager::class.java.getMethod(
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
            } catch (e: Exception) {
                Log.e("MiuiGuard", "Failed to cache AppOps method: ${e.message}")
            }
            isMethodLoaded = true
        }
        return checkOpMethod
    }

    /** Checks if the device is running MIUI/HyperOS */
    fun isXiaomi(): Boolean = isXiaomiDevice

    /**
     * Uses optimized reflection to check the 'Background Pop-up' toggle.
     * Returns true if allowed, or if NOT on a Xiaomi device.
     */
    fun isBackgroundStartAllowed(context: Context): Boolean {
        if (!isXiaomiDevice) return true

        return try {
            val mgr = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = getCheckOpMethod() ?: return true // Fail open if reflection fails

            val result = method.invoke(
                mgr,
                OP_BACKGROUND_START_ACTIVITY,
                Binder.getCallingUid(),
                context.packageName
            ) as Int

            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e("MiuiGuard", "Failed to check AppOps: ${e.message}")
            true
        }
    }

    /** Checks if PaySetu is ignored by battery optimizations (Doze mode) */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the 'Ignore Battery Optimization' system prompt or settings */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /** Takes the user directly to the MIUI 'Other Permissions' screen */
    fun openMiuiPermissionSettings(context: Context) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}