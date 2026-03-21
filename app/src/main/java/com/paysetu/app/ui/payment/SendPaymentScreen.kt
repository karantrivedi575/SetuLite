package com.paysetu.app.ui.payment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendPaymentScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // 💡 Collect the discovered devices from the ViewModel
    val discoveredReceivers by viewModel.discoveredReceivers.collectAsState(initial = emptyMap())

    var selectedReceiverId by remember { mutableStateOf<String?>(null) }
    var selectedReceiverName by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Send Offline Payment", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        if (selectedReceiverId == null) {
            // ==========================================
            // STEP 1: SCANNING AND SELECTION STATE
            // ==========================================
            Text("Scanning for nearby devices...", color = Color.Gray)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (discoveredReceivers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No receivers found yet.\nEnsure the other device is on the 'Receive' screen.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(discoveredReceivers.entries.toList()) { receiver ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // User tapped a device! Move to Step 2.
                                    selectedReceiverId = receiver.key
                                    selectedReceiverName = receiver.value
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(receiver.value, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }

        } else {
            // ==========================================
            // STEP 2: ENTER AMOUNT AND SEND STATE
            // ==========================================
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Sending to: ", fontWeight = FontWeight.Bold)
                    Text(selectedReceiverName ?: "")
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (₹)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { selectedReceiverId = null }, // Go back to the scanning list
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }

                Button(
                    onClick = {
                        val amountLong = amount.toLongOrNull() ?: 0L
                        // 💡 Trigger the full P2P transfer!
                        viewModel.sendOfflinePayment(amountLong, selectedReceiverId!!)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = uiState !is PaymentUiState.Processing && amount.isNotEmpty()
                ) {
                    Text("Send via P2P")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // STEP 3: TRANSACTION STATUS
            // ==========================================
            when (val state = uiState) {
                is PaymentUiState.Processing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                        Text("Connecting & Transferring…", modifier = Modifier.padding(top = 8.dp))
                    }
                }
                is PaymentUiState.Success -> {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✅ Payment Sent Successfully!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            Text("TxHash: ${state.txHash.take(12)}...", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onBack) { Text("Return to Dashboard") }
                        }
                    }
                }
                is PaymentUiState.Failure -> {
                    Text("❌ Payment Failed", color = MaterialTheme.colorScheme.error)
                    Text(state.reason)
                }
                else -> {}
            }
        }
    }
}