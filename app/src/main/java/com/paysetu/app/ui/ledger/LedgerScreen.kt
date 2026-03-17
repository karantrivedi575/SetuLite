package com.paysetu.app.ui.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity

// Helper function OUTSIDE composable
fun ByteArray.toHexString(): String =
    joinToString("") { "%02x".format(it) }

@Composable
fun TransactionItem(tx: LedgerTransactionEntity) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text("Amount: ${tx.amount}")
            Text("Status: ${tx.status.name}")
            Text("Direction: ${tx.direction.name}")

            val hashHex = tx.txHash.toHexString()

            Text("TxHash: ${hashHex.take(12)}...")
        }
    }
}

@Composable
fun LedgerScreen(ledgerDao: LedgerDao) {
    val transactions =
        ledgerDao.getAcceptedTransactions()
            .collectAsStateWithLifecycle(emptyList())

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(transactions.value) { tx ->
            TransactionItem(tx)
        }
    }
}
