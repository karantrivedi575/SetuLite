// File: PaymentModels.kt
package com.paysetu.app.payment

// ==========================================
// 1. UI State (Drives the Compose Screens)
// ==========================================
sealed class PaymentUiState {
    // 💤 Default state when nothing is happening
    object Idle : PaymentUiState()

    // 🔄 Used for P2P discovery and local handshake
    object Processing : PaymentUiState()

    // 📨 Phase 17: Specifically for the Binary SMS dispatching phase
    data class SmsSending(val phoneNumber: String) : PaymentUiState()

    // 🎉 Success now strictly holds the hash and the final amount for the receipt
    data class Success(
        val txHash: String,
        val amount: Long
    ) : PaymentUiState()

    // ⚠️ Failure state with a descriptive reason for the user
    data class Failure(val reason: String) : PaymentUiState()
}

// ==========================================
// 2. Backend Transaction State Tracking
// ==========================================
sealed class TransactionState {
    object Initiated : TransactionState()
    object Signed : TransactionState()
    object Broadcasting : TransactionState()
    object Acknowledged : TransactionState()
    data class Failed(val error: String) : TransactionState()
}

// ==========================================
// 3. Cryptographic Data Payload
// ==========================================
data class SignedTransaction(
    val txHash: ByteArray,
    val prevTxHash: ByteArray,
    val payload: ByteArray,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedTransaction

        if (!txHash.contentEquals(other.txHash)) return false
        if (!prevTxHash.contentEquals(other.prevTxHash)) return false
        if (!payload.contentEquals(other.payload)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = txHash.contentHashCode()
        result = 31 * result + prevTxHash.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}