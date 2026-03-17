package com.paysetu.app.data.ledger.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
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

    @Query("""
    SELECT * FROM ledger_transactions
    WHERE status = 'ACCEPTED'
    ORDER BY id ASC
""")
    abstract fun getAcceptedTransactions(): Flow<List<LedgerTransactionEntity>>


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

        // 2️⃣ Chain integrity
        val lastTx = getLastTransaction()
        if (lastTx != null) {
            require(
                lastTx.txHash.contentEquals(newTx.prevTxHash)
            ) {
                "Transaction chain broken"
            }
        }

        // 3️⃣ Atomic insert
        insertTransaction(newTx)
    }
}
