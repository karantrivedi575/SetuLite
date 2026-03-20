package com.paysetu.app.data.ledger.converters

import androidx.room.TypeConverter
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.data.ledger.entity.TransactionStatus

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