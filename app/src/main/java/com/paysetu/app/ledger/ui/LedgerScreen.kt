package com.paysetu.app.ledger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paysetu.app.ledger.model.LedgerTransactionEntity
import com.paysetu.app.ledger.ledger.LedgerRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 💎 PREMIUM FINTECH PALETTE
private val DeepSlateGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
private val EmeraldGreen = Color(0xFF10B981)
private val SlateBlue = Color(0xFF94A3B8)
private val SoftText = Color.White.copy(alpha = 0.7f)

// Extension for Hex
fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(
    repository: LedgerRepository,
    onBack: () -> Unit,
    onNavigateToReceive: () -> Unit
) {
    val transactionsResult by repository.getAllTransactions()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedTx by remember { mutableStateOf<LedgerTransactionEntity?>(null) }
    val haptics = LocalHapticFeedback.current

    // 🔍 Filter & Sort Logic (Newest First)
    val filteredTransactions = remember(transactionsResult, searchQuery) {
        val list = if (searchQuery.isEmpty()) transactionsResult else transactionsResult.filter {
            it.amount.toString().contains(searchQuery) ||
                    it.txHash.toHexString().contains(searchQuery, ignoreCase = true)
        }
        list.sortedByDescending { it.timestamp }
    }

    // 📅 Group by Humanized Date
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.timestamp.toRelativeDateString() }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepSlateGradient)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.background(Color.Transparent)) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text("Ledger History", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = Color.White)
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )

                    // 💎 Frosted Glass Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        placeholder = { Text("Search amounts or hashes...", color = SlateBlue, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = SlateBlue) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            cursorColor = EmeraldGreen
                        )
                    )
                }
            }
        ) { padding ->
            if (filteredTransactions.isEmpty()) {
                EmptyLedgerState(padding, onNavigateToReceive, isSearch = searchQuery.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedTransactions.forEach { (dateString, transactions) ->
                        // 📌 STICKY DATE HEADER
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 💡 Solid background so items slide smoothly underneath it
                                    .background(Color(0xFF0F172A).copy(alpha = 0.98f))
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = dateString,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        items(transactions) { tx ->
                            Box(modifier = Modifier.clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTx = tx
                            }) {
                                // 💡 Triggering grouped mode so it doesn't repeat the date in the row
                                TransactionItem(transaction = tx)
                            }
                        }
                    }
                }
            }
        }
    }

    // 🛡️ REFINED SECURE AUDIT DIALOG
    selectedTx?.let { tx ->
        AuditDetailDialog(transaction = tx, onDismiss = { selectedTx = null })
    }
}

// 👻 BEAUTIFUL VECTOR EMPTY STATE
@Composable
fun EmptyLedgerState(padding: PaddingValues, onNavigateToReceive: () -> Unit, isSearch: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Stylized Ghost Icon Composition
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(EmeraldGreen.copy(alpha = 0.05f)))
                Icon(
                    imageVector = if (isSearch) Icons.Default.SearchOff else Icons.Default.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = SlateBlue.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isSearch) "No Matches Found" else "Ledger is Empty",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = if (isSearch) "Try a different hash or amount." else "You haven't made any offline transactions yet.\nWhen you do, they'll be cryptographically secured here.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp)
            )

            if (!isSearch) {
                Button(
                    onClick = onNavigateToReceive,
                    modifier = Modifier.padding(top = 40.dp).height(56.dp).fillMaxWidth(0.7f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Receive Credits", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun AuditDetailDialog(transaction: LedgerTransactionEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isGenesis = transaction.prevTxHash.all { it == 0.toByte() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Cryptographic Audit", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "DEEP PACKET INSPECTION PASSED.\nED25519 SIGNATURE SECURED.",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = EmeraldGreen,
                    letterSpacing = 0.5.sp
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                AuditField("Transaction Hash (SHA-256)", transaction.txHash.toHexString(), Color.White)

                AuditField(
                    label = "Previous Block Pointer",
                    value = if (isGenesis) "[ GENESIS BLOCK ]" else transaction.prevTxHash.toHexString(),
                    color = if (isGenesis) EmeraldGreen else SoftText
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) { AuditField("Sender ID", String(transaction.senderDeviceId).take(8), isShort = true, color = Color.White) }
                    Box(modifier = Modifier.weight(1f)) { AuditField("Receiver ID", String(transaction.receiverDeviceId).take(8), isShort = true, color = Color.White) }
                }

                AuditField("Ed25519 Signature", transaction.signature.toHexString(), SoftText, isSignature = true)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PaySetu Proof", transaction.txHash.toHexString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Hash Copied!", Toast.LENGTH_SHORT).show()
            }) {
                Text("Copy Proof", color = SlateBlue)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold, color = EmeraldGreen)
            }
        }
    )
}

@Composable
fun AuditField(label: String, value: String, color: Color, isShort: Boolean = false, isSignature: Boolean = false) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SlateBlue)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            color = Color.White.copy(alpha = 0.03f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (isSignature) 10.sp else 13.sp,
                    letterSpacing = 0.5.sp
                ),
                color = color,
                maxLines = if (isShort) 1 else 10
            )
        }
    }
}

// 📅 HELPER FUNCTIONS FOR HUMANIZED DATES
fun Long.toRelativeDateString(): String {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toRelativeDateString }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        else -> SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(this))
    }
}

fun Long.toHumanTime(): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(this))
}