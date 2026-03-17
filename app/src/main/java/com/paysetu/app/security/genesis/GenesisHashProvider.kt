package com.paysetu.app.security.genesis

import com.paysetu.app.domain.security.KeyProvider
import java.security.MessageDigest

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
