// File: TransactionItem.kt
package com.paysetu.app.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysetu.app.ledger.model.LedgerTransactionEntity
import com.paysetu.app.ledger.model.TransactionDirection
import java.text.SimpleDateFormat
import java.util.*

// 💎 Unified Elite Palette
private val EmeraldGreen = Color(0xFF10B981)
private val SlateBlue = Color(0xFF94A3B8)
private val DeepNavy = Color(0xFF020617)

// 💎 HIGH-PERFORMANCE CRISP GLASS
fun Modifier.crispRowGlass(shape: RoundedCornerShape = RoundedCornerShape(18.dp)) = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.05f))
    // 💡 0.5dp dark stroke for subtle depth against the deep background
    .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)

@Composable
fun TransactionItem(
    transaction: LedgerTransactionEntity,
    isGroupedMode: Boolean = false // 💡 Tells the component if it's inside the Ledger
) {
    val isOutgoing = transaction.direction == TransactionDirection.OUTGOING

    // 💡 TYPOGRAPHY FOR MONEY: Bold Emerald for incoming, Muted Regular for outgoing
    val amountColor = if (isOutgoing) Color.White.copy(alpha = 0.7f) else EmeraldGreen
    val amountWeight = if (isOutgoing) FontWeight.Medium else FontWeight.Black

    // Subtle tints for the directional icons
    val iconBgColor = if (isOutgoing) Color.White.copy(alpha = 0.06f) else EmeraldGreen.copy(alpha = 0.1f)
    val iconTint = if (isOutgoing) SlateBlue else EmeraldGreen
    val prefix = if (isOutgoing) "-" else "+"

    // 💡 Intelligent Time Formatting (Fixed overload conflict by using private renamed extensions)
    val timeString = if (isGroupedMode) {
        transaction.timestamp.toItemHumanTime() // e.g., "09:46 PM"
    } else {
        "${transaction.timestamp.toItemRelativeDateString()}, ${transaction.timestamp.toItemHumanTime()}" // e.g., "Today, 09:46 PM"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .crispRowGlass() // 💡 Reliable, clean glass look
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🔹 Modern Directional Icon Bubble
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isOutgoing) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.SouthWest,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp) // Slightly smaller for elegance
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 🔹 Transaction Meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isOutgoing) "Paid Out" else "Received", // Cleaner phrasing
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 💡 Tech ID with SlateBlue tint for secondary hierarchy
            Text(
                text = "REF: ${bytesToHex(transaction.txHash).take(8).uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = SlateBlue,
                letterSpacing = 0.5.sp
            )
        }

        // 🔹 Financial Data
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$prefix₢${transaction.amount}",
                fontSize = 18.sp,
                fontWeight = amountWeight, // 💡 Dynamic Weight applied
                color = amountColor,       // 💡 Dynamic Color applied
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = timeString, // 💡 Utilizing the smart string here
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = SlateBlue
            )
        }
    }
}

/**
 * 💡 Helper to render the Cryptographic Hash
 */
private fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it) }

// 📅 HUMANIZED DATE EXTENSIONS
// Fixed: Made private and renamed slightly to prevent external overload conflicts
private fun Long.toItemRelativeDateString(): String {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toItemRelativeDateString }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(this))
    }
}

private fun Long.toItemHumanTime(): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(this))
}