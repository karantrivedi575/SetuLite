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
     * Used for linking new transactions to the chain.
     */
    @Query("SELECT * FROM ledger_transactions ORDER BY id DESC LIMIT 1")
    abstract suspend fun getLastTransaction(): LedgerTransactionEntity?

    /**
     * 💡 ADDED: Returns ALL transactions as a Flow (Required by PaymentViewModel).
     * Ordered by newest first so the UI shows the latest payments at the top.
     */
    @Query("SELECT * FROM ledger_transactions ORDER BY timestamp DESC")
    abstract fun getAllTransactions(): Flow<List<LedgerTransactionEntity>>

    /**
     * Raw query for status filtering. (No default args here to prevent KSP crashes)
     */
    @Query("SELECT * FROM ledger_transactions WHERE status = :status ORDER BY timestamp DESC")
    abstract fun getTransactionsByStatus(status: TransactionStatus): Flow<List<LedgerTransactionEntity>>

    /**
     * Safe wrapper to get accepted transactions stream.
     */
    fun getAcceptedTransactions(): Flow<List<LedgerTransactionEntity>> {
        return getTransactionsByStatus(TransactionStatus.ACCEPTED)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM ledger_transactions WHERE txHash = :txHash)")
    abstract suspend fun existsByTxHash(txHash: ByteArray): Boolean

    /**
     * 🔐 PHASE-9/10 ATOMIC LEDGER APPEND
     * Enforces Chain Integrity, Replay Protection, and Genesis Validation.
     */
    // ... existing insert and append methods ...

    /**
     * ⚖️ PHASE 10: Backend Arbiter Override
     * Forces a transaction into a specific state based on global reconciliation.
     */
    @Query("UPDATE ledger_transactions SET status = :newStatus WHERE txHash = :hash")
    abstract suspend fun updateTransactionStatus(hash: ByteArray, newStatus: TransactionStatus)

    /**
     * Fetches all transactions that are still marked as PENDING (not yet synced).
     */
    @Query("SELECT * FROM ledger_transactions WHERE status = 'PENDING' ORDER BY timestamp ASC")
    abstract suspend fun getUnsyncedTransactions(): List<LedgerTransactionEntity>
    @Transaction
    open suspend fun appendTransactionAtomically(newTx: LedgerTransactionEntity) {
        // 1. Replay protection: Check if hash already exists
        if (existsByTxHash(newTx.txHash)) {
            throw IllegalStateException("Replay detected: txHash ${newTx.txHash.toHex()} already exists")
        }

        // 2. Chain Integrity & Genesis Validation
        val lastTx = getLastTransaction()
        if (lastTx != null) {
            // Verify link to previous block
            if (!lastTx.txHash.contentEquals(newTx.prevTxHash)) {
                throw IllegalStateException("Chain broken: hash mismatch")
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

