// File: PaySetuDatabase.kt
package com.paysetu.app.Core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.paysetu.app.Core.device.DeviceStateDao
import com.paysetu.app.Core.device.DeviceStateEntity
import com.paysetu.app.ledger.LedgerDao
import com.paysetu.app.ledger.model.LedgerTransactionEntity
import com.paysetu.app.ledger.model.TransactionDirection
import com.paysetu.app.ledger.model.TransactionStatus

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

// ==========================================
// Room Type Converters
// ==========================================
class LedgerConverters {
    @TypeConverter
    fun fromStatus(value: TransactionStatus) = value.name

    @TypeConverter
    fun toStatus(value: String) = TransactionStatus.valueOf(value)

    @TypeConverter
    fun fromDirection(value: TransactionDirection) = value.name

    @TypeConverter
    fun toDirection(value: String) = TransactionDirection.valueOf(value)
}