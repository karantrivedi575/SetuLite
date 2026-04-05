// File: DeviceState.kt
package com.paysetu.app.Core.device

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

// ==========================================
// 1. The Entity (Data Structure)
// ==========================================
@Entity(tableName = "device_state")
data class DeviceStateEntity(
    @PrimaryKey
    val id: Int = 0,
    val lastKnownChainHash: ByteArray,
    val lastSuccessfulSync: Long,
    val trustScore: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceStateEntity

        if (id != other.id) return false
        if (lastSuccessfulSync != other.lastSuccessfulSync) return false
        if (trustScore != other.trustScore) return false
        if (!lastKnownChainHash.contentEquals(other.lastKnownChainHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + lastSuccessfulSync.hashCode()
        result = 31 * result + trustScore
        result = 31 * result + lastKnownChainHash.contentHashCode()
        return result
    }
}

// ==========================================
// 2. The DAO (Database Access)
// ==========================================
@Dao
interface DeviceStateDao {
    @Query("SELECT * FROM device_state WHERE id = 0 LIMIT 1")
    suspend fun getDeviceState(): DeviceStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: DeviceStateEntity)
}

// ==========================================
// 3. The Repository (Business Abstraction)
// ==========================================
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