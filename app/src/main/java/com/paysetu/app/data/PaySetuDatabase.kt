package com.paysetu.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.paysetu.app.data.device.dao.DeviceStateDao
import com.paysetu.app.data.device.entity.DeviceStateEntity
import com.paysetu.app.data.ledger.converters.LedgerConverters
import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity

@Database(
    entities = [
        LedgerTransactionEntity::class,
        DeviceStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(LedgerConverters::class)
abstract class PaySetuDatabase : RoomDatabase() {

    // Access to the immutable transaction ledger
    abstract fun ledgerDao(): LedgerDao

    // Access to device-specific security state (Sync time, Trust score)
    abstract fun deviceStateDao(): DeviceStateDao
}