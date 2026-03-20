package com.paysetu.app.data.ledger

import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionStatus
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
        return ledgerDao.getAcceptedTransactions() // Uses the safe wrapper we added to Dao
            .map { transactions ->
                if (transactions.isNotEmpty() && !chainVerifier.verifyChain(transactions)) {
                    throw IllegalStateException("Ledger integrity compromised! Chain verification failed.")
                }
                transactions
            }
    }

    suspend fun isTransactionSeen(txHash: ByteArray): Boolean {
        return ledgerDao.existsByTxHash(txHash)
    }

    /**
     * Manual trigger to verify the current state of the ledger.
     * Useful for startup health checks.
     */
    suspend fun auditLocalLedger(): Boolean {
        // We use the raw list to audit
        val transactions = ledgerDao.getAcceptedTransactions().first()
        return chainVerifier.verifyChain(transactions)
    }
}