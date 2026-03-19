package com.paysetu.app

import android.app.Application
import android.util.Log
import com.paysetu.app.security.keys.KeyManager

class PaySetuApp : Application() {

    // 🔐 Global instance for manual dependency injection in MainActivity
    lateinit var keyManager: KeyManager
        private set

    override fun onCreate() {
        super.onCreate()

        // 1️⃣ Initialize KeyManager (Phase 6 Security Foundation)
        // This generates the hardware-backed ECDSA keys used for Phase 10 signing
        keyManager = KeyManager(this)
        keyManager.initialize()

        // 2️⃣ Verify identity generation
        val pubKey = keyManager.getDevicePublicKey()
        Log.d("PAYSETU_INIT", "Device Identity Initialized. Public key size: ${pubKey.size} bytes")
    }
}