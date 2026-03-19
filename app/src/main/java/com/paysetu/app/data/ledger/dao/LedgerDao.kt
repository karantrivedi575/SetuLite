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

    @Query("""
        SELECT * FROM ledger_transactions
        ORDER BY id DESC
        LIMIT 1
    """)
    abstract suspend fun getLastTransaction(): LedgerTransactionEntity?

    // Updated to use a type-safe parameter instead of hardcoded 'ACCEPTED' string
    @Query("""
        SELECT * FROM ledger_transactions
        WHERE status = :status
        ORDER BY id ASC
    """)
    abstract fun getAcceptedTransactions(
        status: TransactionStatus = TransactionStatus.ACCEPTED
    ): Flow<List<LedgerTransactionEntity>>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM ledger_transactions
            WHERE txHash = :txHash
        )
    """)
    abstract suspend fun existsByTxHash(txHash: ByteArray): Boolean

    // 🔐 PHASE-9 ATOMIC LEDGER APPEND
    @Transaction
    open suspend fun appendTransactionAtomically(
        newTx: LedgerTransactionEntity
    ) {
        // 1️⃣ Replay protection
        require(!existsByTxHash(newTx.txHash)) {
            "Replay detected: transaction already exists"
        }

        // 2️⃣ Chain integrity & Genesis Validation
        val lastTx = getLastTransaction()
        if (lastTx != null) {
            require(lastTx.txHash.contentEquals(newTx.prevTxHash)) {
                "Transaction chain broken: hash mismatch with previous block"
            }
        } else {
            // Genesis Block Validation: The first transaction in an empty ledger
            // must point to an empty/zeroed previous hash.
            require(newTx.prevTxHash.all { it == 0.toByte() }) {
                "Genesis error: first transaction must have a zeroed prevTxHash"
            }
        }

        // 3️⃣ Atomic insert
        insertTransaction(newTx)
    }
}