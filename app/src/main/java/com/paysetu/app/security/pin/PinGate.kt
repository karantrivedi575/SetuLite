package com.paysetu.app.security.pin

import android.security.keystore.UserNotAuthenticatedException
import com.paysetu.app.domain.security.PinAuthorizer
import com.paysetu.app.security.keys.KeyManager

class PinGate(
    private val keyManager: KeyManager
) : PinAuthorizer {

    /**
     * Authorizes the transaction.
     * @param pin The PIN from the UI. While this implementation primarily triggers
     * the system-level auth prompt, the parameter is required by the domain interface.
     */
    override fun authorize(pin: String): Boolean {
        return try {
            /*
             * Phase 9/10–Hardware-backed enforcement:
             * This calls a Keystore operation that requires the user to have
             * authenticated via Biometrics/PIN within the last N seconds.
             */
            keyManager.forceUserAuthentication()
            true
        } catch (e: Exception) {
            // Catches UserNotAuthenticatedException or hardware key issues
            false
        }
    }
}