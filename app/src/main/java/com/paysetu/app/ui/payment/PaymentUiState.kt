package com.paysetu.app.ui.payment

sealed class PaymentUiState {
    object Idle : PaymentUiState()
    object Processing : PaymentUiState()

    // 💡 Update: Success now accepts the transaction hash AND the amount
    data class Success(
        val txHash: String,
        val amount: Long? = null
    ) : PaymentUiState()

    data class Failure(val reason: String) : PaymentUiState()
}