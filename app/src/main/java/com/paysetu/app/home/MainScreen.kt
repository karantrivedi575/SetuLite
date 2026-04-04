// File: MainScreen.kt
package com.paysetu.app.home

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.paysetu.app.ledger.ledger.LedgerRepository
import com.paysetu.app.ledger.model.TransactionDirection
import com.paysetu.app.ledger.ui.TransactionItem
import com.paysetu.app.ledger.ui.LedgerScreen
import com.paysetu.app.payment.AddFundsScreen
import com.paysetu.app.payment.PaymentViewModel
import com.paysetu.app.payment.ReceivePaymentScreen
import com.paysetu.app.payment.SendPaymentScreen
import com.paysetu.app.connectivity.ui.PermissionGate

// 💡 NEW: Import from our unified theme! Replaces local palette, glassCard, and neonGlow.
import com.paysetu.app.Core.theme.*

// 💡 THE FIX: Isolated the infinite animation into its own Composable.
// Now, only this tiny row recomposes 60 times a second, instead of the entire app UI.
@Composable
fun IntegrityBadge(isVerified: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "securityPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isVerified) EmeraldGreen.copy(alpha = 0.10f) else RoseError.copy(alpha = 0.10f))
            .border(1.dp, if (isVerified) EmeraldGreen.copy(alpha = 0.2f) else RoseError.copy(alpha = 0.2f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (isVerified) Icons.Default.Shield else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isVerified) EmeraldGreen.copy(alpha = pulseAlpha) else RoseError,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isVerified) "Secured by Hardware" else "Integrity Compromised",
            fontSize = 12.sp,
            color = if (isVerified) EmeraldGreen else RoseError,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    paymentViewModel: PaymentViewModel,
    dashboardViewModel: DashboardViewModel,
    ledgerRepository: LedgerRepository
) {
    var currentScreen by remember { mutableStateOf("HOME") }
    val uiState by dashboardViewModel.uiState.collectAsState()
    val history by paymentViewModel.ledgerHistory.collectAsState()
    val isVerified by paymentViewModel.isLedgerIntact.collectAsState()

    val targetBalance = remember(history) {
        history.sumOf {
            if (it.direction == TransactionDirection.INCOMING) it.amount else -it.amount
        }
    }

    var triggerAnimation by remember { mutableStateOf(false) }

    // 💡 THE FIX: Added a tiny delay. Compose state batches updates, so setting
    // false then true immediately in the same frame gets ignored.
    LaunchedEffect(currentScreen) {
        if (currentScreen == "HOME") {
            triggerAnimation = false
            delay(50)
            triggerAnimation = true
        }
    }

    val animatedBalanceState = animateFloatAsState(
        targetValue = if (triggerAnimation) targetBalance.toFloat() else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "balanceAnimation"
    )

    LaunchedEffect(currentScreen) {
        when (currentScreen) {
            "RECEIVE" -> {
                Log.d("PaySetu_UI", "Entering Receive Mode...")
                paymentViewModel.startReceivingOffline()
            }
            "SEND" -> {
                Log.d("PaySetu_UI", "Entering Send Mode...")
            }
            else -> {
                Log.d("PaySetu_UI", "Entering Safe Mode. Terminating active radios.")
                paymentViewModel.stopOfflineMode()
            }
        }
    }

    when (currentScreen) {
        "HOME" -> {
            Box(modifier = Modifier.fillMaxSize().background(DeepSlateGradient)) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text("PaySetu", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = Color.White)
                            },
                            actions = {
                                IconButton(onClick = { dashboardViewModel.triggerManualSync() }) {
                                    Icon(
                                        Icons.Default.Sync,
                                        contentDescription = "Sync",
                                        tint = if (uiState.isSyncing) EmeraldGreen else SlateBlue
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard() // 💡 Replaced crispGlass with our centralized glassCard
                                    .padding(vertical = 32.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Total Balance", style = MaterialTheme.typography.titleMedium, color = SlateBlue)
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "₢${animatedBalanceState.value.toLong()}",
                                    modifier = Modifier.neonGlow(EmeraldGreen.copy(alpha = 0.15f)), // 💡 Sourced from UIComponents.kt
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-1.5).sp,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { currentScreen = "ADD_FUNDS" },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Credits", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Funds", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                IntegrityBadge(isVerified = isVerified)
                            }
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                val trustColor = if (uiState.trustScore >= 90) EmeraldGreen else if (uiState.trustScore >= 50) Color(0xFFFACC15) else RoseError
                                Box(modifier = Modifier.weight(1f)) {
                                    DashboardWidget("Trust Score", "${uiState.trustScore}%", trustColor, Icons.Default.Timeline)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DashboardWidget("Sync Window", "${uiState.hoursUntilSyncRequired}H", Color.White, Icons.Default.Update)
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = { currentScreen = "SEND" },
                                    modifier = Modifier.weight(1f).height(64.dp),
                                    enabled = targetBalance > 0 && uiState.trustScore >= 50,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldGreen,
                                        contentColor = Color(0xFF020617),
                                        disabledContainerColor = EmeraldGreen.copy(alpha = 0.3f),
                                        disabledContentColor = Color.Black.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Send", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { currentScreen = "RECEIVE" },
                                    modifier = Modifier.weight(1f).height(64.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.05f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Receive", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent Activity", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { currentScreen = "LEDGER" },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("See All", color = EmeraldGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }

                        if (history.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                    Text("No offline transactions yet.", color = SlateBlue)
                                }
                            }
                        } else {
                            // 💡 THE FIX: Adding a key prevents the list from brutally recomposing
                            // every time items shift.
                            items(
                                items = history.take(10),
                                key = { it.hashCode() }
                            ) { transaction ->
                                TransactionItem(transaction = transaction)
                            }
                        }

                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }

        "SEND" -> {
            PermissionGate {
                SendPaymentScreen(
                    viewModel = paymentViewModel,
                    onBack = { currentScreen = "HOME" }
                )
            }
        }

        "RECEIVE" -> {
            PermissionGate {
                ReceivePaymentScreen(
                    viewModel = paymentViewModel,
                    onAccept = { currentScreen = "HOME" },
                    onReject = { currentScreen = "HOME" }
                )
            }
        }

        "LEDGER" -> LedgerScreen(
            repository = ledgerRepository,
            onBack = { currentScreen = "HOME" },
            onNavigateToReceive = { currentScreen = "RECEIVE" }
        )

        "ADD_FUNDS" -> AddFundsScreen(
            viewModel = paymentViewModel,
            onBack = { currentScreen = "HOME" },
            onSuccess = {
                currentScreen = "HOME"
                paymentViewModel.reset()
            }
        )
    }
}

@Composable
fun DashboardWidget(label: String, value: String, valueColor: Color, bgIcon: ImageVector) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp)) // 💡 Replaced crispGlass
            .height(100.dp)
    ) {
        Icon(
            imageVector = bgIcon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.03f),
            modifier = Modifier
                .size(70.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 10.dp)
        )

        Column(
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Text(label, fontSize = 13.sp, color = SlateBlue, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = valueColor)
        }
    }
}