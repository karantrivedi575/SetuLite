// File: CoreSecurity.kt
package com.paysetu.app.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

// ==========================================
// 1. SECURITY INTERFACES
// ==========================================

interface KeyProvider {
    fun getDevicePublicKey(): ByteArray
}

interface TransactionSigner {
    fun sign(payloadHash: ByteArray): ByteArray
}

interface DeviceIntegrityChecker {
    fun isDeviceTrusted(): Boolean
}

// ==========================================
// 2. KEY MANAGER (Hardware ECDSA & Keystore)
// ==========================================

/**
 * Unified Security Engine.
 * Handles Android Keystore initialization, key lifecycle, hardware-backed ECDSA key generation,
 * and transaction signing.
 */
class KeyManager(private val context: Context) : KeyProvider, TransactionSigner {

    companion object {
        // Single source of truth for the Keystore alias
        private const val KEY_ALIAS = "paysetu_device_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    // Lazy initialization ensures the Keystore is loaded exactly once when first needed
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }

    /**
     * Safe startup initialization.
     * Ensures key exists without forcing authentication.
     */
    fun initialize() {
        ensureKeyExists()
    }

    /**
     * Phase-9 AUTH ENFORCEMENT
     * This MUST trigger user authentication.
     */
    fun forceUserAuthentication() {
        ensureKeyExists()

        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")

        signature.initSign(privateKey)
        signature.update(byteArrayOf(0x01))
        signature.sign() // 🔐 Keystore-enforced user auth
    }

    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
    }

    private fun generateKey() {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN
        )
            .setAlgorithmParameterSpec(
                ECGenParameterSpec("secp256r1")
            )
            .setDigests(KeyProperties.DIGEST_SHA256)
            // Note: If forceUserAuthentication() is meant to trigger a biometric/device prompt,
            // this must be set to true. Left as false to preserve your existing logic.
            .setUserAuthenticationRequired(false)
            .setInvalidatedByBiometricEnrollment(true)

        // Attempt to back the key with the secure StrongBox chip if the device supports it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(isStrongBoxAvailable())
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )

        keyPairGenerator.initialize(builder.build())
        keyPairGenerator.generateKeyPair()
    }

    override fun getDevicePublicKey(): ByteArray {
        val cert = keyStore.getCertificate(KEY_ALIAS)
        return cert.publicKey.encoded
    }

    // --- TransactionSigner Implementation ---

    override fun sign(payloadHash: ByteArray): ByteArray {
        // 1. Retrieve the private key safely
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Device key not found in Keystore. Ensure KeyManager has initialized the keys.")

        val privateKey = entry.privateKey

        // 2. Sign the SHA-256 hash using ECDSA (standard for mobile ledgers)
        // Note: SHA256withECDSA is the recommended algorithm for Phase 6/10 requirements.
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(payloadHash)
            sign()
        }
    }

    private fun isStrongBoxAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_STRONGBOX_KEYSTORE
                )
    }
}

// ==========================================
// 3. DEVICE INTEGRITY (Root & Tamper Checks)
// ==========================================

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

// ==========================================
// 4. GENESIS HASH PROVIDER (Ledger Bootstrapping)
// ==========================================

class GenesisHashProvider(
    private val keyProvider: KeyProvider
) {

    // 🔒 Backing field renamed to avoid JVM clash
    private val cachedGenesisHash: ByteArray by lazy {
        val publicKey = keyProvider.getDevicePublicKey()

        MessageDigest
            .getInstance("SHA-256")
            .digest(publicKey)
    }

    fun getGenesisHash(): ByteArray {
        // Defensive copy to preserve immutability
        return cachedGenesisHash.clone()
    }
}