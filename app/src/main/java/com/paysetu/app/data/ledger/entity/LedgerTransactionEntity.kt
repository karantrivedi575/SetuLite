package com.paysetu.app.data.ledger.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "ledger_transactions",
    indices = [
        Index(value = ["txHash"], unique = true),
        Index(value = ["prevTxHash"])
    ]
)
data class LedgerTransactionEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val txHash: ByteArray,
    val prevTxHash: ByteArray,

    val senderDeviceId: ByteArray,
    val receiverDeviceId: ByteArray,

    val amount: Long,
    val timestamp: Long,

    val signature: ByteArray,

    val direction: TransactionDirection,
    val status: TransactionStatus
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LedgerTransactionEntity) return false

        return id == other.id &&
                timestamp == other.timestamp &&
                amount == other.amount &&
                direction == other.direction &&
                status == other.status &&
                txHash.contentEquals(other.txHash) &&
                prevTxHash.contentEquals(other.prevTxHash) &&
                senderDeviceId.contentEquals(other.senderDeviceId) &&
                receiverDeviceId.contentEquals(other.receiverDeviceId) &&
                signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + txHash.contentHashCode()
        result = 31 * result + prevTxHash.contentHashCode()
        result = 31 * result + senderDeviceId.contentHashCode()
        result = 31 * result + receiverDeviceId.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
