package com.paysetu.app.domain.security

interface DeviceIntegrityChecker {
    fun isDeviceTrusted(): Boolean
}