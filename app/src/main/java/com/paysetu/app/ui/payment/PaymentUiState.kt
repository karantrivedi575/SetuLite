package com.paysetu.app.ui.payment

sealed class PaymentUiState {
    object Idle : PaymentUiState()
    object Processing : PaymentUiState()
    data class Success(val txHash: String) : PaymentUiState()
    data class Failure(val reason: String) : PaymentUiState()
}
