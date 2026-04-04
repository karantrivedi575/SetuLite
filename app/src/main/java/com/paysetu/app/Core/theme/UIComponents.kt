// File: UIComponents.kt
package com.paysetu.app.Core.theme

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 💎 CENTRALIZED BACKGROUNDS & COLORS
val DeepSlateGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
val SoftText = Color.White.copy(alpha = 0.7f)

// 💎 SHARED MODIFIERS
fun Modifier.glassCard(shape: RoundedCornerShape = RoundedCornerShape(24.dp)) = this
    .clip(shape)
    .background(GlassBackground)
    .border(0.5.dp, GlassBorder, shape)

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