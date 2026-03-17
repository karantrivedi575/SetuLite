package com.paysetu.app.domain.security

interface KeyProvider {
    fun getDevicePublicKey(): ByteArray
}
