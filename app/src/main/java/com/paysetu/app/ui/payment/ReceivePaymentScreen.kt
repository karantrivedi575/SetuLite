package com.paysetu.app.ui.payment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 💎 PREMIUM FINTECH PALETTE
private val DeepSlateGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
private val EmeraldGreen = Color(0xFF10B981)
private val RoseError = Color(0xFFF43F5E)
private val SlateBlue = Color(0xFF94A3B8)
private val SoftText = Color.White.copy(alpha = 0.7f)

// 💎 CRISP GLASSMORPHISM UTILITY (Updated with 0.5dp Depth)
private fun Modifier.glassCard(shape: RoundedCornerShape = RoundedCornerShape(24.dp)) = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.05f))
    .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape) // 💡 0.5dp subtle depth stroke

@Composable
fun ReceivePaymentScreen(
    viewModel: PaymentViewModel,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionId by viewModel.myQrSessionId.collectAsState()

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val qrBitmap = remember(sessionId) {
        sessionId?.let { QrCodeGenerator.generateQrCode(it) }
    }

    // 🛡️ THE EXORCISM: Failsafe state cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            Log.d("PaySetu_UI", "Leaving Receive Screen. Stopping radios and resetting state.")
            viewModel.stopOfflineMode()
            viewModel.reset() // 💡 Ensures state isn't stuck if user system-swipes back
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepSlateGradient)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 🔹 Custom Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.stopOfflineMode()
                        viewModel.reset() // 💡 Clean state
                        onReject()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("Receive Credits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val state = uiState) {
                    is PaymentUiState.Idle -> {
                        if (qrBitmap != null) {

                            // 💎 BRANDED QR CONTAINER
                            Box(contentAlignment = Alignment.Center) {
                                // Background Glow
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .background(EmeraldGreen.copy(alpha = 0.03f), RoundedCornerShape(32.dp))
                                )

                                // The QR Frame
                                Surface(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .padding(16.dp),
                                    color = Color.White,
                                    shape = RoundedCornerShape(24.dp),
                                    tonalElevation = 8.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {

                                        // The Actual QR Code
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Payment QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // 💡 FIX: Disabled BRANDED LOGO CENTER to prevent data matrix corruption
                                        // Unless QrCodeGenerator uses Error Correction Level H, this will block ML Kit.
                                        /*
                                        Surface(
                                            modifier = Modifier.size(48.dp),
                                            color = Color.White,
                                            shape = CircleShape,
                                            border = BorderStroke(3.dp, Color.White)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF020617)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                // Tiny logo placeholder
                                                Text("P", color = EmeraldGreen, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                            }
                                        }
                                        */
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp), color = EmeraldGreen)
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // 📡 BROADCASTING STATUS (Animated Pulse)
                        val infiniteTransition = rememberInfiniteTransition(label = "broadcast")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                            label = "alpha"
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldGreen.copy(alpha = pulseAlpha)))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Broadcasting Secure Signal...",
                                color = EmeraldGreen.copy(alpha = pulseAlpha),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // 📋 QUICK ACTIONS & ID
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(RoundedCornerShape(20.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Your Offline Node ID", color = SlateBlue, fontSize = 12.sp)
                            Text(
                                text = sessionId ?: "GENERATING...",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickActionButton(Icons.Default.ContentCopy, "Copy") {
                                    if (sessionId != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("PaySetu ID", sessionId))
                                        Toast.makeText(context, "ID Copied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                QuickActionButton(Icons.Default.Share, "Share") {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Standard Android Share Intent logic would go here
                                    Toast.makeText(context, "Share sheet opening...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        OutlinedButton(
                            onClick = {
                                viewModel.stopOfflineMode()
                                viewModel.reset() // 💡 Clean state
                                onReject()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Cancel Request", fontWeight = FontWeight.Bold)
                        }
                    }

                    is PaymentUiState.Processing -> {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp), color = EmeraldGreen, strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Verifying Transaction...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Securing ledger entry offline.\nPlease keep devices close.",
                            color = SoftText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    is PaymentUiState.Success -> {
                        // 💡 HAPTIC SUCCESS THUD
                        LaunchedEffect(Unit) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        // 💎 Glass Success Icon
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .glassCard(CircleShape)
                                .background(EmeraldGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Success",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(50.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Payment Received!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("Funds verified and added to ledger.", color = EmeraldGreen, fontSize = 14.sp)

                        Spacer(modifier = Modifier.height(24.dp))

                        // 💡 AMOUNT TYPOGRAPHY (Bold Emerald)
                        Text(
                            text = "+₢${state.amount ?: "0"}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldGreen
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Column(modifier = Modifier.fillMaxWidth().glassCard().padding(24.dp)) {
                            val timeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            ReceiptRow("Time Received", timeFormat.format(Date()))

                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))

                            ReceiptRow("TX Hash", state.txHash.take(12).uppercase() + "...")
                            Spacer(modifier = Modifier.height(12.dp))
                            ReceiptRow("Status", "Verified Offline", valueColor = EmeraldGreen)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                viewModel.reset() // 💡 Clean state so we don't get stuck!
                                onAccept()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    is PaymentUiState.Failure -> {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .glassCard(CircleShape)
                                .background(RoseError.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Error", tint = RoseError, modifier = Modifier.size(50.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Verification Failed", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().glassCard().background(RoseError.copy(alpha = 0.1f)).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = RoseError)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(state.reason, color = RoseError, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Text("Try Again", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

// 💡 SHARED UI HELPERS
@Composable
private fun QuickActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = SlateBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, isBold: Boolean = false, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = SoftText, fontSize = if (isBold) 16.sp else 14.sp)
        Text(
            text = value,
            color = valueColor,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.Medium,
            fontSize = if (isBold) 18.sp else 14.sp,
            textAlign = TextAlign.End,
            fontFamily = if (label.contains("Hash")) FontFamily.Monospace else FontFamily.SansSerif
        )
    }
}