package com.paysetu.app.ui.payment

import android.Manifest
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SendPaymentScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var amount by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    // 💡 PHASE 14: Track the camera permission state
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Send Offline Payment", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // ==========================================
        // 💡 PHASE 14: THE CAMERA SCANNER STATE
        // ==========================================
        if (isScanning) {
            // Check if we actually have permission to use the camera
            if (cameraPermissionState.status.isGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    QrScannerView(
                        onCodeScanned = { scannedCode ->
                            Log.d("PaySetu_P2P", "QR Scanned: $scannedCode")
                            isScanning = false // Close camera instantly
                            val amountLong = amount.toLongOrNull() ?: 0L

                            // 💡 Fire the targeted discovery.
                            // The ViewModel now has a 1s delay to let radios warm up.
                            viewModel.startTargetedDiscovery(scannedCode, amountLong)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay Instructions
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))
                    ) {
                        Text(
                            "Scan Receiver's QR Code",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                // UI to ask for Camera Permission
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera permission is required to scan the receiver's QR code.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Camera Permission")
                    }
                }
            }

            Button(
                onClick = { isScanning = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Cancel Scan")
            }
        }
        else {
            // ==========================================
            // 💡 PHASE 14: STANDARD UI STATES
            // ==========================================
            when (val state = uiState) {

                // STEP 1: ENTER AMOUNT
                is PaymentUiState.Idle, is PaymentUiState.Failure -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = { Text("Amount to Send (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (state is PaymentUiState.Failure) {
                        Text("❌ Payment Failed: ${state.reason}", color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.stopOfflineMode()
                                onBack()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = { isScanning = true },
                            modifier = Modifier.weight(1f),
                            enabled = amount.isNotEmpty() && (amount.toLongOrNull() ?: 0) > 0
                        ) {
                            Text("Scan to Pay")
                        }
                    }
                }

                // STEP 2: CONNECTING AND SENDING
                is PaymentUiState.Processing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Target Locked...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Connecting securely & transferring funds", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    }
                }

                // STEP 3: SUCCESS
                is PaymentUiState.Success -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("✅ Payment Sent!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("TxHash: ${state.txHash.take(12)}...", style = MaterialTheme.typography.labelMedium, color = Color.Gray)

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                viewModel.reset()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Return to Dashboard", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}