package com.paysetu.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.data.ledger.entity.TransactionStatus
import com.paysetu.app.domain.security.TransactionSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest



class PaymentViewModel(
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: TransactionSigner
) : ViewModel() {

    // 1. The State of the Current Transaction Action
    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState

    // 2. 💡 NEW: Streaming the Ledger History
    // This uses stateIn to convert a cold Flow from Room into a hot StateFlow.
    // It automatically stops fetching when the UI is not visible (SharingStarted.WhileSubscribed).
    val ledgerHistory: StateFlow<List<LedgerTransactionEntity>> = ledgerRepository
        .getAllTransactions() // Make sure this returns a Flow<List<LedgerTransactionEntity>> in your Dao
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Cache for 5s after UI hides
            initialValue = emptyList() // The UI renders immediately with an empty list instead of waiting
        )

    /**
     * Sends an offline payment.
     * Explicitly uses background dispatchers to prevent UI jank during crypto/DB ops.
     */
    fun sendOfflinePayment(amount: Long) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.Processing

            try {
                // Run heavy logic on background threads
                val resultHash = withContext(Dispatchers.Default) {

                    // Fetch last transaction (IO bound)
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
                        receiverDeviceId = "REMOTE_DEVICE".toByteArray(),
                        amount = amount,
                        timestamp = timestamp,
                        signature = ByteArray(0),
                        direction = TransactionDirection.OUTGOING,
                        status = TransactionStatus.ACCEPTED
                    )

                    // SHA-256 Hashing (CPU bound)
                    val finalTxHash = calculateTransactionHash(txTemplate)

                    // Hardware Signing (Slowest part)
                    val digitalSignature = transactionSigner.sign(finalTxHash)

                    val finalTx = txTemplate.copy(
                        txHash = finalTxHash,
                        signature = digitalSignature
                    )

                    // Atomic Append (IO bound)
                    withContext(Dispatchers.IO) {
                        ledgerRepository.appendTransactionAtomically(finalTx)
                    }

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

    /**
     * Internal hashing logic.
     * Keep private and run on Dispatchers.Default.
     */
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