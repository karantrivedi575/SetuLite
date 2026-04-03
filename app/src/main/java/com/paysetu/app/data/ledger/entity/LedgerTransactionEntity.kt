package com.paysetu.app.data.ledger.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Immutable
@Entity(
    tableName = "ledger_transactions",
    indices = [
        Index(value = ["txHash"], unique = true),
        Index(value = ["prevTxHash"]),
        Index(value = ["nonce"], unique = true) // 💡 NEW: Fast lookup to prevent replay attacks
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
    val status: TransactionStatus,

    // ==========================================
    // 🛡️ NEW SECURITY & AUDIT FIELDS
    // ==========================================

    /**
     * A cryptographically secure random identifier generated for every transaction.
     * This guarantees that even if Alice sends Bob ₹50 twice at the exact same millisecond,
     * the transactions are unique. It mathematically prevents SMS Replay Attacks.
     */
    @ColumnInfo(defaultValue = "")
    val nonce: String = UUID.randomUUID().toString(),

    /**
     * If this transaction is a system-generated refund (Auto-Reversal),
     * this field stores the txHash of the original failed outgoing payment.
     * Null for standard user-to-user transfers.
     */
    val refundedTxHash: ByteArray? = null

) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LedgerTransactionEntity

        if (id != other.id) return false
        if (amount != other.amount) return false
        if (timestamp != other.timestamp) return false
        if (direction != other.direction) return false
        if (status != other.status) return false
        if (nonce != other.nonce) return false
        if (!txHash.contentEquals(other.txHash)) return false
        if (!prevTxHash.contentEquals(other.prevTxHash)) return false
        if (!senderDeviceId.contentEquals(other.senderDeviceId)) return false
        if (!receiverDeviceId.contentEquals(other.receiverDeviceId)) return false
        if (!signature.contentEquals(other.signature)) return false
        if (refundedTxHash != null) {
            if (other.refundedTxHash == null) return false
            if (!refundedTxHash.contentEquals(other.refundedTxHash)) return false
        } else if (other.refundedTxHash != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + txHash.contentHashCode()
        result = 31 * result + prevTxHash.contentHashCode()
        result = 31 * result + senderDeviceId.contentHashCode()
        result = 31 * result + receiverDeviceId.contentHashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + (refundedTxHash?.contentHashCode() ?: 0)
        return result
    }
}