package com.paysetu.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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
    version = 2, // Incremented to match the new schema
    exportSchema = false
)
@TypeConverters(LedgerConverters::class)
abstract class PaySetuDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao
    abstract fun deviceStateDao(): DeviceStateDao

    companion object {
        @Volatile
        private var INSTANCE: PaySetuDatabase? = null

        fun getDatabase(context: Context): PaySetuDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaySetuDatabase::class.java,
                    "paysetu_database"
                )
                    // Wipes and rebuilds instead of crashing when versions don't match
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}