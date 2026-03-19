package com.paysetu.app.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendPaymentScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit // Added missing navigation callback
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
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    val amountLong = amount.toLongOrNull() ?: 0L
                    viewModel.sendOfflinePayment(
                        amount = amountLong,
                        prevTxHash = null // Properly handled by validatedPrevHash in ViewModel
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = uiState !is PaymentUiState.Processing
            ) {
                Text("Send")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is PaymentUiState.Processing -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Processing offline transaction…")
                }
            }

            is PaymentUiState.Success -> {
                Text("✅ Payment Accepted", color = MaterialTheme.colorScheme.primary)
                Text("TxHash: ${state.txHash}", style = MaterialTheme.typography.labelSmall)
            }

            is PaymentUiState.Failure -> {
                Text("❌ Payment Failed", color = MaterialTheme.colorScheme.error)
                Text(state.reason)
            }

            else -> {}
        }
    }
}