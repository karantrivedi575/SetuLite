package com.paysetu.app.ui

import android.app.Application
import android.util.Log
import com.paysetu.app.security.keys.KeyManager

class PaySetuApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 🔐 Phase 6 test
        val keyManager = KeyManager(applicationContext)
        keyManager.initialize()


        val pubKey = keyManager.getDevicePublicKey()
        Log.d("PHASE6", "Public key size = ${pubKey.size}")
    }
}
