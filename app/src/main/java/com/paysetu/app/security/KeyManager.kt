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

/**
 * Interface for Phase 10 digital signatures.
 */
interface TransactionSigner {
    fun sign(payloadHash: ByteArray): ByteArray
}

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