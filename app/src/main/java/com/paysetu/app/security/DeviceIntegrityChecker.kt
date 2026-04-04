package com.paysetu.app.security

interface DeviceIntegrityChecker {
    fun isDeviceTrusted(): Boolean
}