package com.paysetu.app.ui.payment

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.ui.components.CustomNumpad
import com.paysetu.app.ui.payment.utils.MiuiPermissionUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 💎 PREMIUM FINTECH PALETTE
private val DeepSlateGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
private val EmeraldGreen = Color(0xFF10B981)
private val RoseError = Color(0xFFF43F5E)
private val SlateBlue = Color(0xFF94A3B8)
private val SoftText = Color.White.copy(alpha = 0.7f)

// 💎 GLASSMORPHISM UTILITY
private fun Modifier.glassCard(shape: RoundedCornerShape = RoundedCornerShape(16.dp)) = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.05f))
    .border(1.dp, Color.White.copy(alpha = 0.1f), shape)

// 💡 STEP STATE MACHINE
private enum class SendStep { SCANNER, AMOUNT_ENTRY }

// 🛡️ NATIVE BIOMETRIC HELPER (WITH BULLETPROOF DEVICE B BYPASS)
private fun promptBiometricAuth(context: Context, amount: String, onSuccess: () -> Unit, onError: () -> Unit) {
    try {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

        // 🔍 1. Capability Check: Can this device authenticate?
        val canAuthenticate = biometricManager.canAuthenticate(authenticators)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // 💡 Device B Logic: No hardware or no PIN set. Bypass instantly.
            Log.w("PaySetu_Security", "Device has no secure lock/biometrics. Bypassing Auth.")
            onSuccess()
            return
        }

        // 🛡️ 2. Device A Logic: Enforce high-security prompt
        val activity = context as? FragmentActivity ?: run {
            Log.e("PaySetu_Security", "Context is not FragmentActivity. Bypassing biometrics.")
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authorize Transfer")
            .setSubtitle("Authenticate to securely send ₢$amount")
            .setAllowedAuthenticators(authenticators)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // 💡 Extra Guard for Device B: If the OS lied about capability, catch it here
                if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                    errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ||
                    errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                    errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                    Log.w("PaySetu_Security", "Hardware rejected prompt ($errorCode). Bypassing.")
                    onSuccess()
                } else {
                    Log.e("PaySetu_Security", "Biometric Error: $errString ($errorCode)")
                    onError() // e.g., user canceled deliberately
                }
            }
        })

        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        Log.e("PaySetu_Security", "Biometric hardware fault: ${e.message}")
        onSuccess() // 💡 Safety Fallback: Do not trap the user
    }
}

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
                coroutineScope.launch {
                    repeat(3) {
                        isMiuiAllowed = MiuiPermissionUtils.isBackgroundStartAllowed(context)
                        isBatteryOptimized = !MiuiPermissionUtils.isBatteryOptimizationIgnored(context)

                        if (isMiuiAllowed && !isBatteryOptimized) return@launch
                        delay(500)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(allFixed) {
        if (allFixed) {
            delay(2000)
            showCard = false
        }
    }

    if (showCard) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .glassCard()
                .padding(16.dp)
        ) {
            Text(
                text = "Device Optimization Required",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionRow(
                title = "Background Pop-ups",
                isGranted = isMiuiAllowed,
                onFixClick = { MiuiPermissionUtils.openMiuiPermissionSettings(context) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            PermissionRow(
                title = "Battery Restrictions",
                isGranted = !isBatteryOptimized,
                onFixClick = { MiuiPermissionUtils.requestIgnoreBatteryOptimizations(context) }
            )
        }
    }
}

@Composable
fun PermissionRow(title: String, isGranted: Boolean, onFixClick: () -> Unit) {
    val backgroundColor = if (isGranted) EmeraldGreen.copy(alpha = 0.1f) else Color.Transparent
    val contentColor = if (isGranted) EmeraldGreen else SoftText

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
            tint = if (isGranted) EmeraldGreen else RoseError,
            modifier = Modifier.size(20.dp)
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
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Fix", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                "Active",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldGreen,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SendPaymentScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Ledger Integrity & Balance
    val history by viewModel.ledgerHistory.collectAsState()
    val totalBalance = remember(history) {
        history.sumOf { if (it.direction == TransactionDirection.INCOMING) it.amount else -it.amount }
    }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // State Machine Control
    var currentStep by remember { mutableStateOf(SendStep.SCANNER) }
    var scannedReceiverId by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }

    // 💡 Check Hardware Capabilities for dynamic icon
    val isSecureDevice = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("PaySetu_UI", "Leaving Send Screen. Stopping all radios.")
            viewModel.stopOfflineMode()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepSlateGradient)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 🔹 Dynamic Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        // Prevent back navigation during processing/success
                        if (uiState is PaymentUiState.Processing || uiState is PaymentUiState.Success) return@IconButton

                        if (currentStep == SendStep.AMOUNT_ENTRY) {
                            currentStep = SendStep.SCANNER // Go back to scanner
                            amountText = ""
                        } else {
                            onBack() // Exit screen
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (currentStep == SendStep.SCANNER) "Scan PaySetu QR" else "Send Credits",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Xiaomi Optimization Check
            XiaomiGuard(context)

            // 🔄 Step State Machine
            Crossfade(targetState = currentStep, label = "send_flow") { step ->
                when (step) {
                    SendStep.SCANNER -> {
                        if (cameraPermissionState.status.isGranted) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.8f) // Leaves room at bottom
                                    .glassCard(RoundedCornerShape(24.dp))
                            ) {
                                QrScannerView(
                                    onCodeScanned = { scannedCode ->
                                        if (currentStep == SendStep.SCANNER) { // Prevent double-triggers
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Log.d("PaySetu_P2P", "QR Scanned: $scannedCode")
                                            scannedReceiverId = scannedCode
                                            currentStep = SendStep.AMOUNT_ENTRY
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                                )

                                // 💡 Sweeping Laser Overlay
                                val infiniteTransition = rememberInfiniteTransition(label = "scanner")
                                val laserOffset by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 200f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(2000, easing = LinearOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "laser"
                                )

                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .border(2.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .offset(y = laserOffset.dp)
                                                .background(EmeraldGreen)
                                        )
                                    }
                                }

                                // Hint Chip
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Align QR within frame", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        } else {
                            // Camera Permission Denied State
                            Column(
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).glassCard(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = SoftText, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Camera access is required\nto scan the receiver's QR code.",
                                    textAlign = TextAlign.Center,
                                    color = SoftText
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { cameraPermissionState.launchPermissionRequest() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                ) {
                                    Text("Enable Camera", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    SendStep.AMOUNT_ENTRY -> {
                        when (val state = uiState) {
                            is PaymentUiState.Idle, is PaymentUiState.Failure -> {

                                val amountLong = amountText.toLongOrNull() ?: 0L
                                val isOverdraft = amountLong > totalBalance
                                val isZeroInvalid = amountText.isNotEmpty() && amountLong <= 0L
                                val hasError = isOverdraft || isZeroInvalid
                                val canAuthorize = amountText.isNotEmpty() && !hasError

                                // 💡 DYNAMIC COLOR ANIMATIONS
                                val animatedBgColor by animateColorAsState(
                                    targetValue = if (canAuthorize) EmeraldGreen else Color.White.copy(alpha = 0.05f),
                                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                    label = "btnBg"
                                )
                                val animatedTextColor by animateColorAsState(
                                    targetValue = if (canAuthorize) Color(0xFF020617) else Color.White.copy(alpha = 0.3f),
                                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                    label = "btnText"
                                )
                                val animatedIconScale by animateFloatAsState(
                                    targetValue = if (canAuthorize) 1.2f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "btnIcon"
                                )

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // 👤 Receiver Identification Card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .glassCard(RoundedCornerShape(16.dp))
                                            .padding(16.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(42.dp).background(EmeraldGreen.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Person, null, tint = EmeraldGreen)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Paying Offline Node", color = SlateBlue, fontSize = 12.sp)
                                                Text(scannedReceiverId, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(32.dp))

                                    // 💰 Dynamic Amount Display
                                    Text("Enter Amount", color = SlateBlue, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("₢", fontSize = 32.sp, color = if(amountText.isEmpty()) Color.White.copy(alpha = 0.2f) else Color.White, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = amountText.ifEmpty { "0" },
                                            fontSize = 56.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (hasError) RoseError else Color.White,
                                            letterSpacing = (-2).sp
                                        )
                                    }

                                    // Validation Messaging
                                    val helperText = when {
                                        isOverdraft -> "Insufficient credits. Available: ₢$totalBalance"
                                        isZeroInvalid -> "Amount must be greater than zero"
                                        else -> "Available Credits: ₢$totalBalance"
                                    }
                                    Text(
                                        text = helperText,
                                        color = if (hasError) RoseError else SlateBlue,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )

                                    if (state is PaymentUiState.Failure) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RoseError.copy(alpha = 0.1f)).padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.ErrorOutline, null, tint = RoseError, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(state.reason, color = RoseError, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // ⌨️ Custom Numpad Integration
                                    CustomNumpad(
                                        onNumberClick = { digit ->
                                            if (amountText.length < 6) amountText += digit.toString()
                                        },
                                        onBackspaceClick = {
                                            if (amountText.isNotEmpty()) amountText = amountText.dropLast(1)
                                        },
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    // 🚀 BIOMETRIC AUTHORIZE BUTTON
                                    Button(
                                        onClick = {
                                            if (canAuthorize) {
                                                promptBiometricAuth(
                                                    context = context,
                                                    amount = amountText,
                                                    onSuccess = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.startTargetedDiscovery(scannedReceiverId, amountLong)
                                                    },
                                                    onError = {
                                                        // Handled silently
                                                    }
                                                )
                                            }
                                        },
                                        enabled = canAuthorize, // Allow click intercept based on inputs
                                        modifier = Modifier.fillMaxWidth().height(60.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = animatedBgColor,
                                            contentColor = animatedTextColor,
                                            disabledContainerColor = Color.White.copy(alpha = 0.05f),
                                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        // 💡 Dynamic Icon: Shield for unsecured, Fingerprint for secured
                                        Icon(
                                            imageVector = if (isSecureDevice) Icons.Default.Fingerprint else Icons.Default.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size((20 * animatedIconScale).dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isOverdraft) "NO FUNDS" else "AUTHORIZE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
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
                                        color = EmeraldGreen,
                                        strokeWidth = 4.dp
                                    )
                                    Spacer(modifier = Modifier.height(32.dp))
                                    Text("Securing Connection...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        "Transferring funds over offline protocol.\nPlease keep devices close.",
                                        color = SoftText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp)
                                    )

                                    Spacer(modifier = Modifier.height(48.dp))
                                    TextButton(onClick = { viewModel.stopOfflineMode() }) {
                                        Text("Cancel Transfer", color = RoseError)
                                    }
                                }
                            }

                            is PaymentUiState.Success -> {
                                // 💡 CUSTOM SUCCESS HAPTIC SEQUENCE (Heartbeat-Thud)
                                LaunchedEffect(Unit) {
                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        // Timing: delay, vibe, delay, vibe, delay, heavy_vibe
                                        val timings = longArrayOf(0, 50, 100, 50, 150, 200)
                                        val amplitudes = intArrayOf(0, 100, 0, 100, 0, 255)
                                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
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
                                    Text("Transfer Successful", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                    Text("Funds delivered securely.", color = EmeraldGreen, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(40.dp))

                                    Column(
                                        modifier = Modifier.fillMaxWidth().glassCard().padding(24.dp)
                                    ) {
                                        ReceiptRow("Amount Sent", "₢$amountText", isBold = true, valueColor = EmeraldGreen)

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 16.dp),
                                            color = Color.White.copy(alpha = 0.1f)
                                        )

                                        val timeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                        ReceiptRow("Time", timeFormat.format(Date()))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ReceiptRow("TX Hash", state.txHash.take(12).uppercase() + "...")
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ReceiptRow("Status", "Offline Verified", valueColor = EmeraldGreen)
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    Button(
                                        onClick = {
                                            viewModel.reset()
                                            onBack()
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                    ) {
                                        Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 💡 Simple Helper for the Success Receipt
@Composable
private fun ReceiptRow(label: String, value: String, isBold: Boolean = false, valueColor: Color = Color.White) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = SoftText, fontSize = if (isBold) 16.sp else 14.sp)
        Text(
            text = value,
            color = valueColor,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.Medium,
            fontSize = if (isBold) 18.sp else 14.sp
        )
    }
}