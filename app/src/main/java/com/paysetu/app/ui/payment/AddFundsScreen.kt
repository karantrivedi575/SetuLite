package com.paysetu.app.ui.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// 💎 PREMIUM PALETTE
private val DeepSlateGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
private val AccentGold = Color(0xFFFACC15) // Gold for "Banking/Value" context
private val EmeraldGreen = Color(0xFF10B981)
private val RoseError = Color(0xFFF43F5E)
private val SoftText = Color.White.copy(alpha = 0.6f)

// 💎 GLASSMORPHISM UTILITY
private fun Modifier.glassCard(shape: RoundedCornerShape = RoundedCornerShape(24.dp)) = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.05f))
    .border(1.dp, Color.White.copy(alpha = 0.1f), shape)

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
            // 🔹 Custom Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Add Credits",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

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

            // 💰 NATIVE KEYBOARD AMOUNT DISPLAY
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Enter Amount", color = SoftText, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "₹",
                        fontSize = 32.sp,
                        color = if (amountText.isEmpty()) Color.White.copy(alpha = 0.2f) else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))

                    BasicTextField(
                        value = amountText,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            // 10000 is 5 digits long, so limit input to 5 characters
                            if (filtered.length <= 5) {
                                amountText = filtered
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword, // Best for avoiding text suggestions
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        textStyle = TextStyle(
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isExceedingLimit) RoseError else Color.White,
                            letterSpacing = (-1.5).sp,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(AccentGold),
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .defaultMinSize(minWidth = 50.dp)
                            .focusRequester(amountFocusRequester),
                        decorationBox = { innerTextField ->
                            Box {
                                if (amountText.isEmpty()) {
                                    Text(
                                        text = "0",
                                        fontSize = 56.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White.copy(alpha = 0.2f),
                                        letterSpacing = (-1.5).sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

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