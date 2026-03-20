package com.paysetu.app.data.device

import com.paysetu.app.data.device.dao.DeviceStateDao
import com.paysetu.app.data.device.entity.DeviceStateEntity

class DeviceStateRepository(
    private val deviceStateDao: DeviceStateDao
) {
    /**
     * Fetches the last time the device was online.
     * Used by HeartbeatPolicy to enforce the 48h payment window.
     */
    suspend fun getLastSyncTimestamp(): Long {
        return deviceStateDao.getDeviceState()?.lastSuccessfulSync ?: 0L
    }

    /**
     * Updates the heartbeat timestamp after successful backend communication.
     */
    suspend fun updateLastSyncTimestamp(timestamp: Long) {
        updateSyncTimestamp(timestamp)
    }

    /**
     * Updates state after a successful reconciliation.
     */
    suspend fun updateSyncTimestamp(timestamp: Long) {
        val currentState = deviceStateDao.getDeviceState() ?: DeviceStateEntity(
            lastKnownChainHash = byteArrayOf(),
            lastSuccessfulSync = timestamp,
            trustScore = 100
        )
        deviceStateDao.insertOrUpdate(currentState.copy(lastSuccessfulSync = timestamp))
    }

    /**
     * Updates the local trust score based on backend risk assessment.
     */
    suspend fun updateTrustScore(score: Float) {
        val currentState = deviceStateDao.getDeviceState() ?: DeviceStateEntity(
            lastKnownChainHash = byteArrayOf(),
            lastSuccessfulSync = 0L,
            trustScore = 100
        )
        // Store the backend float as an Int percentage (0-100)
        deviceStateDao.insertOrUpdate(currentState.copy(trustScore = score.toInt()))
    }

    suspend fun getTrustScore(): Int {
        return deviceStateDao.getDeviceState()?.trustScore ?: 100
    }

    /**
     * Manages the "Anchor Hash" - the last transaction officially accepted by the server.
     */
    suspend fun updateLastSyncedHash(hash: ByteArray) {
        val currentState = deviceStateDao.getDeviceState() ?: DeviceStateEntity(
            lastKnownChainHash = hash,
            lastSuccessfulSync = 0L,
            trustScore = 100
        )
        deviceStateDao.insertOrUpdate(currentState.copy(lastKnownChainHash = hash))
    }

    /**
     * Retrieves the binary last known chain hash.
     */
    suspend fun getLastSyncedHash(): ByteArray? {
        return deviceStateDao.getDeviceState()?.lastKnownChainHash
    }

    /**
     * ✅ Added for Phase 10: Requirements of PerformGlobalSyncUseCase.
     * Returns the anchor point in Hex string format for the backend to start reconciliation.
     */
    suspend fun getLastSyncedHashHex(): String? {
        return getLastSyncedHash()?.toHex()
    }

    /**
     * ✅ Added for Phase 10: Requirements of PerformGlobalSyncUseCase.
     * Fetches the device public key from the Keystore/Identity layer for sync identification.
     */
    suspend fun getDevicePublicKeyHex(): String {
        // This is a stub - in a later phase we will pull this from KeyManager
        return "STUB_HARDWARE_IDENTITY_HEX"
    }

    /**
     * Internal helper to convert binary hashes to Hex strings for network transmission.
     */
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}