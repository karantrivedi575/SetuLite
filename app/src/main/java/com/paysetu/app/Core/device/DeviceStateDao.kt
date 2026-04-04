package com.paysetu.app.Core.device

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.paysetu.app.Core.device.DeviceStateEntity

@Dao
interface DeviceStateDao {

    @Query("SELECT * FROM device_state WHERE id = 0 LIMIT 1")
    suspend fun getDeviceState(): DeviceStateEntity?

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertOrUpdate(state: DeviceStateEntity)
}