package com.paysetu.app.Core.database

import androidx.room.TypeConverter
import com.paysetu.app.ledger.model.TransactionDirection
import com.paysetu.app.ledger.model.TransactionStatus

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