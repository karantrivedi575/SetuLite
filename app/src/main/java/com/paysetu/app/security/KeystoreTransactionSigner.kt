package com.paysetu.app.security

import java.security.KeyStore
import java.security.Signature

/**
 * Concrete implementation of the TransactionSigner interface using Android Keystore.
 * Uses hardware-backed ECDSA keys for Phase 10 digital signatures.
 */

interface TransactionSigner {
    fun sign(payloadHash: ByteArray): ByteArray
}
class KeystoreTransactionSigner : TransactionSigner {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    // Reference to the alias used in KeyManager.kt
    private val keyAlias = "paysetu_device_key"

    override fun sign(payloadHash: ByteArray): ByteArray {
        // 1. Retrieve the private key safely
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry
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
}