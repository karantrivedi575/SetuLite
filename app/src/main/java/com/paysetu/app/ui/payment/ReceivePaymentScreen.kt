package com.paysetu.app.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReceivePaymentScreen(
    viewModel: PaymentViewModel, // Added the ViewModel
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    // Collect the UI state (Loading, Success, Idle, etc.)
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (uiState) {
            is PaymentUiState.Idle -> {
                // Initial state while broadcasting
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Broadcasting...",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Open the Send screen on the other device",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(onClick = onReject, colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)) {
                    Text("Cancel", color = Color.Black)
                }
            }

            is PaymentUiState.Processing -> {
                // State when the payload has been received and is being verified/saved
                Text("Verifying Transaction...", fontSize = 18.sp)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            }

            is PaymentUiState.Success -> {
                // Payload received!
                val hash = (uiState as PaymentUiState.Success).txHash
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Done,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Payment Received!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Hash: ${hash.take(12)}...", color = Color.Gray)

                Spacer(modifier = Modifier.height(32.dp))

                Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                    Text("View in Ledger")
                }
            }

            is PaymentUiState.Failure -> {
                Text("Payment Failed", color = Color.Red, fontSize = 20.sp)
                Text((uiState as PaymentUiState.Failure).reason)
                Button(onClick = { viewModel.reset() }) {
                    Text("Try Again")
                }
            }
        }
    }
}