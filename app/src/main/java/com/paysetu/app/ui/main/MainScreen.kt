package com.paysetu.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.ui.ledger.LedgerScreen
import com.paysetu.app.ui.payment.PaymentViewModel
import com.paysetu.app.ui.payment.ReceivePaymentScreen
import com.paysetu.app.ui.payment.SendPaymentScreen

@Composable
fun MainScreen(
    viewModel: PaymentViewModel,
    ledgerRepository: LedgerRepository // Updated from LedgerDao to LedgerRepository
) {
    var screen by remember { mutableStateOf("HOME") }

    when (screen) {
        "HOME" -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PaySetu Lite",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Button(
                onClick = { screen = "SEND" },
                modifier = Modifier.padding(8.dp)
            ) { Text("Send Payment") }

            Button(
                onClick = { screen = "RECEIVE" },
                modifier = Modifier.padding(8.dp)
            ) { Text("Receive Payment") }

            Button(
                onClick = { screen = "LEDGER" },
                modifier = Modifier.padding(8.dp)
            ) { Text("View Verified Ledger") }
        }

        "SEND" -> SendPaymentScreen(
            viewModel = viewModel,
            onBack = { screen = "HOME" }
        )

        "RECEIVE" -> ReceivePaymentScreen(
            onAccept = { screen = "LEDGER" }, // Navigate to ledger to see the new verified TX
            onReject = { screen = "HOME" }
        )

        "LEDGER" -> LedgerScreen(
            repository = ledgerRepository // Passes the repository for chain verification
        )
    }
}