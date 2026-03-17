package com.paysetu.app.ui.payment

import PaymentViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SendPaymentScreen(
    viewModel: PaymentViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var amount by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text("Send Offline Payment", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") }
        )

        Button(
            onClick = {
                viewModel.sendOfflinePayment(
                    amount = amount.toLong(),
                    prevTxHash = null // Phase-9 simplification
                )
            }
        ) {
            Text("Send Payment")
        }

        when (uiState) {
            is PaymentUiState.Processing -> {
                CircularProgressIndicator()
                Text("Processing offline transaction…")
            }

            is PaymentUiState.Success -> {
                Text("✅ Payment Accepted")
                Text("TxHash: ${(uiState as PaymentUiState.Success).txHash}")
            }

            is PaymentUiState.Failure -> {
                Text("❌ Payment Failed")
                Text((uiState as PaymentUiState.Failure).reason)
            }

            else -> {}
        }
    }
}
