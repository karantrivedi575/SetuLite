package com.paysetu.app.Core.device

import androidx.room.Entity
import androidx.room.PrimaryKey

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