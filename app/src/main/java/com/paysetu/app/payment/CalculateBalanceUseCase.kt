package com.paysetu.app.payment

import com.paysetu.app.ledger.LedgerDao
import com.paysetu.app.ledger.model.TransactionDirection

import kotlinx.coroutines.flow.first

class CalculateBalanceUseCase(
    private val ledgerDao: LedgerDao
) {

    suspend fun calculateBalance(): Long {
        val acceptedTxs = ledgerDao.getAcceptedTransactions().first()

        var balance = 0L
        for (tx in acceptedTxs) {
            when (tx.direction) {
                TransactionDirection.INCOMING ->
                    balance += tx.amount
                TransactionDirection.OUTGOING ->
                    balance -= tx.amount
            }
        }
        return balance
    }
}

