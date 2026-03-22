package com.paysetu.app.ui.payment

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.paysetu.app.ui.payment.utils.MiuiPermissionUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun XiaomiGuard(context: Context) {
    if (!MiuiPermissionUtils.isXiaomi()) return

    var isMiuiAllowed by remember { mutableStateOf(MiuiPermissionUtils.isBackgroundStartAllowed(context)) }
    var isBatteryOptimized by remember { mutableStateOf(!MiuiPermissionUtils.isBatteryOptimizationIgnored(context)) }

    val allFixed = isMiuiAllowed && !isBatteryOptimized
    var showCard by remember { mutableStateOf(!allFixed) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 💡 REAL-TIME BURST POLLING: Check 3 times to account for System DB lag
                coroutineScope.launch {
                    repeat(3) {
                        isMiuiAllowed = MiuiPermissionUtils.isBackgroundStartAllowed(context)
                        isBatteryOptimized = !MiuiPermissionUtils.isBatteryOptimizationIgnored(context)

                        if (isMiuiAllowed && !isBatteryOptimized) return@launch // Stop polling if successful
                        delay(500) // Wait half a second before checking again
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(allFixed) {
        if (allFixed) {
            delay(2000) // Let them see the green ticks for 2 seconds
            showCard = false
        }
    }

    if (showCard) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Xiaomi Turbo Optimization",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionRow(
                    title = "MIUI Background Pop-ups",
                    isGranted = isMiuiAllowed,
                    onFixClick = { MiuiPermissionUtils.openMiuiPermissionSettings(context) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                )

                PermissionRow(
                    title = "Battery: No Restrictions",
                    isGranted = !isBatteryOptimized,
                    onFixClick = { MiuiPermissionUtils.requestIgnoreBatteryOptimizations(context) }
                )
            }
        }
    }
}

@Composable
fun PermissionRow(title: String, isGranted: Boolean, onFixClick: () -> Unit) {
    val backgroundColor = if (isGranted) Color(0xFFE8F5E9) else Color.Transparent
    val contentColor = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isGranted) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )

        if (!isGranted) {
            Button(
                onClick = onFixClick,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Fix", fontSize = 12.sp)
            }
        } else {
            Text(
                "Active",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SendPaymentScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var hasTriggeredTransfer by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    DisposableEffect(Unit) {
        onDispose {
            Log.d("PaySetu_UI", "Leaving Send Screen. Stopping all radios.")
            viewModel.stopOfflineMode()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Send Offline Payment", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        XiaomiGuard(context)

        if (isScanning) {
            if (cameraPermissionState.status.isGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    QrScannerView(
                        onCodeScanned = { scannedCode ->
                            if (uiState !is PaymentUiState.Processing && !hasTriggeredTransfer) {
                                hasTriggeredTransfer = true
                                Log.d("PaySetu_P2P", "QR Scanned: $scannedCode")
                                isScanning = false
                                val amountLong = amount.toLongOrNull() ?: 0L

                                coroutineScope.launch {
                                    delay(300)
                                    viewModel.startTargetedDiscovery(scannedCode, amountLong)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

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
            when (val state = uiState) {
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
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = state !is PaymentUiState.Processing
                            )
                        }
                    }

                    if (state is PaymentUiState.Failure) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "❌ ${state.reason}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
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
                            onClick = {
                                hasTriggeredTransfer = false
                                isScanning = true
                            },
                            modifier = Modifier.weight(1f),
                            enabled = amount.isNotEmpty() && (amount.toLongOrNull() ?: 0) > 0 && state !is PaymentUiState.Processing
                        ) {
                            Text("Scan to Pay")
                        }
                    }
                }

                is PaymentUiState.Processing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Target Locked...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Connecting securely & transferring funds.\nPlease keep devices close.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
                        )

                        Spacer(modifier = Modifier.height(48.dp))
                        TextButton(onClick = { viewModel.stopOfflineMode() }) {
                            Text("Stop Connection Attempt", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                is PaymentUiState.Success -> {
                    LaunchedEffect(Unit) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
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

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Payment Sent Successfully!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(32.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                ReceiptRow("Amount Sent", "₹$amount", isBold = true)

                                // 💡 MODERN HORIZONTAL DIVIDER
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )

                                val timeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                ReceiptRow("Time", timeFormat.format(Date()))
                                Spacer(modifier = Modifier.height(8.dp))

                                ReceiptRow("Transaction Hash", state.txHash.take(16) + "...")
                                Spacer(modifier = Modifier.height(8.dp))

                                ReceiptRow("Status", "Verified & Secured Offline")
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                viewModel.reset()
                                onBack()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("Return to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}