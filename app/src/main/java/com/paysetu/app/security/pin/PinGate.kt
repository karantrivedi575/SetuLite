package com.paysetu.app.security.pin

import android.security.keystore.UserNotAuthenticatedException
import com.paysetu.app.domain.security.PinAuthorizer
import com.paysetu.app.security.keys.KeyManager

class PinGate(
    private val keyManager: KeyManager
) : PinAuthorizer {

    override fun authorize(): Boolean {
        return try {
            /*
             * Phase 9–correct behavior:
             * - Force a Keystore operation
             * - OS handles PIN / biometrics
             * - Hardware-backed enforcement
             */
            keyManager.forceUserAuthentication()
            true
        } catch (e: UserNotAuthenticatedException) {
            false
        }
    }
}
