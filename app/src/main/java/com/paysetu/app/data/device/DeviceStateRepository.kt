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

    suspend fun getTrustScore(): Int {
        return deviceStateDao.getDeviceState()?.trustScore ?: 100
    }
}