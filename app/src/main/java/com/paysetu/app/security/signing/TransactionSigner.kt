package com.paysetu.app.security.signing

import com.paysetu.app.domain.security.TransactionSigner
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature

class KeystoreTransactionSigner : TransactionSigner {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    override fun sign(payloadHash: ByteArray): ByteArray {
        val privateKey = keyStore
            .getKey("paysetu_device_key", null) as PrivateKey

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(payloadHash)

        return signature.sign()
    }
}
