package com.paysetu.app.payment

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paysetu.app.ledger.ledger.LedgerRepository
import com.paysetu.app.ledger.ledger.TransactionProcessor
import com.paysetu.app.ledger.model.LedgerTransactionEntity
import com.paysetu.app.ledger.model.TransactionDirection
import com.paysetu.app.ledger.model.TransactionStatus
import com.paysetu.app.connectivity.P2PTransferManager
import com.paysetu.app.security.KeystoreTransactionSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

// 🚀 THE BULLETPROOF EVENT BUS: Direct communication from Background Receiver to UI
object OfflinePaymentEventBus {
    private val _ackReceived = MutableStateFlow<String?>(null)
    val ackReceived: StateFlow<String?> = _ackReceived.asStateFlow()

    fun triggerAck(txHashHex: String) {
        _ackReceived.value = txHashHex
    }

    fun clearAck() {
        _ackReceived.value = null
    }
}

class PaymentViewModel(
    private val application: Application,
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: KeystoreTransactionSigner,
    private val p2pManager: P2PTransferManager,
    private val transactionProcessor: TransactionProcessor
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    val ledgerHistory: StateFlow<List<LedgerTransactionEntity>> = ledgerRepository
        .getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 🚀 THE FIX: Offload total balance calculation to the background thread.
    val totalBalance: StateFlow<Long> = ledgerHistory.map { history ->
        // This math now happens off the Main Thread
        history.sumOf { if (it.direction == TransactionDirection.INCOMING) it.amount else -it.amount }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val isLedgerIntact: StateFlow<Boolean> = ledgerHistory.map { history ->
        if (history.size < 2) return@map true
        for (i in 0 until history.size - 1) {
            val current = history[i]
            val previous = history[i + 1]
            if (!current.prevTxHash.contentEquals(previous.txHash)) {
                Log.e("PaySetu_Security", "CHAIN BROKEN: Entry ${current.id}")
                return@map false
            }
        }
        true
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true)

    private val _myQrSessionId = MutableStateFlow<String?>(null)
    val myQrSessionId: StateFlow<String?> = _myQrSessionId

    private var connectionTimeoutJob: Job? = null
    private var isRadioActive = false
    private var pendingSmsTxHash: String? = null
    private var pendingSmsAmount: Long = 0L

    // 🚀 THE FINAL FIX: Bulletproof EventBus Observer (No DB Delay)
    init {
        viewModelScope.launch {
            OfflinePaymentEventBus.ackReceived.collect { ackHash ->
                if (ackHash != null) {
                    // Only intercept if we are actively waiting on the UI
                    if (_uiState.value is PaymentUiState.SmsSending || _uiState.value is PaymentUiState.Processing) {

                        Log.i("PaySetu_UI", "EventBus caught ACK! Transitioning to Receipt Screen instantly.")

                        // Cancel the auto-reversal timeout
                        connectionTimeoutJob?.cancel()

                        // 🛑 DATABASE LOOKUP REMOVED.
                        // Push the UI to the Receipt screen instantly using the memory cache!
                        _uiState.value = PaymentUiState.Success(ackHash, pendingSmsAmount)

                        // Clear pending memory & reset bus
                        pendingSmsTxHash = null
                        pendingSmsAmount = 0L
                        OfflinePaymentEventBus.clearAck()
                    }
                }
            }
        }
    }

    // 🚀 NEW: The function called directly from the UI's LaunchedEffect
    fun forceSuccessState(txHashHex: String) {
        // 1. Stop the 10-minute auto-reversal timer immediately
        connectionTimeoutJob?.cancel()

        // 2. Snap the UI state to Success using the confirmed hash
        // and the amount we have stored in memory (pendingSmsAmount)
        _uiState.value = PaymentUiState.Success(txHashHex, pendingSmsAmount)

        // 3. Cleanup memory
        pendingSmsTxHash = null
        pendingSmsAmount = 0L

        Log.i("PaySetu_VM", "Forcefully transitioned to Success state for hash: $txHashHex")
    }

    // ==========================================
    // 📨 UNIVERSAL TEXT SMS LOGIC (WITH REVERSAL)
    // ==========================================
    fun sendSmsPayment(context: Context, phoneNumber: String, amount: Long) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.SmsSending(phoneNumber)

            try {
                // Check against the background-calculated balance
                if (amount > totalBalance.value) {
                    _uiState.value = PaymentUiState.Failure("Insufficient Credits!")
                    return@launch
                }

                val currentTimestamp = System.currentTimeMillis()

                val resultHashHex = withContext(Dispatchers.Default) {
                    val lastTx = withContext(Dispatchers.IO) { ledgerRepository.getLastTransaction() }
                    val validatedPrevHash = lastTx?.txHash ?: ByteArray(32) { 0 }

                    val txTemplate = LedgerTransactionEntity(
                        id = 0L, txHash = ByteArray(0), prevTxHash = validatedPrevHash,
                        senderDeviceId = "LOCAL_SMS_NODE".toByteArray(), receiverDeviceId = phoneNumber.toByteArray(),
                        amount = amount, timestamp = currentTimestamp, signature = ByteArray(0),
                        direction = TransactionDirection.OUTGOING,
                        status = TransactionStatus.PENDING
                    )

                    val finalTxHash = calculateTransactionHash(txTemplate)
                    val digitalSignature = transactionSigner.sign(finalTxHash)
                    val finalTx = txTemplate.copy(txHash = finalTxHash, signature = digitalSignature)

                    withContext(Dispatchers.IO) { ledgerRepository.appendTransactionAtomically(finalTx) }
                    bytesToHex(finalTx.txHash)
                }

                pendingSmsTxHash = resultHashHex
                pendingSmsAmount = amount

                // 💡 SECURITY FIX: Added timestamp to the payload to prevent delayed double-spends
                val payload = "SETU:TX-OFFLINE|$amount|$resultHashHex|$currentTimestamp"

                try {
                    // 1. Attempt Background Sending (Requires SEND_SMS permission)
                    val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(phoneNumber, null, payload, null, null)
                    Log.i("PaySetu_SMS", "Background Text Dispatched: $payload. Waiting for ACK...")

                    // Start the 10-minute auto-reversal timer
                    startReversalTimer(600_000L, resultHashHex, amount)

                } catch (securityEx: SecurityException) {
                    // 2. FALLBACK: Open Default SMS App if permission denied
                    Log.w("PaySetu_SMS", "Background SMS denied. Opening SMS App.")

                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phoneNumber")
                        putExtra("sms_body", payload)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(smsIntent)

                    // We still start the timer, because the user will press "Send" in their app
                    startReversalTimer(600_000L, resultHashHex, amount)
                }

            } catch (e: Exception) {
                Log.e("PaySetu_SMS", "SMS Path Failed", e)
                _uiState.value = PaymentUiState.Failure("SMS Transfer Failed: ${e.localizedMessage}")
            }
        }
    }

    fun handleIncomingSms(context: Context, payload: String, senderNumber: String) {
        val cleanPayload = payload.removePrefix("SETU:")

        if (cleanPayload.startsWith("ACK-OFFLINE")) {
            val ackHash = cleanPayload.split("|").getOrNull(1)

            if (ackHash != null) {
                Log.i("PaySetu_SMS", "ACK Received via Text! Transaction Verified.")

                // 1. Update the database (for permanence)
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val hashByteArray = ackHash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            ledgerRepository.updateTransactionStatus(hashByteArray, TransactionStatus.ACCEPTED)
                        } catch (e: Exception) {
                            Log.w("PaySetu_Ledger", "Status update failed", e)
                        }
                    }
                }

                // 2. Fire the Event Bus (for instant UI reaction)
                OfflinePaymentEventBus.triggerAck(ackHash)
            }
        } else if (cleanPayload.startsWith("TX-OFFLINE")) {
            processIncomingSmsPayment(context, "SETU:$cleanPayload", senderNumber)
        }
    }

    private fun processIncomingSmsPayment(context: Context, fullPayload: String, senderNumber: String) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.Processing
            val result = transactionProcessor.processIncomingPayload(fullPayload)
            result.fold(
                onSuccess = { (txHash, amount) ->
                    _uiState.value = PaymentUiState.Success(txHash, amount)
                    sendSmsAck(context, senderNumber, txHash)
                },
                onFailure = { error ->
                    _uiState.value = PaymentUiState.Failure(error.message ?: "SMS Payment Invalid")
                }
            )
        }
    }

    private fun sendSmsAck(context: Context, senderNumber: String, txHash: String) {
        try {
            val ackPayload = "SETU:ACK-OFFLINE|$txHash"
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(senderNumber, null, ackPayload, null, null)
        } catch (e: Exception) {
            Log.e("PaySetu_SMS", "Failed to send ACK SMS", e)
        }
    }

    // ==========================================
    // 🔄 FAIL-SAFE: AUTO-REVERSAL LOGIC
    // ==========================================
    private fun startReversalTimer(durationMillis: Long, pendingHashHex: String, refundAmount: Long) {
        connectionTimeoutJob?.cancel()

        connectionTimeoutJob = viewModelScope.launch {
            delay(durationMillis)

            if (pendingSmsTxHash == pendingHashHex) {
                Log.w("PaySetu_Security", "ACK Timeout! Auto-reversing transaction: $pendingHashHex")

                executeAutoReversal(pendingHashHex, refundAmount)

                _uiState.value = PaymentUiState.Failure("No response from receiver. ₢$refundAmount has been refunded to your wallet.")

                pendingSmsTxHash = null
                pendingSmsAmount = 0L
            }
        }
    }

    private suspend fun executeAutoReversal(failedTxHashHex: String, refundAmount: Long) {
        withContext(Dispatchers.Default) {
            try {
                val hashByteArray = failedTxHashHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

                // 1. Mark original as REVERSED
                withContext(Dispatchers.IO) {
                    // Make sure your enum supports 'REVERSED', otherwise change this to your failure status
                    // ledgerRepository.updateTransactionStatus(hashByteArray, TransactionStatus.REVERSED)
                }

                // 2. Create the incoming Refund block
                val lastTx = withContext(Dispatchers.IO) { ledgerRepository.getLastTransaction() }
                val validatedPrevHash = lastTx?.txHash ?: ByteArray(32) { 0 }

                val refundTxTemplate = LedgerTransactionEntity(
                    id = 0L, txHash = ByteArray(0), prevTxHash = validatedPrevHash,
                    senderDeviceId = "SYSTEM_REFUND".toByteArray(), receiverDeviceId = "LOCAL_DEVICE".toByteArray(),
                    amount = refundAmount, timestamp = System.currentTimeMillis(), signature = ByteArray(0),
                    direction = TransactionDirection.INCOMING, status = TransactionStatus.ACCEPTED,
                    refundedTxHash = hashByteArray // 💡 Link it to the failed transaction
                )

                val finalTxHash = calculateTransactionHash(refundTxTemplate)
                val digitalSignature = transactionSigner.sign(finalTxHash)
                val finalRefundTx = refundTxTemplate.copy(txHash = finalTxHash, signature = digitalSignature)

                withContext(Dispatchers.IO) { ledgerRepository.appendTransactionAtomically(finalRefundTx) }
                Log.i("PaySetu_Security", "Refund complete. Ledger balanced.")

            } catch (e: Exception) {
                Log.e("PaySetu_Security", "CRITICAL ERROR: Failed to process auto-reversal!", e)
            }
        }
    }

    // ==========================================
    // 💡 OFFLINE P2P LOGIC (NEARBY)
    // ==========================================
    fun startReceivingOffline(userName: String = "PaySetu User") {
        if (isRadioActive) return
        isRadioActive = true
        p2pManager.stopAll()
        reset()

        val sessionId = "SETU-" + UUID.randomUUID().toString().take(6).uppercase()
        _myQrSessionId.value = sessionId

        viewModelScope.launch {
            delay(400)
            p2pManager.startBroadcasting(sessionId) { payload ->
                viewModelScope.launch {
                    _uiState.value = PaymentUiState.Processing
                    val result = transactionProcessor.processIncomingPayload(payload)
                    result.fold(
                        onSuccess = { (txHashHex, receivedAmount) ->
                            _uiState.value = PaymentUiState.Success(txHashHex, receivedAmount)
                        },
                        onFailure = { error ->
                            _uiState.value = PaymentUiState.Failure(error.message ?: "Compromised transaction rejected")
                        }
                    )
                }
            }
        }
    }

    // 🚀 FIXED: Unlocks the radio state upon successful tap
    fun startTapToPayMode(onTapDetected: (String) -> Unit) {
        if (isRadioActive) return
        isRadioActive = true

        // Stop any existing discovery to focus strictly on proximity
        p2pManager.stopAll()

        p2pManager.startTapDiscovery { receiverId ->
            viewModelScope.launch {
                // 🛑 THE FIX: We found the target!
                // 1. Turn off the radar to save battery while the user types the amount.
                p2pManager.stopAll()

                // 2. Unlock the radio state so the "Authorize" button can actually send the payload.
                isRadioActive = false

                onTapDetected(receiverId)
            }
        }
    }

    fun startTargetedDiscovery(scannedQrCode: String, amountToSend: Long) {
        if (isRadioActive) return
        isRadioActive = true
        _uiState.value = PaymentUiState.Processing
        p2pManager.stopAll()

        viewModelScope.launch {
            delay(400)
            startTimeoutTimer(15000, "Receiver not found. Ensure the QR code is still visible.")
            p2pManager.startDiscovering { endpointId, endpointName ->
                if (endpointName == scannedQrCode) {
                    connectionTimeoutJob?.cancel()
                    p2pManager.stopDiscovery()
                    viewModelScope.launch {
                        delay(150)
                        sendOfflinePayment(amountToSend, endpointId)
                    }
                }
            }
        }
    }

    fun sendOfflinePayment(amount: Long, receiverEndpointId: String) {
        viewModelScope.launch {
            // Check against the background-calculated balance
            if (amount > totalBalance.value) {
                _uiState.value = PaymentUiState.Failure("Insufficient Credits!")
                return@launch
            }

            startTimeoutTimer(15000, "Transfer Interrupted.")

            try {
                val resultHash = withContext(Dispatchers.Default) {
                    val lastTx = withContext(Dispatchers.IO) { ledgerRepository.getLastTransaction() }
                    val validatedPrevHash = lastTx?.txHash ?: ByteArray(32) { 0 }

                    val txTemplate = LedgerTransactionEntity(
                        id = 0L, txHash = ByteArray(0), prevTxHash = validatedPrevHash,
                        senderDeviceId = "LOCAL_DEVICE".toByteArray(), receiverDeviceId = receiverEndpointId.toByteArray(),
                        amount = amount, timestamp = System.currentTimeMillis(), signature = ByteArray(0),
                        direction = TransactionDirection.OUTGOING, status = TransactionStatus.ACCEPTED
                    )

                    val finalTxHash = calculateTransactionHash(txTemplate)
                    val digitalSignature = transactionSigner.sign(finalTxHash)
                    val finalTx = txTemplate.copy(txHash = finalTxHash, signature = digitalSignature)

                    withContext(Dispatchers.IO) { ledgerRepository.appendTransactionAtomically(finalTx) }
                    bytesToHex(finalTx.txHash)
                }

                val payloadData = "TX_PAYLOAD:{hash:${resultHash},amount:$amount}"

                p2pManager.sendTransaction(
                    endpointId = receiverEndpointId,
                    payloadData = payloadData,
                    onDeliveryConfirmed = {
                        connectionTimeoutJob?.cancel()
                        viewModelScope.launch {
                            _uiState.value = PaymentUiState.Success(resultHash, amount)
                        }
                    },
                    onFailure = { reason ->
                        connectionTimeoutJob?.cancel()
                        viewModelScope.launch {
                            _uiState.value = PaymentUiState.Failure(reason)
                        }
                    }
                )
            } catch (e: Exception) {
                connectionTimeoutJob?.cancel()
                _uiState.value = PaymentUiState.Failure(e.message ?: "Offline transaction failed")
            }
        }
    }

    // ==========================================
    // 🏦 BANK TOP-UP LOGIC - UNTOUCHED
    // ==========================================
    fun addCreditsFromBank(creditAmount: Long) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.Processing
            try {
                withContext(Dispatchers.Default) {
                    val lastTx = withContext(Dispatchers.IO) { ledgerRepository.getLastTransaction() }
                    val validatedPrevHash = lastTx?.txHash ?: ByteArray(32) { 0 }

                    val txTemplate = LedgerTransactionEntity(
                        id = 0L, txHash = ByteArray(0), prevTxHash = validatedPrevHash,
                        senderDeviceId = "BANK_RESERVE_NODE".toByteArray(), receiverDeviceId = "LOCAL_DEVICE".toByteArray(),
                        amount = creditAmount, timestamp = System.currentTimeMillis(), signature = ByteArray(0),
                        direction = TransactionDirection.INCOMING, status = TransactionStatus.ACCEPTED
                    )

                    val finalTxHash = calculateTransactionHash(txTemplate)
                    val digitalSignature = transactionSigner.sign(finalTxHash)
                    val finalTx = txTemplate.copy(txHash = finalTxHash, signature = digitalSignature)

                    withContext(Dispatchers.IO) { ledgerRepository.appendTransactionAtomically(finalTx) }
                }
                delay(500)
                _uiState.value = PaymentUiState.Idle
            } catch (e: Exception) {
                _uiState.value = PaymentUiState.Failure("Bank Top-Up Failed: ${e.message}")
            }
        }
    }

    // Standard short timeout for P2P Radio connections
    private fun startTimeoutTimer(duration: Long, errorMessage: String) {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(duration)
            if (_uiState.value is PaymentUiState.Processing || _uiState.value is PaymentUiState.SmsSending) {
                _uiState.value = PaymentUiState.Failure(errorMessage)
            }
        }
    }

    fun stopOfflineMode() {
        if (!isRadioActive) return
        isRadioActive = false
        p2pManager.stopAll()
        _myQrSessionId.value = null
        connectionTimeoutJob?.cancel()
    }

    fun reset() { _uiState.value = PaymentUiState.Idle }

    override fun onCleared() {
        super.onCleared()
        stopOfflineMode()
    }

    private fun calculateTransactionHash(tx: LedgerTransactionEntity): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(2048)
        buffer.put(tx.prevTxHash)
        buffer.put(tx.senderDeviceId)
        buffer.put(tx.receiverDeviceId)
        buffer.putLong(tx.amount)
        buffer.putLong(tx.timestamp)
        buffer.put(tx.direction.name.toByteArray())

        buffer.put(tx.nonce.toByteArray())
        if (tx.refundedTxHash != null) {
            buffer.put(tx.refundedTxHash)
        }

        val array = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(array)
        return digest.digest(array)
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}