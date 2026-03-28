package com.paysetu.app.ui.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// 💎 SHARED PREMIUM PALETTE
private val DeepSlateGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
private val EmeraldGreen = Color(0xFF10B981)
private val SoftText = Color.White.copy(alpha = 0.6f)

// 💎 Standardized 0.5dp Depth Modifier
private fun Modifier.crispPermissionGlass(shape: RoundedCornerShape = RoundedCornerShape(20.dp)) = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.05f))
    .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)

// 💡 1. CRITICAL HELPER: Unwraps the Compose Context to find the true Android Activity
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun PermissionGate(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // 🛡️ Define the Security Manifest
    val requiredPermissions = remember {
        mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    // State to track if we can proceed
    var allGranted by remember {
        mutableStateOf(requiredPermissions.all { checkPermission(context, it) })
    }

    // 💡 Auto-Refresh State when returning from System Permission Prompt
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allGranted = requiredPermissions.all { checkPermission(context, it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (allGranted) {
        content()
    } else {
        PermissionRequestScreen(
            permissions = requiredPermissions,
            onLaunch = {
                // 🚀 2. THE ULTIMATE FIX: Bypass Compose Launchers completely.
                // We use the raw Activity API and force a 16-bit safe request code (101).
                context.findActivity()?.let { activity ->
                    ActivityCompat.requestPermissions(activity, requiredPermissions, 101)
                }
            }
        )
    }
}

@Composable
private fun PermissionRequestScreen(permissions: Array<String>, onLaunch: () -> Unit) {
    val context = LocalContext.current

    // 💡 LASER SWEEP ANIMATION
    val infiniteTransition = rememberInfiniteTransition(label = "securityScanner")
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = -35f, // Sweep from above the icon
        targetValue = 35f,  // To below the icon
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    // Logic to see which icons to check off
    val bluetoothGranted = permissions.filter { it.contains("BLUETOOTH") }.all { checkPermission(context, it) }
    val locationGranted = permissions.filter { it.contains("LOCATION") }.all { checkPermission(context, it) }
    val cameraGranted = checkPermission(context, Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize().background(DeepSlateGradient)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 🛡️ ENHANCED SCANNING ICON
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // The Shield Icon
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(44.dp)
                )

                // 💡 THE SCANNING LASER LINE
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(2.dp)
                        .offset(y = scanOffset.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, EmeraldGreen, Color.Transparent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Security Configuration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "PaySetu requires localized mesh-networking to process your payments without internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 📋 CHECKLIST SECTION
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PermissionCheckItem(
                    icon = Icons.Default.Bluetooth,
                    title = "Offline Radios",
                    description = "Bluetooth & P2P handshakes",
                    isGranted = bluetoothGranted
                )
                PermissionCheckItem(
                    icon = Icons.Default.LocationOn,
                    title = "Proximity Engine",
                    description = "Secure device-to-device range",
                    isGranted = locationGranted
                )
                PermissionCheckItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Node Scanner",
                    description = "QR peer recognition",
                    isGranted = cameraGranted
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 💎 PREMIUM ACTION BUTTON
            Button(
                onClick = onLaunch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    "INITIALIZE RADIOS",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }

            TextButton(onClick = { /* Security Info */ }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Hardware Encryption Active", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionCheckItem(icon: ImageVector, title: String, description: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().crispPermissionGlass().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (isGranted) EmeraldGreen else Color.White, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(description, color = SoftText, fontSize = 12.sp)
        }

        if (isGranted) {
            Icon(Icons.Default.Check, null, tint = EmeraldGreen)
        }
    }
}

private fun checkPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}