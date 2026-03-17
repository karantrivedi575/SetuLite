package com.paysetu.app.data


import androidx.room.Database
import androidx.room.RoomDatabase
import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity

@Database(
    entities = [LedgerTransactionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PaySetuDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}
