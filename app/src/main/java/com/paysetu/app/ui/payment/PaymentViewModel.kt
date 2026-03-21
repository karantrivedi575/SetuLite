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

class PaymentViewModel(
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: KeystoreTransactionSigner,
    private val p2pManager: P2PTransferManager,
    private val transactionProcessor: TransactionProcessor
) : ViewModel() {

    // 1. The State of the Current Transaction Action
    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState

    // 2. Streaming the Ledger History
    val ledgerHistory: StateFlow<List<LedgerTransactionEntity>> = ledgerRepository
        .getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 3. Holds a map of EndpointID -> EndpointName (Device Name)
    private val _discoveredReceivers = MutableStateFlow<Map<String, String>>(emptyMap())
    val discoveredReceivers: StateFlow<Map<String, String>> = _discoveredReceivers

    // ==========================================
    // 💡 OFFLINE P2P LOGIC (PHASE 12 & 13)
    // ==========================================

    /**
     * Called by the "Receive" UI. Opens the device to discovery.
     */
    fun startReceivingOffline(userName: String = "PaySetu User") {
        // 💡 FIX 2: Kill lingering sender sockets so we don't catch our own broadcast!
        p2pManager.stopAll()

        // 💡 FIX 3: Wake the UI up from any previous "Success" or "Failure" states
        reset()

        p2pManager.startBroadcasting(userName) { payload ->
            Log.d("PaySetu_P2P", "Received Payload: $payload")

            // THE CATCH AND VERIFY
            viewModelScope.launch {
                // Tell the UI we are verifying the math
                _uiState.value = PaymentUiState.Processing

                // Push to the processor
                val result = transactionProcessor.processIncomingPayload(payload)

                // Update UI based on outcome
                result.fold(
                    onSuccess = { txHashHex ->
                        _uiState.value = PaymentUiState.Success(txHashHex)
                    },
                    onFailure = { error ->
                        _uiState.value = PaymentUiState.Failure(error.message ?: "Compromised transaction rejected")
                    }
                )
            }
        }
    }

    /**
     * Called by the "Send" UI. Scans the room for broadcasting receivers.
     */
    fun startScanningForReceivers() {
        // 💡 FIX 2: Kill lingering receiver sockets so we start a fresh scan
        p2pManager.stopAll()

        // 💡 FIX 3: Clear the UI state so buttons become clickable again
        reset()

        _discoveredReceivers.value = emptyMap() // Clear old scans
        p2pManager.startDiscovering { endpointId, endpointName ->
            Log.d("PaySetu_P2P", "Found Receiver: $endpointName with ID: $endpointId")
            _discoveredReceivers.update { currentMap ->
                currentMap + (endpointId to endpointName)
            }
        }
    }

    /**
     * Kills the Bluetooth radios manually.
     */
    fun stopOfflineMode() {
        p2pManager.stopAll()
        // 💡 Return to Idle when canceling out of a scan/receive screen
        reset()
    }

    /**
     * Automatically kills the Bluetooth radios if the user closes the app or screen.
     */
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

                    // FIRE IT OVER THE AIR!
                    val payloadData = "TX_PAYLOAD:{hash:${bytesToHex(finalTx.txHash)},amount:$amount}"
                    p2pManager.sendTransaction(receiverEndpointId, payloadData)

                    bytesToHex(finalTx.txHash)
                }

                _uiState.value = PaymentUiState.Success(resultHash)

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