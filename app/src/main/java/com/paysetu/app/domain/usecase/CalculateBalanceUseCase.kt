package com.paysetu.app.domain.usecase

import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.data.ledger.entity.TransactionDirection

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

