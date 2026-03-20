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
    version = 2, // 1. Incremented from 1 to 2 because the schema changed
    exportSchema = false
)
@TypeConverters(LedgerConverters::class)
abstract class PaySetuDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao
    abstract fun deviceStateDao(): DeviceStateDao
}