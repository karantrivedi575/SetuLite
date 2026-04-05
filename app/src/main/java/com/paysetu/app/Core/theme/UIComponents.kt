// File: UIComponents.kt
package com.paysetu.app.Core.theme

import android.graphics.Paint
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 💎 PROPERTIES SPECIFIC TO UI COMPONENTS (Not in Color.kt)
val DeepSlateGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
val SoftText = Color.White.copy(alpha = 0.7f)
val AccentGold = Color(0xFFFACC15)

// 💎 SHARED MODIFIERS
fun Modifier.glassCard(shape: RoundedCornerShape = RoundedCornerShape(24.dp)) = this
    .clip(shape)
    .background(GlassBackground) // Automatically pulled from Color.kt!
    .border(0.5.dp, GlassBorder, shape) // Automatically pulled from Color.kt!

fun Modifier.neonGlow(color: Color) = this.drawWithCache {
    val radius = 40.dp.toPx()
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.TRANSPARENT
        setShadowLayer(radius, 0f, 0f, color.toArgb())
    }
    onDrawBehind {
        drawContext.canvas.nativeCanvas.drawCircle(center.x, center.y, radius / 2f, paint)
    }
}

// 💎 SHARED COMPONENTS

@Composable
fun PaySetuTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun ReceiptRow(label: String, value: String, isBold: Boolean = false, valueColor: Color = Color.White, valueSize: TextUnit = 14.sp) {
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
            fontSize = valueSize,
            textAlign = TextAlign.End,
            fontFamily = if (label.contains("Hash") || label.contains("ID")) FontFamily.Monospace else FontFamily.SansSerif
        )
    }
}

@Composable
fun ProcessingView(title: String, subtitle: String, onCancel: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp), color = EmeraldGreen, strokeWidth = 4.dp)
        Spacer(modifier = Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, color = SoftText, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp))
        Spacer(modifier = Modifier.height(48.dp))
        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text("Cancel Transfer", color = RoseError)
            }
        }
    }
}

@Composable
fun TransactionReceiptView(
    title: String,
    subtitle: String,
    amountText: String,
    details: @Composable () -> Unit,
    onDone: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Large Success Icon
        Box(
            modifier = Modifier.size(88.dp).glassCard(CircleShape).background(EmeraldGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = "Success", tint = EmeraldGreen, modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        Text(subtitle, color = EmeraldGreen, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(24.dp))

        // Amount Display
        Text(
            text = amountText,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = EmeraldGreen
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Details Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.03f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                details()
            }
        }

        Spacer(modifier = Modifier.weight(1.5f))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Done", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AmountInputPad(
    amountText: String,
    onAmountChange: (String) -> Unit,
    symbol: String,
    hasError: Boolean,
    maxLength: Int,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
    cursorColor: Color = EmeraldGreen
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = symbol,
            fontSize = 32.sp,
            color = if (amountText.isEmpty()) Color.White.copy(alpha = 0.2f) else Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))

        BasicTextField(
            value = amountText,
            onValueChange = { newValue ->
                val filtered = newValue.filter { it.isDigit() }
                if (filtered.length <= maxLength) onAmountChange(filtered)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (hasError) RoseError else Color.White,
                letterSpacing = (-1.5).sp,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(cursorColor),
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .defaultMinSize(minWidth = 50.dp)
                .focusRequester(focusRequester),
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
}