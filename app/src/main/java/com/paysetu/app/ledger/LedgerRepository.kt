package com.paysetu.app.ledger.ledger

import com.paysetu.app.ledger.LedgerDao
import com.paysetu.app.ledger.ChainVerifier
import com.paysetu.app.ledger.model.LedgerTransactionEntity
import com.paysetu.app.ledger.model.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val chainVerifier: ChainVerifier
) {

    /**
     * ✅ Simple bridge for the ViewModel to get all transactions raw.
     * Use this if you want to show pending/failed transactions too.
     */
    fun getAllTransactions(): Flow<List<LedgerTransactionEntity>> {
        return ledgerDao.getAllTransactions()
    }

    /**
     * Appends a new transaction after the DAO performs its atomic
     * link and replay protection checks.
     */
    suspend fun appendTransactionAtomically(newTx: LedgerTransactionEntity) {
        ledgerDao.appendTransactionAtomically(newTx)
    }

    /**
     * Retrieves the last transaction to help the ViewModel
     * determine the prevTxHash for new payments.
     */
    suspend fun getLastTransaction(): LedgerTransactionEntity? {
        return ledgerDao.getLastTransaction()
    }

    /**
     * Returns a stream of accepted transactions, but only if the
     * entire chain passes the cryptographic integrity audit.
     */
    fun getVerifiedTransactions(): Flow<List<LedgerTransactionEntity>> {
        return ledgerDao.getAcceptedTransactions()
            .map { transactions: List<LedgerTransactionEntity> -> // Explicit type added to fix inference error
                if (transactions.isNotEmpty() && !chainVerifier.verifyChain(transactions)) {
                    throw IllegalStateException("Ledger integrity compromised! Chain verification failed.")
                }
                transactions
            }
    }

    /**
     * ✅ PHASE 10: Backend Arbiter Override & Phase 17 ACK Updates
     * Updates a transaction status locally based on backend sync or SMS ACK.
     */
    suspend fun updateTransactionStatus(txHash: ByteArray, newStatus: TransactionStatus) { // 💡 Renamed to match ViewModel!
        ledgerDao.updateTransactionStatus(txHash, newStatus)
    }

    /**
     * ✅ PHASE 10: Unsynced Data Collection
     * Fetches all local transactions marked as PENDING to send to the backend.
     */
    suspend fun getUnsyncedTransactions(): List<LedgerTransactionEntity> {
        return ledgerDao.getUnsyncedTransactions()
    }

    suspend fun isTransactionSeen(txHash: ByteArray): Boolean {
        return ledgerDao.existsByTxHash(txHash)
    }

    /**
     * Manual trigger to verify the current state of the ledger.
     * Useful for startup health checks.
     */
    suspend fun auditLocalLedger(): Boolean {
        val transactions = ledgerDao.getAcceptedTransactions().first()
        return chainVerifier.verifyChain(transactions)
    }
}