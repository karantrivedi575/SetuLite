package com.paysetu.app.security

interface KeyProvider {
    fun getDevicePublicKey(): ByteArray
}
