package com.paysetu.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.data.ledger.entity.TransactionStatus
import com.paysetu.app.domain.security.TransactionSigner // Import the interface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.security.MessageDigest

class PaymentViewModel(
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: TransactionSigner // Inject the signer
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState

    fun sendOfflinePayment(
        amount: Long,
        prevTxHash: ByteArray?
    ) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.Processing

            try {
                val timestamp = System.currentTimeMillis()
                val validatedPrevHash = prevTxHash ?: ByteArray(32) { 0 }

                // 1. Create a template for hashing
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

                // 2. Calculate the deterministic SHA-256 hash
                val finalTxHash = calculateTransactionHash(txTemplate)

                // 3. 🔐 NEW: Generate Digital Signature using Hardware Keystore
                val digitalSignature = transactionSigner.sign(finalTxHash)

                // 4. Create the final entity with BOTH Hash and Signature
                val finalTx = txTemplate.copy(
                    txHash = finalTxHash,
                    signature = digitalSignature
                )

                // 5. Append to ledger via repository
                ledgerRepository.appendTransactionAtomically(finalTx)

                _uiState.value = PaymentUiState.Success(
                    txHash = bytesToHex(finalTx.txHash)
                )

            } catch (e: Exception) {
                _uiState.value = PaymentUiState.Failure(
                    reason = e.message ?: "Transaction failed"
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

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun reset() {
        _uiState.value = PaymentUiState.Idle
    }
}