package com.paysetu.app.data.ledger

import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity

class LedgerRepository(
    private val ledgerDao: LedgerDao
) {

    suspend fun appendTransactionAtomically(
        newTx: LedgerTransactionEntity
    ) {
        ledgerDao.appendTransactionAtomically(newTx)
    }

    suspend fun isTransactionSeen(txHash: ByteArray): Boolean {
        return ledgerDao.existsByTxHash(txHash)
    }
}
