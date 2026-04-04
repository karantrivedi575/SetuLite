package com.paysetu.app.security

/**
 * Interface for verifying user authority before a sensitive operation (like sending payment).
 */
interface PinAuthorizer {
    /**
     * Authorizes the action using the provided [pin].
     * Returns true if the PIN is correct, false otherwise.
     */
    fun authorize(pin: String): Boolean // Added 'pin' parameter
}