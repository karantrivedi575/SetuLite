// File: SendPaymentScreen.kt
package com.paysetu.app.payment

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.paysetu.app.Core.utils.MiuiPermissionUtils
import com.paysetu.app.payment.qr.QrScannerView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 💎 IMPORT OUR UNIFIED THEME AND COMPONENTS
import com.paysetu.app.Core.theme.*

private enum class SendStep { SCANNER, TAP_TO_PAY, PHONE_ENTRY, AMOUNT_ENTRY }

// 🛡️ NATIVE BIOMETRIC HELPER
private fun promptBiometricAuth(context: Context, amount: String, onSuccess: () -> Unit, onError: () -> Unit) {
    try {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

        val canAuthenticate = biometricManager.canAuthenticate(authenticators)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.w("PaySetu_Security", "Device has no secure lock/biometrics. Bypassing Auth.")
            onSuccess()
            return
        }

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
                if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                    errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ||
                    errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                    errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                    Log.w("PaySetu_Security", "Hardware rejected prompt ($errorCode). Bypassing.")
                    onSuccess()
                } else {
                    Log.e("PaySetu_Security", "Biometric Error: $errString ($errorCode)")
                    onError()
                }
            }
        })

        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        Log.e("PaySetu_Security", "Biometric hardware fault: ${e.message}")
        onSuccess()
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isMiuiAllowed = MiuiPermissionUtils.isBackgroundStartAllowed(context)
                isBatteryOptimized = !MiuiPermissionUtils.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(allFixed) {
        if (allFixed) {
            delay(1500)
            showCard = false
        }
    }

    if (showCard) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).glassCard().padding(16.dp)
        ) {
            Text("Device Optimization Required", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            PermissionRow("Background Pop-ups", isMiuiAllowed) { MiuiPermissionUtils.openMiuiPermissionSettings(context) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
            PermissionRow("Battery Restrictions", !isBatteryOptimized) { MiuiPermissionUtils.requestIgnoreBatteryOptimizations(context) }
        }
    }
}

