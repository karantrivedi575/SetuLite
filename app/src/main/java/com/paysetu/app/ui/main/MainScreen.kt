package com.paysetu.app.ui.main

import PaymentViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.paysetu.app.data.ledger.dao.LedgerDao
import com.paysetu.app.ui.ledger.LedgerScreen
import com.paysetu.app.ui.payment.ReceivePaymentScreen
import com.paysetu.app.ui.payment.SendPaymentScreen

@Composable
fun MainScreen(
    viewModel: PaymentViewModel,
    ledgerDao: LedgerDao
) {
    var screen by remember { mutableStateOf("HOME") }

    when (screen) {
        "HOME" -> Column {
            Button(onClick = { screen = "SEND" }) { Text("Send") }
            Button(onClick = { screen = "RECEIVE" }) { Text("Receive") }
            Button(onClick = { screen = "LEDGER" }) { Text("Ledger") }
        }

        "SEND" -> SendPaymentScreen(viewModel)
        "RECEIVE" -> ReceivePaymentScreen(
            onAccept = { screen = "SEND" },
            onReject = { screen = "HOME" }
        )
        "LEDGER" -> LedgerScreen(ledgerDao)
    }
}
