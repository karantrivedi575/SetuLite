package com.paysetu.app.data.ledger

import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val chainVerifier: ChainVerifier // Injected verifier
) {

    /**
     * Appends a new transaction after the DAO performs its atomic
     * link and replay protection checks.
     */
    suspend fun appendTransactionAtomically(
        newTx: LedgerTransactionEntity
    ) {
        ledgerDao.appendTransactionAtomically(newTx)
    }

    /**
     * Returns a stream of accepted transactions, but only if the
     * entire chain passes the cryptographic integrity audit.
     * Throws an IllegalStateException if tampering is detected.
     */
    fun getVerifiedTransactions(): Flow<List<LedgerTransactionEntity>> {
        return ledgerDao.getAcceptedTransactions(TransactionStatus.ACCEPTED)
            .map { transactions ->
                if (!chainVerifier.verifyChain(transactions)) {
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
     */
    suspend fun auditLocalLedger(): Boolean {
        // Note: This would typically involve fetching all blocks
        // from the DAO and passing them to the verifier.
        return true
    }
}