@Composable
fun PermissionRow(title: String, isGranted: Boolean, onFixClick: () -> Unit) {
    val backgroundColor = if (isGranted) EmeraldGreen.copy(alpha = 0.1f) else Color.Transparent
    val contentColor = if (isGranted) EmeraldGreen else SoftText

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(backgroundColor).padding(8.dp)
    ) {
        Icon(if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (isGranted) EmeraldGreen else RoseError, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isGranted) FontWeight.Bold else FontWeight.Normal, color = contentColor, modifier = Modifier.weight(1f))

        if (!isGranted) {
            Button(onClick = onFixClick, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("Fix", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Active", style = MaterialTheme.typography.labelSmall, color = EmeraldGreen, modifier = Modifier.padding(end = 4.dp))
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
    val totalBalance by viewModel.totalBalance.collectAsState()

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val requiredPermissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val hardwarePermissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    val smsPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
    )

    var currentStep by remember { mutableStateOf(SendStep.SCANNER) }
    var isSmsFlow by remember { mutableStateOf(false) }
    var scannedReceiverId by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }

    var isCameraPrepped by remember { mutableStateOf(false) }
    val amountFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        OfflinePaymentEventBus.ackReceived.collect { ackHash ->
            if (ackHash != null) {
                if (uiState is PaymentUiState.SmsSending || uiState is PaymentUiState.Processing) {
                    viewModel.forceSuccessState(ackHash)
                    OfflinePaymentEventBus.clearAck()
                }
            }
        }
    }

    LaunchedEffect(currentStep) {
        if (currentStep == SendStep.SCANNER) {
            delay(400)
            isCameraPrepped = true
        } else {
            isCameraPrepped = false
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try { cameraProviderFuture.get().unbindAll() } catch (e: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        }

        if (currentStep == SendStep.TAP_TO_PAY) {
            if (hardwarePermissionState.allPermissionsGranted) {
                viewModel.startTapToPayMode { tapId ->
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, 255))
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    scannedReceiverId = tapId
                    isSmsFlow = false
                    currentStep = SendStep.AMOUNT_ENTRY
                }
            } else {
                hardwarePermissionState.launchMultiplePermissionRequest()
                currentStep = SendStep.SCANNER
            }
        }

        if (currentStep == SendStep.AMOUNT_ENTRY) {
            delay(300)
            amountFocusRequester.requestFocus()
        }
    }

    val isSecureDevice = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val colIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (colIndex != -1) {
                            var number = it.getString(colIndex).replace(Regex("[^0-9]"), "")
                            if (number.length > 10) number = number.takeLast(10)
                            phoneInput = number
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try { cameraProviderFuture.get().unbindAll() } catch (e: Exception) {}
            }, ContextCompat.getMainExecutor(context))

            viewModel.stopOfflineMode()
            viewModel.reset()
        }
    }

    val handleBackAction = {
        when (uiState) {
            is PaymentUiState.Processing, is PaymentUiState.SmsSending -> { }
            is PaymentUiState.Success -> onBack()
            is PaymentUiState.Failure -> viewModel.reset()
            is PaymentUiState.Idle -> {
                when (currentStep) {
                    SendStep.AMOUNT_ENTRY -> {
                        viewModel.stopOfflineMode()
                        currentStep = if (isSmsFlow) SendStep.PHONE_ENTRY else SendStep.SCANNER
                        amountText = ""
                    }
                    SendStep.PHONE_ENTRY, SendStep.TAP_TO_PAY -> {
                        viewModel.stopOfflineMode()
                        currentStep = SendStep.SCANNER
                        isSmsFlow = false
                    }
                    SendStep.SCANNER -> onBack()
                }
            }
        }
    }

    BackHandler(enabled = true) { handleBackAction() }

    Box(modifier = Modifier.fillMaxSize().background(DeepSlateGradient)) {

        when (val state = uiState) {

            // 💎 USING OUR UNIFIED RECEIPT VIEW
            is PaymentUiState.Success -> {
                LaunchedEffect(Unit) {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val timings = longArrayOf(0, 50, 100, 50, 150, 200)
                        val amplitudes = intArrayOf(0, 100, 0, 100, 0, 255)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                TransactionReceiptView(
                    title = "Transfer Successful",
                    subtitle = "Funds delivered securely.",
                    amountText = "₢${state.amount}",
                    details = {
                        val timeFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
                        ReceiptRow("Time", timeFormat.format(Date()))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                        ReceiptRow("TX Hash", state.txHash.take(12).uppercase() + "...")
                        Spacer(modifier = Modifier.height(12.dp))
                        ReceiptRow("Method", if (isSmsFlow) "Cellular SMS" else "Offline Node", valueColor = EmeraldGreen)
                    },
                    onDone = { handleBackAction() }
                )
            }

            is PaymentUiState.Processing -> {
                ProcessingView(
                    title = "Securing Connection...",
                    subtitle = "Transferring funds over offline protocol.\nPlease keep devices close.",
                    onCancel = { viewModel.stopOfflineMode() }
                )
            }

            is PaymentUiState.SmsSending -> {
                ProcessingView(
                    title = "Dispatching Secure SMS...",
                    subtitle = "Sending encrypted payload via cellular network.\nPlease wait.",
                    onCancel = null
                )
            }

            is PaymentUiState.Idle, is PaymentUiState.Failure -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // 🔹 UNIFIED TOP BAR
                    PaySetuTopBar(
                        title = when (currentStep) {
                            SendStep.SCANNER -> "Scan PaySetu QR"
                            SendStep.TAP_TO_PAY -> "Tap to Pay"
                            SendStep.PHONE_ENTRY -> "Send via SMS"
                            SendStep.AMOUNT_ENTRY -> "Send Credits"
                        },
                        onBack = { handleBackAction() }
                    )

                    XiaomiGuard(context)

                    when (currentStep) {
                        SendStep.SCANNER -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                                if (cameraPermissionState.status.isGranted) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f).glassCard(RoundedCornerShape(24.dp))) {
                                        if (isCameraPrepped) {
                                            QrScannerView(
                                                onCodeScanned = { scannedCode ->
                                                    if (currentStep == SendStep.SCANNER) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        scannedReceiverId = scannedCode
                                                        isSmsFlow = false
                                                        currentStep = SendStep.AMOUNT_ENTRY
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(color = EmeraldGreen, strokeWidth = 2.dp)
                                            }
                                        }

                                        val infiniteTransition = rememberInfiniteTransition(label = "scanner")
                                        val laserProgress by infiniteTransition.animateFloat(
                                            initialValue = 0.1f, targetValue = 0.9f,
                                            animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                            label = "laser"
                                        )

                                        Box(modifier = Modifier.size(240.dp).align(Alignment.Center).border(2.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                            .drawWithContent {
                                                drawContent()
                                                val yPos = size.height * laserProgress
                                                drawLine(color = EmeraldGreen, start = Offset(15f, yPos), end = Offset(size.width - 15f, yPos), strokeWidth = 4f)
                                            }
                                        )
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxWidth().weight(1f).glassCard(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.QrCodeScanner, null, tint = SoftText, modifier = Modifier.size(64.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Camera access is required\nto scan the receiver's QR code.", textAlign = TextAlign.Center, color = SoftText)
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(onClick = { cameraPermissionState.launchPermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                                            Text("Enable Camera", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { currentStep = SendStep.TAP_TO_PAY }.padding(12.dp)) {
                                        Box(modifier = Modifier.size(56.dp).glassCard(CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Nfc, contentDescription = null, tint = EmeraldGreen)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Tap to Pay", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { isSmsFlow = true; currentStep = SendStep.PHONE_ENTRY }.padding(12.dp)) {
                                        Box(modifier = Modifier.size(56.dp).glassCard(CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Message, contentDescription = null, tint = EmeraldGreen)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("SMS Transfer", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        SendStep.TAP_TO_PAY -> {
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.weight(1f))

                                val infiniteTransition = rememberInfiniteTransition(label = "radar")
                                val rippleAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.5f, targetValue = 0f,
                                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "alpha"
                                )
                                val rippleScale by infiniteTransition.animateFloat(
                                    initialValue = 1f, targetValue = 2.5f,
                                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "scale"
                                )

                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .border(2.dp, EmeraldGreen.copy(alpha = rippleAlpha), CircleShape)
                                            .background(EmeraldGreen.copy(alpha = rippleAlpha * 0.2f))
                                            .drawWithContent {
                                                drawContent()
                                                drawCircle(
                                                    color = EmeraldGreen.copy(alpha = rippleAlpha),
                                                    radius = size.width / 2 * rippleScale,
                                                    style = Stroke(width = 4f)
                                                )
                                            }
                                    )
                                    Box(
                                        modifier = Modifier.size(100.dp).glassCard(CircleShape).background(EmeraldGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Nfc, contentDescription = "Tap", tint = EmeraldGreen, modifier = Modifier.size(48.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))
                                Text("Hold near Receiver's Phone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Wait for the vibration...", color = SlateBlue, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                                Spacer(modifier = Modifier.height(48.dp))
                            }
                        }

                        SendStep.PHONE_ENTRY -> {
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(32.dp))
                                Box(modifier = Modifier.size(72.dp).background(EmeraldGreen.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PhoneAndroid, null, tint = EmeraldGreen, modifier = Modifier.size(36.dp))
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Receiver's Phone Number", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Enter number or pick from contacts", color = SlateBlue, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(48.dp))

                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { newValue ->
                                        val digitsOnly = newValue.filter { it.isDigit() }
                                        if (digitsOnly.length <= 10) phoneInput = digitsOnly
                                    },
                                    textStyle = TextStyle(color = Color.White, fontSize = 24.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldGreen, unfocusedBorderColor = SlateBlue, cursorColor = EmeraldGreen),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    placeholder = { Text("00000 00000", color = SoftText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                    visualTransformation = PhoneVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                            contactPickerLauncher.launch(intent)
                                        }) {
                                            Icon(Icons.Default.AccountBox, contentDescription = "Contacts", tint = EmeraldGreen)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        if (phoneInput.length == 10) {
                                            scannedReceiverId = phoneInput
                                            currentStep = SendStep.AMOUNT_ENTRY
                                        }
                                    },
                                    enabled = phoneInput.length == 10,
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black, disabledContainerColor = Color.White.copy(0.1f))
                                ) {
                                    Text("NEXT", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        SendStep.AMOUNT_ENTRY -> {
                            val amountLong = amountText.toLongOrNull() ?: 0L
                            val isOverdraft = amountLong > totalBalance
                            val isZeroInvalid = amountText.isNotEmpty() && amountLong <= 0L
                            val hasError = isOverdraft || isZeroInvalid
                            val canAuthorize = amountText.isNotEmpty() && !hasError

                            val animatedBgColor = animateColorAsState(targetValue = if (canAuthorize) EmeraldGreen else Color.White.copy(alpha = 0.05f), animationSpec = tween(400), label = "bg").value
                            val animatedTextColor = animateColorAsState(targetValue = if (canAuthorize) Color(0xFF020617) else Color.White.copy(alpha = 0.3f), animationSpec = tween(400), label = "txt").value
                            val animatedIconScale = animateFloatAsState(targetValue = if (canAuthorize) 1.2f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "icon").value

                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp)).padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(42.dp).background(EmeraldGreen.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(if(isSmsFlow) Icons.Default.PhoneAndroid else Icons.Default.Person, null, tint = EmeraldGreen)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(if(isSmsFlow) "Paying via Secure SMS" else "Paying Offline Node", color = SlateBlue, fontSize = 12.sp)
                                            Text(scannedReceiverId, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(32.dp))
                                Text("Enter Amount", color = SlateBlue, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // 💰 UNIFIED AMOUNT INPUT
                                AmountInputPad(
                                    amountText = amountText,
                                    onAmountChange = { amountText = it },
                                    symbol = "₢",
                                    hasError = hasError,
                                    maxLength = 6,
                                    focusRequester = amountFocusRequester,
                                    onDone = { focusManager.clearFocus() }
                                )

                                val helperText = when {
                                    isOverdraft -> "Insufficient credits. Available: ₢$totalBalance"
                                    isZeroInvalid -> "Amount must be greater than zero"
                                    else -> "Available Credits: ₢$totalBalance"
                                }
                                Text(text = helperText, color = if (hasError) RoseError else SlateBlue, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

                                val failureState = state as? PaymentUiState.Failure
                                if (failureState != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RoseError.copy(alpha = 0.1f)).padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, null, tint = RoseError, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(failureState.reason, color = RoseError, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        if (canAuthorize) {
                                            if (isSmsFlow && !smsPermissionState.allPermissionsGranted) {
                                                smsPermissionState.launchMultiplePermissionRequest()
                                            } else {
                                                promptBiometricAuth(
                                                    context = context, amount = amountText,
                                                    onSuccess = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (isSmsFlow) viewModel.sendSmsPayment(context, scannedReceiverId, amountLong)
                                                        else viewModel.startTargetedDiscovery(scannedReceiverId, amountLong)
                                                    },
                                                    onError = { }
                                                )
                                            }
                                        }
                                    },
                                    enabled = canAuthorize,
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = animatedBgColor, contentColor = animatedTextColor, disabledContainerColor = Color.White.copy(alpha = 0.05f), disabledContentColor = Color.White.copy(alpha = 0.3f))
                                ) {
                                    Icon(imageVector = if (isSecureDevice) Icons.Default.Fingerprint else Icons.Default.Shield, contentDescription = null, modifier = Modifier.size((20 * animatedIconScale).dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isOverdraft) "NO FUNDS" else "AUTHORIZE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 10) text.text.substring(0..9) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 4) out += " "
        }

        val numberOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 10) return offset + 1
                return 11
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 5) return offset
                if (offset <= 11) return offset - 1
                return 10
            }
        }
        return TransformedText(AnnotatedString(out), numberOffsetTranslator)
    }
}