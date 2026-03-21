package com.paysetu.app.data.ledger.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LedgerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTransaction(tx: LedgerTransactionEntity)

    /**
     * Retrieves the absolute latest transaction in the ledger.
     * 💡 FIX: Ordered by timestamp AND id to ensure absolute chronological order
     * even if two transactions occur within the same millisecond.
     */
    @Query("SELECT * FROM ledger_transactions ORDER BY timestamp DESC, id DESC LIMIT 1")
    abstract suspend fun getLastTransaction(): LedgerTransactionEntity?

    @Query("SELECT * FROM ledger_transactions ORDER BY timestamp DESC")
    abstract fun getAllTransactions(): Flow<List<LedgerTransactionEntity>>

    @Query("SELECT * FROM ledger_transactions WHERE status = :status ORDER BY timestamp DESC")
    abstract fun getTransactionsByStatus(status: TransactionStatus): Flow<List<LedgerTransactionEntity>>

    fun getAcceptedTransactions(): Flow<List<LedgerTransactionEntity>> {
        return getTransactionsByStatus(TransactionStatus.ACCEPTED)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM ledger_transactions WHERE txHash = :txHash)")
    abstract suspend fun existsByTxHash(txHash: ByteArray): Boolean

    @Query("UPDATE ledger_transactions SET status = :newStatus WHERE txHash = :hash")
    abstract suspend fun updateTransactionStatus(hash: ByteArray, newStatus: TransactionStatus)

    @Query("SELECT * FROM ledger_transactions WHERE status = 'PENDING' ORDER BY timestamp ASC")
    abstract suspend fun getUnsyncedTransactions(): List<LedgerTransactionEntity>

    /**
     * 🔐 ATOMIC LEDGER APPEND
     * Enforces Chain Integrity, Replay Protection, and Genesis Validation.
     */
    @Transaction
    open suspend fun appendTransactionAtomically(newTx: LedgerTransactionEntity) {
        // 1. Replay protection
        if (existsByTxHash(newTx.txHash)) {
            throw IllegalStateException("Replay detected: txHash ${newTx.txHash.toHex()} already exists")
        }

        // 2. Chain Integrity & Genesis Validation
        val lastTx = getLastTransaction()
        if (lastTx != null) {
            // Verify link to previous block
            if (!lastTx.txHash.contentEquals(newTx.prevTxHash)) {
                // 💡 LOGGING TIP: If this fails, compare the hex values in Logcat
                throw IllegalStateException(
                    "Chain broken! \nExpected prev: ${lastTx.txHash.toHex()}\nActual received: ${newTx.prevTxHash.toHex()}"
                )
            }
        } else {
            // Genesis: First block must point to a 32-byte zeroed array
            val isGenesisValid = newTx.prevTxHash.size == 32 && newTx.prevTxHash.all { it == 0.toByte() }
            if (!isGenesisValid) {
                throw IllegalStateException("Genesis error: first block must link to 32 zero-bytes")
            }
        }

        // 3. Persist
        insertTransaction(newTx)
    }

    // Helper for better error logging
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}