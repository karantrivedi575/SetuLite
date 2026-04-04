package com.paysetu.app.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SendPaymentViewModel(
    private val sendPaymentUseCase: SendPaymentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState

    /**
     * Executes the payment.
     * @param amount The value to send.
     * @param pin The PIN captured from the UI input field.
     */
    fun sendPayment(amount: Long, pin: String) {
        viewModelScope.launch {
            _uiState.value = PaymentUiState.Processing
            try {
                // ✅ Now passing the 'pin' parameter required by SendPaymentUseCase
                val result = sendPaymentUseCase.execute(amount, pin)

                // 🛡️ Update line 30 to this:
                _uiState.value = PaymentUiState.Success(
                    txHash = bytesToHex(result.txHash),
                    amount = amount // 👈 Hand over the amount here
                )
            } catch (e: Exception) {
                _uiState.value = PaymentUiState.Failure(
                    reason = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun resetState() {
        _uiState.value = PaymentUiState.Idle
    }
}