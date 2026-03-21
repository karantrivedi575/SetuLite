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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val _discoveredReceivers = MutableStateFlow<Map<String, String>>(emptyMap())
    val discoveredReceivers: StateFlow<Map<String, String>> = _discoveredReceivers

    private val _myQrSessionId = MutableStateFlow<String?>(null)
    val myQrSessionId: StateFlow<String?> = _myQrSessionId

    // ==========================================
    // 💡 OFFLINE P2P LOGIC (PHASE 14 - FAST SYNC)
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
                    onSuccess = { txHashHex ->
                        // The Receiver shows Success instantly upon processing the payload.
                        _uiState.value = PaymentUiState.Success(txHashHex)
                    },
                    onFailure = { error ->
                        _uiState.value = PaymentUiState.Failure(error.message ?: "Compromised transaction rejected")
                    }
                )
            }
        }
    }

    fun startTargetedDiscovery(scannedQrCode: String, amountToSend: Long) {
        _uiState.value = PaymentUiState.Processing

        p2pManager.startDiscovering { endpointId, endpointName ->
            Log.d("PaySetu_P2P", "Found Receiver: $endpointName with ID: $endpointId")

            if (endpointName == scannedQrCode) {
                // 🚀 FASTEST DISCOVERY: Stop scanning immediately to free up the radio,
                // then instantly fire the payment.
                p2pManager.stopDiscovery()
                sendOfflinePayment(amountToSend, endpointId)
            }
        }
    }

    fun startScanningForReceivers() {
        p2pManager.stopAll()
        reset()

        _discoveredReceivers.value = emptyMap()
        p2pManager.startDiscovering { endpointId, endpointName ->
            Log.d("PaySetu_P2P", "Found Receiver: $endpointName with ID: $endpointId")
            _discoveredReceivers.update { currentMap ->
                currentMap + (endpointId to endpointName)
            }
        }
    }

    fun stopOfflineMode() {
        p2pManager.stopAll()
        _myQrSessionId.value = null
        reset()
    }

    override fun onCleared() {
        super.onCleared()
        p2pManager.stopAll()
    }

    // ==========================================
    // EXISTING CRYPTO & DB LOGIC
    // ==========================================

    fun sendOfflinePayment(amount: Long, receiverEndpointId: String) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.Processing

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

                // 🚀 NATIVE ACK IMPLEMENTATION
                // The third parameter is the onDeliveryConfirmed callback.
                p2pManager.sendTransaction(receiverEndpointId, payloadData) {
                    Log.d("PaySetu_P2P", "Hardware Delivery Confirmed! Showing Success UI.")

                    // This block executes the EXACT millisecond the hardware guarantees delivery.
                    // This forces the Sender's UI to sync perfectly with the Receiver's UI.
                    viewModelScope.launch {
                        _uiState.value = PaymentUiState.Success(resultHash)
                    }
                }

            } catch (e: Exception) {
                _uiState.value = PaymentUiState.Failure(
                    reason = e.message ?: "Offline transaction failed"
                )
            }
        }
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

    fun reset() {
        _uiState.value = PaymentUiState.Idle
    }
}