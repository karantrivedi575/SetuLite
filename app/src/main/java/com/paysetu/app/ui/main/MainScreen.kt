package com.paysetu.app.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.ui.ledger.LedgerScreen
import com.paysetu.app.ui.payment.PaymentViewModel
import com.paysetu.app.ui.payment.ReceivePaymentScreen
import com.paysetu.app.ui.payment.SendPaymentScreen

@Composable
fun MainScreen(
    paymentViewModel: PaymentViewModel,
    dashboardViewModel: DashboardViewModel,
    ledgerRepository: LedgerRepository
) {
    // State for navigation within MainScreen
    var currentScreen by remember { mutableStateOf("HOME") }

    // Collect UI state from the Dashboard/Security logic
    val uiState by dashboardViewModel.uiState.collectAsState()

    // ==========================================
    // 🛰️ PHASE 12: P2P LIFECYCLE MANAGER
    // This logic starts/stops radios based on navigation
    // ==========================================
    LaunchedEffect(currentScreen) {
        when (currentScreen) {
            "SEND" -> {
                paymentViewModel.startScanningForReceivers()
            }
            "RECEIVE" -> {
                // You can replace "User" with a name from preferences later
                paymentViewModel.startReceivingOffline(userName = "PaySetu_User")
            }
            "HOME", "LEDGER" -> {
                // Shut down radios when not on a payment screen to save battery
                paymentViewModel.stopOfflineMode()
            }
        }
    }

    when (currentScreen) {
        "HOME" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PaySetu Security Center",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp, top = 32.dp)
                )

                // Trust Score Card (Existing)
                TrustScoreCard(score = uiState.trustScore)

                Spacer(modifier = Modifier.height(16.dp))

                // Offline Window Card (Existing)
                OfflineWindowCard(hoursLeft = uiState.hoursUntilSyncRequired)

                Spacer(modifier = Modifier.height(24.dp))

                // Quick Action Buttons
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { currentScreen = "SEND" },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.trustScore >= 50
                    ) {
                        Text("Send")
                    }
                    Button(
                        onClick = { currentScreen = "RECEIVE" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Receive")
                    }
                }

                Button(
                    onClick = { currentScreen = "LEDGER" },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("View Verified Ledger")
                }

                Spacer(modifier = Modifier.weight(1f))

                // Sync Section (Existing)
                if (uiState.syncError != null) {
                    Text(text = uiState.syncError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { dashboardViewModel.triggerManualSync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !uiState.isSyncing
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Sync with Network", fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        "SEND" -> SendPaymentScreen(
            viewModel = paymentViewModel,
            onBack = { currentScreen = "HOME" }
        )

        "RECEIVE" -> ReceivePaymentScreen(
            // Passing the viewModel here so the Receive screen can show P2P status
            viewModel = paymentViewModel,
            onAccept = { currentScreen = "LEDGER" },
            onReject = { currentScreen = "HOME" }
        )

        "LEDGER" -> LedgerScreen(
            repository = ledgerRepository,
            onBack = { currentScreen = "HOME" }
        )
    }
}

// --- KEEPING EXISTING SUB-COMPOSABLES EXACTLY AS THEY WERE ---

@Composable
fun TrustScoreCard(score: Int) {
    val cardColor = when {
        score >= 90 -> Color(0xFF4CAF50) // Green
        score >= 50 -> Color(0xFFFFC107) // Yellow
        else -> Color(0xFFF44336)        // Red
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Trust Score", fontSize = 16.sp, color = Color.Gray)
            Text(
                text = "$score / 100",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = cardColor
            )
            if (score < 50) {
                Text("Warning: Outgoing payments disabled.", color = Color.Red, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun OfflineWindowCard(hoursLeft: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Offline Window", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Time until sync required", fontSize = 14.sp, color = Color.Gray)
            }
            Text(
                text = "${hoursLeft}h",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (hoursLeft <= 0) Color.Red else MaterialTheme.colorScheme.primary
            )
        }
    }
}