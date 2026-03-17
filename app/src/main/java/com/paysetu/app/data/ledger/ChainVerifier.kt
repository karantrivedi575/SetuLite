package com.paysetu.app.data.ledger

import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity

class ChainVerifier {

    fun verifyChain(
        transactions: List<LedgerTransactionEntity>
    ): Boolean {

        for (i in 1 until transactions.size) {
            if (!transactions[i].prevTxHash
                    .contentEquals(transactions[i - 1].txHash)
            ) {
                return false
            }
        }
        return true
    }
}
