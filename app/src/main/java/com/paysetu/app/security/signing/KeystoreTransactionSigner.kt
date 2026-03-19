package com.paysetu.app.security.signing

import com.paysetu.app.domain.security.TransactionSigner //
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature

/**
 * Concrete implementation of the TransactionSigner interface using Android Keystore.
 * Renamed to KeystoreTransactionSigner to avoid naming collisions with the interface.
 */
class KeystoreTransactionSigner : TransactionSigner { //

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    override fun sign(payloadHash: ByteArray): ByteArray {
        // Retrieves the private key anchored in the hardware-backed Keystore
        val privateKey = keyStore
            .getKey("paysetu_device_key", null) as PrivateKey

        // Signs the SHA-256 hash using the ECDSA algorithm as defined in Phase 6
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(payloadHash)

        return signature.sign()
    }
}