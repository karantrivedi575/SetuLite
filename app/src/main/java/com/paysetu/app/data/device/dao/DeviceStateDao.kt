package com.paysetu.app.data.device.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.paysetu.app.data.device.entity.DeviceStateEntity

@Dao
interface DeviceStateDao {

    @Query("SELECT * FROM device_state WHERE id = 0 LIMIT 1")
    suspend fun getDeviceState(): DeviceStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: DeviceStateEntity)
}