package com.paysetu.app.payment

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