package com.paysetu.app.ui.payment

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.TransactionProcessor
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.data.ledger.entity.TransactionStatus
import com.paysetu.app.data.p2p.P2PTransferManager
import com.paysetu.app.security.signing.KeystoreTransactionSigner
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

class PaymentViewModel(
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: KeystoreTransactionSigner,
    private val p2pManager: P2PTransferManager,
    private val transactionProcessor: TransactionProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    val ledgerHistory: StateFlow<List<LedgerTransactionEntity>> = ledgerRepository
        .getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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

    // ==========================================
    // 📨 PHASE 17: BINARY SMS LOGIC
    // ==========================================
    fun sendSmsPayment(context: Context, phoneNumber: String, amount: Long) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.SmsSending(phoneNumber)

            try {
                val currentBalance = ledgerHistory.value.sumOf {
                    if (it.direction == TransactionDirection.INCOMING) it.amount else -it.amount
                }

                if (amount > currentBalance) {
                    _uiState.value = PaymentUiState.Failure("Insufficient Credits!")
                    return@launch
                }

                val resultHashHex = withContext(Dispatchers.Default) {
                    val lastTx = withContext(Dispatchers.IO) { ledgerRepository.getLastTransaction() }
                    val validatedPrevHash = lastTx?.txHash ?: ByteArray(32) { 0 }

                    val txTemplate = LedgerTransactionEntity(
                        id = 0L, txHash = ByteArray(0), prevTxHash = validatedPrevHash,
                        senderDeviceId = "LOCAL_SMS_NODE".toByteArray(), receiverDeviceId = phoneNumber.toByteArray(),
                        amount = amount, timestamp = System.currentTimeMillis(), signature = ByteArray(0),
                        direction = TransactionDirection.OUTGOING, status = TransactionStatus.ACCEPTED
                    )

                    val finalTxHash = calculateTransactionHash(txTemplate)
                    val digitalSignature = transactionSigner.sign(finalTxHash)
                    val finalTx = txTemplate.copy(txHash = finalTxHash, signature = digitalSignature)

                    withContext(Dispatchers.IO) { ledgerRepository.appendTransactionAtomically(finalTx) }
                    bytesToHex(finalTx.txHash)
                }

                val payload = "SETU-TX-OFFLINE|$amount|$resultHashHex"
                val data = payload.toByteArray(Charsets.UTF_8)
                val port: Short = 8901

                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                smsManager.sendDataMessage(phoneNumber, null, port, data, null, null)
                Log.i("PaySetu_SMS", "Binary Transaction Dispatched: $payload")
                delay(1000)
                _uiState.value = PaymentUiState.Success(resultHashHex, amount)

            } catch (e: Exception) {
                Log.e("PaySetu_SMS", "SMS Path Failed", e)
                _uiState.value = PaymentUiState.Failure("SMS Transfer Failed: ${e.localizedMessage}")
            }
        }
    }

    fun processIncomingSms(payload: String) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.Processing
            val result = transactionProcessor.processIncomingPayload(payload)
            result.fold(
                onSuccess = { (txHash, amount) -> _uiState.value = PaymentUiState.Success(txHash, amount) },
                onFailure = { error -> _uiState.value = PaymentUiState.Failure(error.message ?: "SMS Payment Invalid") }
            )
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

        // 🛡️ THE EXORCISM: Add a 400ms debounce to prevent SOCKET_CLOSED hardware crash
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

    fun startTargetedDiscovery(scannedQrCode: String, amountToSend: Long) {
        if (isRadioActive) return
        isRadioActive = true
        _uiState.value = PaymentUiState.Processing
        p2pManager.stopAll()

        viewModelScope.launch {
            // 🛡️ THE EXORCISM: Standardized 400ms Hardware Cooldown
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
            val currentBalance = ledgerHistory.value.sumOf {
                if (it.direction == TransactionDirection.INCOMING) it.amount else -it.amount
            }

            if (amount > currentBalance) {
                _uiState.value = PaymentUiState.Failure("Insufficient Credits! Your balance is ₢$currentBalance")
                return@launch
            }

            startTimeoutTimer(15000, "Transfer Interrupted. Check connection and try again.")

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
    // 🏦 BANK TOP-UP LOGIC
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

    // ==========================================
    // UTILITIES & LIFECYCLE
    // ==========================================
    private fun startTimeoutTimer(duration: Long, errorMessage: String) {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(duration)
            if (_uiState.value is PaymentUiState.Processing) {
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
        isRadioActive = true // Force bypass the check
        stopOfflineMode()
    }

    private fun calculateTransactionHash(tx: LedgerTransactionEntity): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(1024)
        buffer.put(tx.prevTxHash)
        buffer.put(tx.senderDeviceId)
        buffer.put(tx.receiverDeviceId)
        buffer.putLong(tx.amount)
        buffer.putLong(tx.timestamp)
        buffer.put(tx.direction.name.toByteArray())
        val array = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(array)
        return digest.digest(array)
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}