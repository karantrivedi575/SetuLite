package com.paysetu.app.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// 💎 IMPORT OUR UNIFIED THEME AND COMPONENTS
import com.paysetu.app.Core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFundsScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    // ⌨️ Keyboard Focus Management
    val focusManager = LocalFocusManager.current
    val amountFocusRequester = remember { FocusRequester() }

    // 💡 Strict Validation Logic (Untouched)
    val amountLong = amountText.toLongOrNull() ?: 0L
    val isAmountInvalid = amountText.isNotEmpty() && amountLong <= 0
    val isExceedingLimit = amountLong > 10000 // Max single top-up limit

    // 💡 Handle Success Navigation (Untouched)
    LaunchedEffect(uiState) {
        if (uiState is PaymentUiState.Success) {
            onSuccess()
        }
    }

    // Auto-pop the keyboard when the screen loads smoothly
    LaunchedEffect(Unit) {
        delay(300)
        amountFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlateGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🔹 UNIFIED TOP BAR
            PaySetuTopBar(title = "Add Credits", onBack = onBack)

            // 🏦 SOURCE BANK CARD (Frosted Glass)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalance, null, tint = AccentGold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Transferring From", color = SoftText, fontSize = 12.sp)
                        Text("Linked Bank Account", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 💰 UNIFIED AMOUNT INPUT
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Enter Amount", color = SoftText, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                AmountInputPad(
                    amountText = amountText,
                    onAmountChange = { amountText = it },
                    symbol = "₹",
                    hasError = isExceedingLimit || isAmountInvalid,
                    maxLength = 5,
                    focusRequester = amountFocusRequester,
                    onDone = { focusManager.clearFocus() },
                    cursorColor = AccentGold // Uses Gold instead of Emerald for Bank transfers
                )

                // Dynamic Validation Text
                val helperText = when {
                    isExceedingLimit -> "Maximum top-up limit is ₹10,000"
                    isAmountInvalid -> "Amount must be greater than zero"
                    else -> "Whole numbers only. Max limit: ₹10,000"
                }
                val helperColor = if (isExceedingLimit || isAmountInvalid) RoseError else SoftText

                Text(
                    text = helperText,
                    color = helperColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 🛡️ SECURITY FOOTNOTE
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(EmeraldGreen.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Secured by Hardware KeyStore",
                    color = EmeraldGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 🚀 AUTHORIZE BUTTON
            Button(
                onClick = {
                    focusManager.clearFocus() // Ensure keyboard drops when they authorize
                    if (amountLong > 0 && !isExceedingLimit) {
                        viewModel.addCreditsFromBank(amountLong)
                    }
                },
                enabled = amountText.isNotEmpty() && amountLong > 0 && !isExceedingLimit && uiState !is PaymentUiState.Processing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.1f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                )
            ) {
                if (uiState is PaymentUiState.Processing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 3.dp)
                } else {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AUTHORIZE TRANSFER", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}