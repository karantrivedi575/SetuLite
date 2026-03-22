package com.paysetu.app.ui.payment

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
    val uiState: StateFlow<PaymentUiState> = _uiState

    val ledgerHistory: StateFlow<List<LedgerTransactionEntity>> = ledgerRepository
        .getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _myQrSessionId = MutableStateFlow<String?>(null)
    val myQrSessionId: StateFlow<String?> = _myQrSessionId

    private var connectionTimeoutJob: Job? = null

    // ==========================================
    // 💡 OFFLINE P2P LOGIC (STRESS-TESTED)
    // ==========================================

    fun startReceivingOffline(userName: String = "PaySetu User") {
        p2pManager.stopAll()
        reset()

        val sessionId = "SETU-" + UUID.randomUUID().toString().take(6).uppercase()
        _myQrSessionId.value = sessionId

        p2pManager.startBroadcasting(sessionId) { payload ->
            Log.d("PaySetu_P2P", "Received Payload: $payload")

            viewModelScope.launch {
                _uiState.value = PaymentUiState.Processing

                val result = transactionProcessor.processIncomingPayload(payload)

                result.fold(
                    onSuccess = { (txHashHex, receivedAmount) ->
                        _uiState.value = PaymentUiState.Success(txHashHex, receivedAmount)

                        // 💡 GRACEFUL DISCONNECT: Wait half a second before killing radios
                        delay(500)
                        p2pManager.stopAll()
                    },
                    onFailure = { error ->
                        _uiState.value = PaymentUiState.Failure(error.message ?: "Compromised transaction rejected")
                        delay(500)
                        p2pManager.stopAll()
                    }
                )
            }
        }
    }

    fun startTargetedDiscovery(scannedQrCode: String, amountToSend: Long) {
        _uiState.value = PaymentUiState.Processing

        // 💡 FORCE CLEANUP: Tell the system to drop ALL old connections/sockets
        p2pManager.stopAll()

        viewModelScope.launch {
            // 💡 THE HARDWARE BREATH: Wait 500ms for the Wi-Fi/Bluetooth chips to actually flush
            // their buffers and close the old sockets.
            delay(500)

            // 💡 NOW start the timeout timer, because discovery is actually starting
            startTimeoutTimer(15000, "Receiver not found. Ensure the QR code is still visible.")

            p2pManager.startDiscovering { endpointId, endpointName ->
                Log.d("PaySetu_P2P", "Found Receiver: $endpointName with ID: $endpointId")

                if (endpointName == scannedQrCode) {
                    connectionTimeoutJob?.cancel()

                    p2pManager.stopDiscovery()

                    viewModelScope.launch {
                        delay(150) // Hardware breath
                        sendOfflinePayment(amountToSend, endpointId)
                    }
                }
            }
        }
    }

    fun sendOfflinePayment(amount: Long, receiverEndpointId: String) {
        viewModelScope.launch {
            // 💡 RELIABILITY FIX: Increased to 15 seconds to allow older hardware to connect
            startTimeoutTimer(15000, "Transfer Interrupted. Check connection and try again.")

            try {
                val resultHash = withContext(Dispatchers.Default) {
                    val lastTx = withContext(Dispatchers.IO) {
                        ledgerRepository.getLastTransaction()
                    }

                    val validatedPrevHash = lastTx?.txHash ?: ByteArray(32) { 0 }
                    val timestamp = System.currentTimeMillis()

                    val txTemplate = LedgerTransactionEntity(
                        id = 0L,
                        txHash = ByteArray(0),
                        prevTxHash = validatedPrevHash,
                        senderDeviceId = "LOCAL_DEVICE".toByteArray(),
                        receiverDeviceId = receiverEndpointId.toByteArray(),
                        amount = amount,
                        timestamp = timestamp,
                        signature = ByteArray(0),
                        direction = TransactionDirection.OUTGOING,
                        status = TransactionStatus.ACCEPTED
                    )

                    val finalTxHash = calculateTransactionHash(txTemplate)
                    val digitalSignature = transactionSigner.sign(finalTxHash)

                    val finalTx = txTemplate.copy(
                        txHash = finalTxHash,
                        signature = digitalSignature
                    )

                    withContext(Dispatchers.IO) {
                        ledgerRepository.appendTransactionAtomically(finalTx)
                    }
                    bytesToHex(finalTx.txHash)
                }

                val payloadData = "TX_PAYLOAD:{hash:${resultHash},amount:$amount}"

                p2pManager.sendTransaction(receiverEndpointId, payloadData) {
                    Log.d("PaySetu_P2P", "Hardware Delivery Confirmed!")

                    connectionTimeoutJob?.cancel()

                    viewModelScope.launch {
                        _uiState.value = PaymentUiState.Success(resultHash, amount)

                        // 💡 GRACEFUL DISCONNECT: Prevents the "SOCKET_CLOSED" error
                        delay(800)
                        p2pManager.stopAll()
                    }
                }

            } catch (e: Exception) {
                connectionTimeoutJob?.cancel()
                _uiState.value = PaymentUiState.Failure(
                    reason = e.message ?: "Offline transaction failed"
                )
                delay(500)
                p2pManager.stopAll()
            }
        }
    }

    private fun startTimeoutTimer(duration: Long, errorMessage: String) {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(duration)
            if (_uiState.value is PaymentUiState.Processing) {
                Log.e("PaySetu_P2P", "Failsafe Triggered: $errorMessage")
                p2pManager.stopAll()
                _uiState.value = PaymentUiState.Failure(errorMessage)
            }
        }
    }

    fun stopOfflineMode() {
        p2pManager.stopAll()
        _myQrSessionId.value = null
        connectionTimeoutJob?.cancel()
        reset()
    }

    fun reset() {
        _uiState.value = PaymentUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        p2pManager.stopAll()
        connectionTimeoutJob?.cancel()
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

        return digest.digest(buffer.array().take(buffer.position()).toByteArray())
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}