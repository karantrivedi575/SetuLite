// File: KeyManager.kt
package com.paysetu.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

interface KeyProvider {
    fun getDevicePublicKey(): ByteArray
}

class KeyManager(private val context: Context) : KeyProvider {

    companion object {
        private const val KEY_ALIAS = "paysetu_device_key"
    }

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(isStrongBoxAvailable())
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        keyPairGenerator.initialize(builder.build())
        keyPairGenerator.generateKeyPair()
    }

    override fun getDevicePublicKey(): ByteArray {
        val cert = keyStore.getCertificate(KEY_ALIAS)
        return cert.publicKey.encoded
    }

    private fun isStrongBoxAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_STRONGBOX_KEYSTORE
                )
    }
}