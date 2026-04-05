// File: PinGate.kt
package com.paysetu.app.security

/**
 * Interface for verifying user authority before a sensitive operation (like sending a payment).
 */
interface PinAuthorizer {
    /**
     * Authorizes the action using the provided [pin].
     * Returns true if the PIN is correct, false otherwise.
     */
    fun authorize(pin: String): Boolean
}

/**
 * Hardware-backed implementation of the PinAuthorizer.
 */
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