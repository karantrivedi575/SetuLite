package com.paysetu.app.ui.payment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceivePaymentScreen(
    viewModel: PaymentViewModel,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionId by viewModel.myQrSessionId.collectAsState()
    val haptic = LocalHapticFeedback.current

    val qrBitmap = remember(sessionId) {
        sessionId?.let { QrCodeGenerator.generateQrCode(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopOfflineMode()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) {
            is PaymentUiState.Idle -> {
                Text(
                    text = "Receive Offline Payment",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (qrBitmap != null) {
                    Card(
                        elevation = CardDefaults.cardElevation(8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Payment QR Code",
                            modifier = Modifier
                                .size(260.dp)
                                .padding(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = sessionId ?: "",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Broadcasting...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Keep this screen open for the sender to scan",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = {
                        viewModel.stopOfflineMode()
                        onReject()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Cancel")
                }
            }

            is PaymentUiState.Processing -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text("Verifying Transaction...", style = MaterialTheme.typography.titleLarge)
                Text("Securing ledger entry offline", color = Color.Gray)
            }

            is PaymentUiState.Success -> {
                LaunchedEffect(Unit) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(50.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = "Success",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Payment Received!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 22.sp)

                // 💡 DISPLAY THE AMOUNT PROMINENTLY
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "₹${state.amount ?: "0"}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Transaction Details",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val timeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        ReceiptRow("Time", timeFormat.format(Date()))

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                        ReceiptRow("Tx Hash", state.txHash.take(16) + "...")
                        Spacer(modifier = Modifier.height(8.dp))

                        ReceiptRow("Status", "Verified & Added to Ledger")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Return to Dashboard", fontWeight = FontWeight.Bold)
                }
            }

            is PaymentUiState.Failure -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Text("Verification Failed", color = MaterialTheme.colorScheme.error, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(state.reason, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { viewModel.reset() }) {
                    Text("Try Again")
                }
            }
        }
    }
}

// 💡 SHARED UI HELPER
@Composable
fun ReceiptRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(
            text = value,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isBold) 18.sp else 14.sp,
            textAlign = TextAlign.End
        )
    }
}